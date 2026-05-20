package com.treeeducation.ioas.task;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.assetfile.AssetFileRepository;
import com.treeeducation.ioas.media.assetfile.AssetFileType;
import com.treeeducation.ioas.media.assetfile.UploadStatus;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackageStatus;
import com.treeeducation.ioas.task.dto.UploadTaskCompleteRequest;
import com.treeeducation.ioas.task.dto.UploadTaskCreateRequest;
import com.treeeducation.ioas.task.dto.UploadTaskPresignRequest;
import com.treeeducation.ioas.task.dto.UploadTaskPresignResponse;
import com.treeeducation.ioas.task.dto.UploadTaskProgressRequest;
import com.treeeducation.ioas.task.dto.UploadTaskResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/upload-tasks")
@Tag(name = "上传任务")
public class UploadTaskController {
    private static final List<String> RUNNING_STATUSES = List.of("created", "queued", "uploading", "processing");
    private static final int PRESIGN_EXPIRE_SECONDS = 60 * 30;

    private final TaskRepository taskRepository;
    private final ContentPackageRepository contentPackageRepository;
    private final AssetFileRepository assetFileRepository;
    private final TaskLogService taskLogService;
    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;

    @Value("${ioas.storage.bucket}")
    private String defaultBucketName;

    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    @Value("${ioas.task.max-user-running:3}")
    private long maxUserRunning;

    @Value("${ioas.task.max-global-running:20}")
    private long maxGlobalRunning;

    public UploadTaskController(TaskRepository taskRepository,
                                ContentPackageRepository contentPackageRepository,
                                AssetFileRepository assetFileRepository,
                                TaskLogService taskLogService,
                                @Qualifier("minioClient") MinioClient minioClient,
                                @Qualifier("publicMinioClient") MinioClient publicMinioClient) {
        this.taskRepository = taskRepository;
        this.contentPackageRepository = contentPackageRepository;
        this.assetFileRepository = assetFileRepository;
        this.taskLogService = taskLogService;
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
    }

    @PostMapping
    @Operation(summary = "创建上传任务")
    public ApiResponse<UploadTaskResponse> create(@RequestBody UploadTaskCreateRequest request,
                                                   @AuthenticationPrincipal UserPrincipal p) {
        Long userId = p == null ? 0L : p.id();
        enforceConcurrencyLimit(userId, p);
        Long packageId = resolvePackageId(request == null ? null : request.packageId());
        ContentPackage pkg = contentPackageRepository.findById(packageId).orElse(null);
        String fileName = request == null ? "unknown" : valueOrDefault(request.fileName(), "unknown");
        String topicName = pkg == null ? "未绑定主题" : pkg.getTopicName();

        Task task = new Task();
        task.setType("UPLOAD");
        task.setTitle(topicName + " - " + fileName);
        task.setTaskType(TaskType.media_upload);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(packageId);
        task.setAssigneeId(userId);
        task.setAssigneeName(p == null ? "system" : p.userName());
        task.setStatus("created");
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());

        Task saved = taskRepository.save(task);
        taskLogService.info(saved.getId(), "task created for direct upload, fileName=" + fileName);
        return ApiResponse.ok(toResponse(saved));
    }

    @PostMapping("/{taskId}/presign")
    @Operation(summary = "生成MinIO直传URL")
    public ApiResponse<UploadTaskPresignResponse> presign(@PathVariable Long taskId,
                                                          @RequestBody UploadTaskPresignRequest request,
                                                          @AuthenticationPrincipal UserPrincipal p) throws Exception {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> BusinessException.notFound("上传任务不存在"));
        assertOwner(task, p);
        if ("cancelled".equalsIgnoreCase(task.getStatus())) {
            throw BusinessException.badRequest("任务已取消，无法上传");
        }

        String originalFileName = valueOrDefault(request == null ? null : request.fileName(), "upload-file");
        String suffix = suffixOf(originalFileName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = "media/" + datePath + "/" + taskId + "-" + UUID.randomUUID() + suffix;
        String uploadUrl = publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(defaultBucketName)
                        .object(objectKey)
                        .expiry(PRESIGN_EXPIRE_SECONDS, TimeUnit.SECONDS)
                        .build()
        );
        String publicUrl = publicBaseUrl + "/" + objectKey;

        task.setStatus("uploading");
        task.setProgress(Math.max(1, value(task.getProgress())));
        task.setUploadBucketName(defaultBucketName);
        task.setUploadObjectKey(objectKey);
        task.setUploadPublicUrl(publicUrl);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        taskLogService.info(taskId, "presigned direct upload url generated, objectKey=" + objectKey + ", fileName=" + originalFileName + ", size=" + (request == null ? null : request.fileSize()));

        return ApiResponse.ok(new UploadTaskPresignResponse(taskId, defaultBucketName, objectKey, uploadUrl, publicUrl, PRESIGN_EXPIRE_SECONDS));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询上传任务状态")
    public ApiResponse<UploadTaskResponse> get(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("upload task not found: " + taskId));
        return ApiResponse.ok(toResponse(task));
    }

    @PatchMapping("/{taskId}/progress")
    @Operation(summary = "上报上传任务进度")
    public ApiResponse<UploadTaskResponse> progress(@PathVariable Long taskId,
                                                    @RequestBody UploadTaskProgressRequest request,
                                                    @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("upload task not found: " + taskId));
        assertOwner(task, p);
        int progress = request == null || request.progress() == null ? value(task.getProgress()) : Math.max(0, Math.min(99, request.progress()));
        task.setStatus(valueOrDefault(request == null ? null : request.status(), "uploading"));
        task.setProgress(progress);
        task.setUpdatedAt(Instant.now());
        Task saved = taskRepository.save(task);
        return ApiResponse.ok(toResponse(saved));
    }

    @PostMapping("/{taskId}/complete")
    @Operation(summary = "完成上传任务并落库")
    public ApiResponse<UploadTaskResponse> complete(@PathVariable Long taskId,
                                                    @RequestBody UploadTaskCompleteRequest request,
                                                    @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("upload task not found: " + taskId));

        try {
            assertOwner(task, p);
            if (request == null || request.objectKey() == null || request.objectKey().isBlank()) {
                throw BusinessException.badRequest("上传对象Key不能为空");
            }
            task.setStatus("processing");
            task.setProgress(95);
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            taskLogService.info(taskId, "complete requested, start stat object, objectKey=" + request.objectKey());

            Long packageId = resolvePackageId(task.getRelatedPackageId());
            String bucketName = valueOrDefault(request.bucketName(), defaultBucketName);
            var stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(request.objectKey())
                    .build());

            Long actualSize = stat.size();
            String actualMime = valueOrDefault(request.mimeType(), stat.contentType());
            String publicUrl = valueOrDefault(request.publicUrl(), publicBaseUrl + "/" + request.objectKey());
            String fileName = valueOrDefault(request.fileName(), request.objectKey());
            AssetFileType actualFileType = request.fileType() == null ? AssetFileType.script : request.fileType();

            AssetFile assetFile = new AssetFile();
            assetFile.setFileNo("FILE" + System.currentTimeMillis());
            assetFile.setPackageId(packageId);
            assetFile.setFileName(fileName);
            assetFile.setOriginalName(fileName);
            assetFile.setType(actualFileType.name());
            assetFile.setFileType(actualFileType);
            assetFile.setMimeType(actualMime);
            assetFile.setFileSize(actualSize);
            assetFile.setBucketName(bucketName);
            assetFile.setObjectKey(request.objectKey());
            assetFile.setPreviewUrl(publicUrl);
            assetFile.setThumbnailUrl(publicUrl);
            assetFile.setUploadStatus(UploadStatus.success);
            assetFile.setUploadedBy(p == null ? 0L : p.id());
            assetFile.setCreatedBy(p == null ? 0L : p.id());
            assetFile.setCreatedByName(p == null ? "system" : p.userName());
            assetFileRepository.save(assetFile);

            task.setRelatedPackageId(packageId);
            task.setUploadBucketName(bucketName);
            task.setUploadObjectKey(request.objectKey());
            task.setUploadPublicUrl(publicUrl);
            task.setStatus("success");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            task.setErrorMessage(null);
            Task saved = taskRepository.save(task);
            taskLogService.info(taskId, "task success after direct upload, assetFileId=" + assetFile.getId());
            return ApiResponse.ok(toResponse(saved));
        } catch (RuntimeException ex) {
            markFailed(task, ex.getMessage());
            taskLogService.error(taskId, "complete failed", ex);
            throw ex;
        } catch (Exception ex) {
            markFailed(task, ex.getMessage());
            taskLogService.error(taskId, "complete failed", ex);
            throw new RuntimeException(ex);
        }
    }

    @PostMapping("/{taskId}/fail")
    @Operation(summary = "标记上传任务失败")
    public ApiResponse<UploadTaskResponse> fail(@PathVariable Long taskId,
                                                @RequestParam(required = false) String message,
                                                @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("upload task not found: " + taskId));
        assertOwner(task, p);
        task.setStatus("failed");
        task.setProgress(100);
        task.setErrorMessage(message);
        task.setUpdatedAt(Instant.now());
        Task saved = taskRepository.save(task);
        taskLogService.warn(taskId, "task marked failed, message=" + message);
        return ApiResponse.ok(toResponse(saved));
    }

    private void markFailed(Task task, String message) {
        task.setStatus("failed");
        task.setProgress(100);
        task.setErrorMessage(message);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
    }

    private void enforceConcurrencyLimit(Long userId, UserPrincipal p) {
        if (p != null && "SUPER_ADMIN".equalsIgnoreCase(p.role())) {
            return;
        }
        long globalRunning = taskRepository.countByRoleTypeAndStatusIn(TaskRoleType.media, RUNNING_STATUSES);
        if (globalRunning >= maxGlobalRunning) {
            throw BusinessException.badRequest("系统上传任务繁忙，请等待已有任务完成后再上传");
        }
        long userRunning = taskRepository.countByRoleTypeAndAssigneeIdAndStatusIn(TaskRoleType.media, userId, RUNNING_STATUSES);
        if (userRunning >= maxUserRunning) {
            throw BusinessException.badRequest("当前账号进行中的上传任务过多，请等待已有任务完成后再上传");
        }
    }

    private void assertOwner(Task task, UserPrincipal p) {
        if (p == null || task.getAssigneeId() == null || p.id().equals(task.getAssigneeId())) {
            return;
        }
        if ("SUPER_ADMIN".equalsIgnoreCase(p.role())) {
            return;
        }
        throw BusinessException.forbidden("只能操作自己的上传任务");
    }

    private Long resolvePackageId(Long packageId) {
        if (packageId != null && packageId > 0 && contentPackageRepository.existsById(packageId)) {
            return packageId;
        }

        ContentPackage contentPackage = new ContentPackage();
        contentPackage.setPackageNo("PKG" + System.currentTimeMillis());
        contentPackage.setTopicName("默认上传资源包");
        contentPackage.setOperatorId(0L);
        contentPackage.setOperatorName("system");
        contentPackage.setFullPath("/default-upload");
        contentPackage.setUploadStatus(ContentPackageStatus.completed);
        contentPackage.setCreatedBy(0L);
        contentPackage.setCreatedByName("system");

        ContentPackage saved = contentPackageRepository.save(contentPackage);
        return saved.getId();
    }

    private UploadTaskResponse toResponse(Task task) {
        return new UploadTaskResponse(
                task.getId(),
                task.getStatus(),
                task.getProgress(),
                task.getRelatedPackageId()
        );
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int value(Integer v) {
        return v == null ? 0 : v;
    }

    private String suffixOf(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx) : "";
    }
}

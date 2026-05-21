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
import com.treeeducation.ioas.task.dto.MultipartCompleteRequest;
import com.treeeducation.ioas.task.dto.MultipartCreateRequest;
import com.treeeducation.ioas.task.dto.MultipartCreateResponse;
import com.treeeducation.ioas.task.dto.MultipartSignPartRequest;
import com.treeeducation.ioas.task.dto.MultipartSignPartResponse;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/upload-tasks")
@Tag(name = "上传任务")
public class UploadTaskController {
    private static final List<String> RUNNING_STATUSES = List.of("created", "queued", "uploading", "processing");
    private static final int PRESIGN_EXPIRE_SECONDS = 60 * 30;
    private static final long DEFAULT_MULTIPART_PART_SIZE = 10L * 1024L * 1024L;
    private static final long MIN_MULTIPART_PART_SIZE = 5L * 1024L * 1024L;

    private final TaskRepository taskRepository;
    private final ContentPackageRepository contentPackageRepository;
    private final AssetFileRepository assetFileRepository;
    private final TaskLogService taskLogService;
    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final S3Client internalS3Client;
    private final S3Presigner publicS3Presigner;

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
                                @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                                @Qualifier("internalS3Client") S3Client internalS3Client,
                                @Qualifier("publicS3Presigner") S3Presigner publicS3Presigner) {
        this.taskRepository = taskRepository;
        this.contentPackageRepository = contentPackageRepository;
        this.assetFileRepository = assetFileRepository;
        this.taskLogService = taskLogService;
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.internalS3Client = internalS3Client;
        this.publicS3Presigner = publicS3Presigner;
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
        task.setFileName(fileName);
        task.setFileSize(request == null ? null : request.fileSize());
        task.setUploadedBytes(0L);
        task.setUpdatedAt(Instant.now());

        Task saved = taskRepository.save(task);
        taskLogService.info(saved.getId(), "task created for multipart/direct upload, fileName=" + fileName);
        return ApiResponse.ok(toResponse(saved));
    }

    @PostMapping("/{taskId}/multipart")
    @Operation(summary = "创建 S3 Multipart Upload")
    public ApiResponse<MultipartCreateResponse> createMultipart(@PathVariable Long taskId,
                                                                @RequestBody MultipartCreateRequest request,
                                                                @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> BusinessException.notFound("上传任务不存在"));
        assertOwner(task, p);
        if ("cancelled".equalsIgnoreCase(task.getStatus())) {
            throw BusinessException.badRequest("任务已取消，无法上传");
        }

        String fileName = valueOrDefault(request == null ? null : request.fileName(), "upload-file");
        String suffix = suffixOf(fileName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = "media/" + datePath + "/" + taskId + "-" + UUID.randomUUID() + suffix;
        String mimeType = valueOrDefault(request == null ? null : request.mimeType(), "application/octet-stream");
        long fileSize = request == null || request.fileSize() == null ? 0L : Math.max(request.fileSize(), 0L);
        long partSize = normalizePartSize(request == null ? null : request.partSize());
        int partCount = fileSize <= 0 ? 1 : (int) Math.ceil((double) fileSize / (double) partSize);

        var createResp = internalS3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(defaultBucketName)
                .key(objectKey)
                .contentType(mimeType)
                .build());
        String uploadId = createResp.uploadId();
        String publicUrl = publicBaseUrl + "/" + objectKey;

        task.setStatus("uploading");
        task.setProgress(1);
        task.setUploadBucketName(defaultBucketName);
        task.setUploadObjectKey(objectKey);
        task.setUploadId(uploadId);
        task.setUploadPublicUrl(publicUrl);
        task.setFileName(fileName);
        task.setFileSize(fileSize);
        task.setUploadedBytes(0L);
        task.setSpeedBytesPerSecond(0L);
        task.setAverageSpeedBytesPerSecond(0L);
        task.setPartCount(partCount);
        task.setCompletedPartCount(0);
        task.setLastProgressAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        taskLogService.info(taskId, "multipart upload created, uploadId=" + uploadId + ", objectKey=" + objectKey + ", partSize=" + partSize + ", partCount=" + partCount + ", fileSize=" + fileSize);

        return ApiResponse.ok(new MultipartCreateResponse(taskId, defaultBucketName, objectKey, uploadId, publicUrl, partSize, partCount));
    }

    @PostMapping("/{taskId}/multipart/sign-part")
    @Operation(summary = "签名单个 S3 Multipart 分片")
    public ApiResponse<MultipartSignPartResponse> signPart(@PathVariable Long taskId,
                                                           @RequestBody MultipartSignPartRequest request,
                                                           @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> BusinessException.notFound("上传任务不存在"));
        assertOwner(task, p);
        String uploadId = valueOrDefault(request == null ? null : request.uploadId(), task.getUploadId());
        String bucketName = valueOrDefault(request == null ? null : request.bucketName(), task.getUploadBucketName());
        String objectKey = valueOrDefault(request == null ? null : request.objectKey(), task.getUploadObjectKey());
        Integer partNumber = request == null ? null : request.partNumber();
        if (uploadId == null || uploadId.isBlank() || bucketName == null || bucketName.isBlank() || objectKey == null || objectKey.isBlank() || partNumber == null || partNumber < 1) {
            throw BusinessException.badRequest("分片签名参数不完整");
        }

        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        PresignedUploadPartRequest presigned = publicS3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGN_EXPIRE_SECONDS))
                .uploadPartRequest(uploadPartRequest)
                .build());
        return ApiResponse.ok(new MultipartSignPartResponse(presigned.url().toString(), partNumber));
    }

    @PostMapping("/{taskId}/multipart/complete")
    @Operation(summary = "完成 S3 Multipart Upload 并落库")
    public ApiResponse<UploadTaskResponse> completeMultipart(@PathVariable Long taskId,
                                                             @RequestBody MultipartCompleteRequest request,
                                                             @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> BusinessException.notFound("上传任务不存在"));
        try {
            assertOwner(task, p);
            if (request == null || request.parts() == null || request.parts().isEmpty()) {
                throw BusinessException.badRequest("缺少已上传分片信息");
            }
            String bucketName = valueOrDefault(request.bucketName(), task.getUploadBucketName());
            String objectKey = valueOrDefault(request.objectKey(), task.getUploadObjectKey());
            String uploadId = valueOrDefault(request.uploadId(), task.getUploadId());
            if (bucketName == null || bucketName.isBlank() || objectKey == null || objectKey.isBlank() || uploadId == null || uploadId.isBlank()) {
                throw BusinessException.badRequest("Multipart 完成参数不完整");
            }

            task.setStatus("processing");
            task.setProgress(95);
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            taskLogService.info(taskId, "multipart complete requested, uploadId=" + uploadId + ", objectKey=" + objectKey + ", parts=" + request.parts().size());

            List<CompletedPart> completedParts = request.parts().stream()
                    .sorted(Comparator.comparing(MultipartCompleteRequest.Part::partNumber))
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.partNumber())
                            .eTag(normalizeEtag(part.etag()))
                            .build())
                    .toList();
            internalS3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build());

            UploadTaskCompleteRequest completeRequest = new UploadTaskCompleteRequest(
                    bucketName,
                    objectKey,
                    valueOrDefault(request.publicUrl(), publicBaseUrl + "/" + objectKey),
                    request.fileName(),
                    request.fileSize(),
                    request.mimeType(),
                    request.fileType()
            );
            return complete(taskId, completeRequest, p);
        } catch (RuntimeException ex) {
            markFailed(task, ex.getMessage());
            taskLogService.error(taskId, "multipart complete failed", ex);
            throw ex;
        } catch (Exception ex) {
            markFailed(task, ex.getMessage());
            taskLogService.error(taskId, "multipart complete failed", ex);
            throw new RuntimeException(ex);
        }
    }

    @PostMapping("/{taskId}/multipart/abort")
    @Operation(summary = "中止 S3 Multipart Upload")
    public ApiResponse<UploadTaskResponse> abortMultipart(@PathVariable Long taskId,
                                                          @AuthenticationPrincipal UserPrincipal p) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> BusinessException.notFound("上传任务不存在"));
        assertOwner(task, p);
        if (task.getUploadId() != null && task.getUploadBucketName() != null && task.getUploadObjectKey() != null) {
            internalS3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(task.getUploadBucketName())
                    .key(task.getUploadObjectKey())
                    .uploadId(task.getUploadId())
                    .build());
            taskLogService.warn(taskId, "multipart upload aborted, uploadId=" + task.getUploadId());
        }
        task.setStatus("cancelled");
        task.setProgress(100);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        task.setErrorMessage("用户取消上传");
        Task saved = taskRepository.save(task);
        return ApiResponse.ok(toResponse(saved));
    }

    @PostMapping("/{taskId}/presign")
    @Operation(summary = "生成MinIO直传URL：旧单PUT兼容接口")
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
        task.setFileName(originalFileName);
        task.setFileSize(request == null ? null : request.fileSize());
        task.setUploadedBytes(0L);
        task.setLastProgressAt(Instant.now());
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
        int oldProgress = value(task.getProgress());
        int progress = request == null || request.progress() == null ? oldProgress : Math.max(0, Math.min(99, request.progress()));
        String status = valueOrDefault(request == null ? null : request.status(), "uploading");
        task.setStatus(status);
        task.setProgress(progress);
        if (request != null) {
            if (request.uploadedBytes() != null) task.setUploadedBytes(request.uploadedBytes());
            if (request.totalBytes() != null) task.setFileSize(request.totalBytes());
            if (request.speedBytesPerSecond() != null) task.setSpeedBytesPerSecond(request.speedBytesPerSecond());
            if (request.averageSpeedBytesPerSecond() != null) task.setAverageSpeedBytesPerSecond(request.averageSpeedBytesPerSecond());
            if (request.partCount() != null) task.setPartCount(request.partCount());
            if (request.completedPartCount() != null) task.setCompletedPartCount(request.completedPartCount());
        }
        task.setLastProgressAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        Task saved = taskRepository.save(task);
        if (progress != oldProgress && shouldLogProgress(oldProgress, progress)) {
            taskLogService.info(taskId, "upload progress heartbeat, status=" + status + ", progress=" + progress + "%" +
                    ", uploadedBytes=" + task.getUploadedBytes() + ", fileSize=" + task.getFileSize() +
                    ", speed=" + task.getSpeedBytesPerSecond() + "B/s" +
                    ", parts=" + task.getCompletedPartCount() + "/" + task.getPartCount());
        }
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
            task.setFileName(fileName);
            task.setFileSize(actualSize);
            task.setUploadedBytes(actualSize);
            task.setStatus("success");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            task.setLastProgressAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            task.setErrorMessage(null);
            Task saved = taskRepository.save(task);
            taskLogService.info(taskId, "task success after direct/multipart upload, assetFileId=" + assetFile.getId());
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
        task.setCompletedAt(Instant.now());
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

    private long normalizePartSize(Long requestedPartSize) {
        long size = requestedPartSize == null ? DEFAULT_MULTIPART_PART_SIZE : requestedPartSize;
        return Math.max(size, MIN_MULTIPART_PART_SIZE);
    }

    private String normalizeEtag(String etag) {
        if (etag == null) return "";
        String trimmed = etag.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) return trimmed;
        return trimmed;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int value(Integer v) {
        return v == null ? 0 : v;
    }

    private boolean shouldLogProgress(int oldProgress, int progress) {
        return progress == 1 || progress == 95 || progress % 5 == 0 || progress - oldProgress >= 5;
    }

    private String suffixOf(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx) : "";
    }
}

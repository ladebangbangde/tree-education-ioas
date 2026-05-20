package com.treeeducation.ioas.task;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.assetfile.AssetFileRepository;
import com.treeeducation.ioas.media.assetfile.UploadStatus;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackageStatus;
import com.treeeducation.ioas.task.dto.UploadTaskCompleteRequest;
import com.treeeducation.ioas.task.dto.UploadTaskCreateRequest;
import com.treeeducation.ioas.task.dto.UploadTaskProgressRequest;
import com.treeeducation.ioas.task.dto.UploadTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/upload-tasks")
@Tag(name = "上传任务")
public class UploadTaskController {

    private final TaskRepository taskRepository;
    private final ContentPackageRepository contentPackageRepository;
    private final AssetFileRepository assetFileRepository;

    @Value("${ioas.storage.bucket}")
    private String defaultBucketName;

    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    public UploadTaskController(TaskRepository taskRepository,
                                ContentPackageRepository contentPackageRepository,
                                AssetFileRepository assetFileRepository) {
        this.taskRepository = taskRepository;
        this.contentPackageRepository = contentPackageRepository;
        this.assetFileRepository = assetFileRepository;
    }

    @PostMapping
    @Operation(summary = "创建上传任务")
    public ApiResponse<UploadTaskResponse> create(@RequestBody UploadTaskCreateRequest request,
                                                   @AuthenticationPrincipal UserPrincipal p) {
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
        task.setAssigneeId(p == null ? 0L : p.id());
        task.setAssigneeName(p == null ? "system" : p.userName());
        task.setStatus("created");
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());

        Task saved = taskRepository.save(task);
        return ApiResponse.ok(toResponse(saved));
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
        int progress = request == null || request.progress() == null ? task.getProgress() : Math.max(0, Math.min(99, request.progress()));
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
            task.setStatus("processing");
            task.setProgress(95);
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);

            Long packageId = resolvePackageId(task.getRelatedPackageId());
            String bucketName = valueOrDefault(request.bucketName(), defaultBucketName);
            String objectKey = request.objectKey();
            String publicUrl = valueOrDefault(request.publicUrl(), publicBaseUrl + "/" + objectKey);
            String fileName = valueOrDefault(request.fileName(), objectKey);

            AssetFile assetFile = new AssetFile();
            assetFile.setFileNo("FILE" + System.currentTimeMillis());
            assetFile.setPackageId(packageId);
            assetFile.setFileName(fileName);
            assetFile.setOriginalName(fileName);
            assetFile.setType(request.fileType().name());
            assetFile.setFileType(request.fileType());
            assetFile.setMimeType(request.mimeType());
            assetFile.setFileSize(request.fileSize());
            assetFile.setBucketName(bucketName);
            assetFile.setObjectKey(objectKey);
            assetFile.setPreviewUrl(publicUrl);
            assetFile.setThumbnailUrl(publicUrl);
            assetFile.setUploadStatus(UploadStatus.success);
            assetFile.setUploadedBy(p == null ? 0L : p.id());
            assetFile.setCreatedBy(p == null ? 0L : p.id());
            assetFile.setCreatedByName(p == null ? "system" : p.userName());
            assetFileRepository.save(assetFile);

            task.setRelatedPackageId(packageId);
            task.setStatus("success");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            task.setErrorMessage(null);
            Task saved = taskRepository.save(task);
            return ApiResponse.ok(toResponse(saved));
        } catch (RuntimeException ex) {
            task.setStatus("failed");
            task.setProgress(100);
            task.setErrorMessage(ex.getMessage());
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            throw ex;
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
        return ApiResponse.ok(toResponse(saved));
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
}

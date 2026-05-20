package com.treeeducation.ioas.task;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.assetfile.AssetFileRepository;
import com.treeeducation.ioas.media.assetfile.AssetFileType;
import com.treeeducation.ioas.media.assetfile.UploadStatus;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackageStatus;
import com.treeeducation.ioas.task.dto.UploadTaskResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload-tasks")
@Tag(name = "上传任务文件")
public class UploadTaskFileController {

    private final TaskRepository taskRepository;
    private final ContentPackageRepository contentPackageRepository;
    private final AssetFileRepository assetFileRepository;
    private final TaskLogService taskLogService;
    private final MinioClient minioClient;

    @Value("${ioas.storage.bucket}")
    private String bucketName;

    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    public UploadTaskFileController(TaskRepository taskRepository,
                                    ContentPackageRepository contentPackageRepository,
                                    AssetFileRepository assetFileRepository,
                                    TaskLogService taskLogService,
                                    MinioClient minioClient) {
        this.taskRepository = taskRepository;
        this.contentPackageRepository = contentPackageRepository;
        this.assetFileRepository = assetFileRepository;
        this.taskLogService = taskLogService;
        this.minioClient = minioClient;
    }

    @PostMapping(value = "/{taskId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件并绑定上传任务")
    public ApiResponse<UploadTaskResponse> uploadFile(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "image") AssetFileType fileType
    ) throws Exception {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("upload task not found: " + taskId));

        try {
            taskLogService.info(taskId, "received multipart upload request, fileType=" + fileType);
            updateTask(task, "uploading", Math.max(task.getProgress(), 20), null, false);

            Long packageId = resolvePackageId(task.getRelatedPackageId());
            String originalFilename = file.getOriginalFilename() == null ? "upload-file" : file.getOriginalFilename();
            String suffix = "";
            if (originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }
            taskLogService.info(taskId, "uploading file name=" + originalFilename + ", size=" + file.getSize() + ", contentType=" + file.getContentType());

            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectKey = datePath + "/" + UUID.randomUUID() + suffix;
            taskLogService.info(taskId, "start put object bucket=" + bucketName + ", objectKey=" + objectKey);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            updateTask(task, "processing", 95, null, false);
            taskLogService.info(taskId, "minio put object finished, start asset_file persist");

            String publicUrl = publicBaseUrl + "/" + objectKey;
            AssetFile assetFile = new AssetFile();
            assetFile.setFileNo("FILE" + System.currentTimeMillis());
            assetFile.setPackageId(packageId);
            assetFile.setFileName(originalFilename);
            assetFile.setOriginalName(originalFilename);
            assetFile.setType(fileType.name());
            assetFile.setFileType(fileType);
            assetFile.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            assetFile.setFileSize(file.getSize());
            assetFile.setBucketName(bucketName);
            assetFile.setObjectKey(objectKey);
            assetFile.setPreviewUrl(publicUrl);
            assetFile.setThumbnailUrl(publicUrl);
            assetFile.setUploadStatus(UploadStatus.success);
            assetFile.setUploadedBy(task.getAssigneeId() == null ? 0L : task.getAssigneeId());
            assetFile.setCreatedBy(task.getAssigneeId() == null ? 0L : task.getAssigneeId());
            assetFile.setCreatedByName(task.getAssigneeName() == null ? "system" : task.getAssigneeName());
            assetFileRepository.save(assetFile);

            task.setRelatedPackageId(packageId);
            Task saved = updateTask(task, "success", 100, null, true);
            taskLogService.info(taskId, "task success, assetFileId=" + assetFile.getId());
            return ApiResponse.ok(toResponse(saved));
        } catch (Exception ex) {
            taskLogService.error(taskId, "upload task failed", ex);
            updateTask(task, "failed", 100, ex.getMessage(), true);
            throw ex;
        }
    }

    private Task updateTask(Task task, String status, Integer progress, String errorMessage, boolean completed) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(Instant.now());
        if (completed) {
            task.setCompletedAt(Instant.now());
        }
        return taskRepository.save(task);
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
}

package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.assetfile.AssetFileRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class TaskCleanupJob {
    private final TaskRepository taskRepository;
    private final AssetFileRepository assetFileRepository;
    private final TaskLogService taskLogService;
    private final MinioClient minioClient;
    private final S3Client s3Client;

    @Value("${ioas.task.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${ioas.task.cleanup.interrupted-retention-hours:24}")
    private long interruptedRetentionHours;

    @Value("${ioas.task.cleanup.failed-retention-days:7}")
    private long failedRetentionDays;

    @Value("${ioas.task.cleanup.cancelled-retention-days:7}")
    private long cancelledRetentionDays;

    @Value("${ioas.task.cleanup.success-log-retention-days:30}")
    private long successLogRetentionDays;

    public TaskCleanupJob(TaskRepository taskRepository,
                          AssetFileRepository assetFileRepository,
                          TaskLogService taskLogService,
                          @Qualifier("minioClient") MinioClient minioClient,
                          @Qualifier("internalS3Client") S3Client s3Client) {
        this.taskRepository = taskRepository;
        this.assetFileRepository = assetFileRepository;
        this.taskLogService = taskLogService;
        this.minioClient = minioClient;
        this.s3Client = s3Client;
    }

    @Scheduled(fixedDelayString = "${ioas.task.cleanup.fixed-delay-ms:300000}")
    @Transactional
    public void run() {
        if (!enabled) return;
        markStaleInterruptedAsFailed();
        purgeOldCancelledTasks();
        purgeOldFailedTasks();
        cleanupOldSuccessLogs();
    }

    private void markStaleInterruptedAsFailed() {
        Instant cutoff = Instant.now().minus(Math.max(1, interruptedRetentionHours), ChronoUnit.HOURS);
        List<Task> stale = taskRepository.findByStatusInAndUpdatedAtBefore(List.of("interrupted"), cutoff);
        for (Task task : stale) {
            abortMultipartQuietly(task);
            task.setStatus("failed");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            task.setErrorMessage("上传中断超过 " + interruptedRetentionHours + " 小时，系统自动标记失败并清理未完成分片");
            taskLogService.warn(task.getId(), "cleanup marked stale interrupted task as failed and aborted multipart upload");
        }
    }

    private void purgeOldCancelledTasks() {
        Instant cutoff = Instant.now().minus(Math.max(1, cancelledRetentionDays), ChronoUnit.DAYS);
        purgeTasks(taskRepository.findByStatusInAndCompletedAtBefore(List.of("cancelled"), cutoff), true, "cancelled retention expired");
    }

    private void purgeOldFailedTasks() {
        Instant cutoff = Instant.now().minus(Math.max(1, failedRetentionDays), ChronoUnit.DAYS);
        purgeTasks(taskRepository.findByStatusInAndCompletedAtBefore(List.of("failed"), cutoff), true, "failed retention expired");
    }

    private void cleanupOldSuccessLogs() {
        Instant cutoff = Instant.now().minus(Math.max(1, successLogRetentionDays), ChronoUnit.DAYS);
        List<Task> rows = taskRepository.findByStatusInAndCompletedAtBefore(List.of("success", "completed"), cutoff);
        for (Task task : rows) {
            taskLogService.delete(task.getId());
        }
    }

    private void purgeTasks(List<Task> tasks, boolean purgeFiles, String reason) {
        for (Task task : tasks) {
            try {
                abortMultipartQuietly(task);
                if (purgeFiles) {
                    purgeObjectAndAssetRows(task);
                }
                taskLogService.warn(task.getId(), "cleanup deleting task, reason=" + reason);
                taskLogService.delete(task.getId());
                taskRepository.delete(task);
            } catch (RuntimeException ex) {
                taskLogService.warn(task.getId(), "cleanup ignored task delete failure, reason=" + reason + ", message=" + ex.getMessage());
            }
        }
    }

    private void abortMultipartQuietly(Task task) {
        if (task.getUploadId() == null || task.getUploadBucketName() == null || task.getUploadObjectKey() == null) {
            return;
        }
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(task.getUploadBucketName())
                    .key(task.getUploadObjectKey())
                    .uploadId(task.getUploadId())
                    .build());
            taskLogService.warn(task.getId(), "cleanup aborted multipart upload, uploadId=" + task.getUploadId());
        } catch (Exception ex) {
            taskLogService.warn(task.getId(), "cleanup abort multipart ignored, uploadId=" + task.getUploadId() + ", message=" + ex.getMessage());
        }
    }

    private void purgeObjectAndAssetRows(Task task) {
        String bucket = task.getUploadBucketName();
        String objectKey = task.getUploadObjectKey();
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            taskLogService.warn(task.getId(), "cleanup removed object " + bucket + "/" + objectKey);
        } catch (Exception ex) {
            taskLogService.warn(task.getId(), "cleanup remove object ignored, bucket=" + bucket + ", objectKey=" + objectKey + ", message=" + ex.getMessage());
        }
        List<AssetFile> rows = assetFileRepository.findByBucketNameAndObjectKey(bucket, objectKey);
        if (!rows.isEmpty()) {
            assetFileRepository.deleteAll(rows);
            taskLogService.warn(task.getId(), "cleanup deleted asset rows, count=" + rows.size());
        }
    }
}

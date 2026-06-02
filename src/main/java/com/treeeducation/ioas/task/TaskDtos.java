package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Task DTOs. */
public final class TaskDtos {
    private TaskDtos() {}

    @Schema(description = "更新任务请求")
    public record UpdateRequest(String status, Integer progress, String errorMessage, Long assigneeId, String assigneeName) {}

    @Schema(description = "任务响应")
    public record Response(Long id,
                           String title,
                           TaskType taskType,
                           TaskRoleType roleType,
                           Long relatedPackageId,
                           String topicName,
                           Long operatorId,
                           String operatorName,
                           String fullPath,
                           Integer scriptCount,
                           Integer videoCount,
                           Integer imageCount,
                           Integer fileTotal,
                           Integer successCount,
                           Integer failedCount,
                           Long relatedLeadId,
                           Long assigneeId,
                           String assigneeName,
                           String status,
                           Integer progress,
                           String errorMessage,
                           String fileName,
                           String uploadBucketName,
                           String uploadObjectKey,
                           String uploadPublicUrl,
                           Long fileSize,
                           Long uploadedBytes,
                           Long speedBytesPerSecond,
                           Long averageSpeedBytesPerSecond,
                           Integer partCount,
                           Integer completedPartCount,
                           Instant lastProgressAt,
                           Instant createdAt,
                           Instant completedAt,
                           Instant updatedAt) {}

    public static Response of(Task t) {
        return of(t, null);
    }

    public static Response of(Task t, ContentPackage p) {
        int progress = isTerminalFailed(t) ? 100 : value(t.getProgress());
        int fileTotal = isUploadTask(t) ? 1 : 0;
        int successCount = isSuccess(t) ? 1 : 0;
        int failedCount = isTerminalFailed(t) ? 1 : 0;

        return new Response(t.getId(), t.getTitle(), t.getTaskType(), t.getRoleType(), t.getRelatedPackageId(),
                p == null ? null : p.getTopicName(),
                p == null ? null : p.getOperatorId(),
                p == null ? null : p.getOperatorName(),
                p == null ? null : p.getFullPath(),
                null,
                null,
                null,
                fileTotal,
                successCount,
                failedCount,
                t.getRelatedLeadId(),
                t.getAssigneeId(),
                t.getAssigneeName(),
                t.getStatus(),
                progress,
                t.getErrorMessage(),
                t.getFileName(),
                t.getUploadBucketName(),
                t.getUploadObjectKey(),
                t.getUploadPublicUrl(),
                t.getFileSize(),
                t.getUploadedBytes(),
                t.getSpeedBytesPerSecond(),
                t.getAverageSpeedBytesPerSecond(),
                t.getPartCount(),
                t.getCompletedPartCount(),
                t.getLastProgressAt(),
                t.getCreatedAt(),
                t.getCompletedAt(),
                t.getUpdatedAt());
    }

    private static boolean isUploadTask(Task t) {
        return t != null && (t.getTaskType() == TaskType.media_upload
                || t.getTaskType() == TaskType.data_cover_upload
                || t.getTaskType() == TaskType.data_screenshot_upload);
    }

    private static boolean isSuccess(Task t) {
        return t != null && "success".equalsIgnoreCase(t.getStatus());
    }

    private static boolean isTerminalFailed(Task t) {
        return t != null && ("failed".equalsIgnoreCase(t.getStatus()) || "cancelled".equalsIgnoreCase(t.getStatus()));
    }

    private static int value(Integer v) {
        return v == null ? 0 : v;
    }
}

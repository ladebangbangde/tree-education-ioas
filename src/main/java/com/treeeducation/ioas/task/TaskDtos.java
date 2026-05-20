package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

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
                           Instant createdAt,
                           Instant completedAt,
                           Instant updatedAt) {}

    public static Response of(Task t) {
        return of(t, null, List.of());
    }

    public static Response of(Task t, ContentPackage p, List<AssetFile> files) {
        int scriptCount = p == null ? 0 : value(p.getScriptCount());
        int videoCount = p == null ? 0 : value(p.getVideoCount());
        int imageCount = p == null ? 0 : value(p.getImageCount());
        int fileTotal = isUploadTask(t) ? 1 : scriptCount + videoCount + imageCount;
        int successCount = isSuccess(t) ? fileTotal : 0;
        int failedCount = isFailed(t) ? fileTotal : 0;
        int progress = isFailed(t) ? 100 : value(t.getProgress());

        return new Response(t.getId(), t.getTitle(), t.getTaskType(), t.getRoleType(), t.getRelatedPackageId(),
                p == null ? null : p.getTopicName(),
                p == null ? null : p.getOperatorId(),
                p == null ? null : p.getOperatorName(),
                p == null ? null : p.getFullPath(),
                scriptCount,
                videoCount,
                imageCount,
                fileTotal,
                successCount,
                failedCount,
                t.getRelatedLeadId(),
                t.getAssigneeId(),
                t.getAssigneeName(),
                t.getStatus(),
                progress,
                t.getErrorMessage(),
                t.getCreatedAt(),
                t.getCompletedAt(),
                t.getUpdatedAt());
    }

    private static boolean isUploadTask(Task t) {
        return t != null && t.getTaskType() == TaskType.media_upload;
    }

    private static boolean isSuccess(Task t) {
        return t != null && "success".equalsIgnoreCase(t.getStatus());
    }

    private static boolean isFailed(Task t) {
        return t != null && "failed".equalsIgnoreCase(t.getStatus());
    }

    private static int value(Integer v) {
        return v == null ? 0 : v;
    }
}

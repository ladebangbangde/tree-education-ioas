package com.treeeducation.ioas.task;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Task DTOs. */
public final class TaskDtos {
    private TaskDtos() {}

    @Schema(description = "更新任务请求")
    public record UpdateRequest(String status, Integer progress, String errorMessage, Long assigneeId, String assigneeName) {}

    @Schema(description = "任务响应")
    public record Response(Long id, TaskType taskType, TaskRoleType roleType, Long relatedPackageId,
                           Long relatedLeadId, Long assigneeId, String assigneeName, String status,
                           Integer progress, String errorMessage, Instant createdAt, Instant completedAt,
                           Instant updatedAt) {}

    public static Response of(Task t) {
        return new Response(t.getId(), t.getTaskType(), t.getRoleType(), t.getRelatedPackageId(),
                t.getRelatedLeadId(), t.getAssigneeId(), t.getAssigneeName(), t.getStatus(), t.getProgress(),
                t.getErrorMessage(), t.getCreatedAt(), t.getCompletedAt(), t.getUpdatedAt());
    }
}

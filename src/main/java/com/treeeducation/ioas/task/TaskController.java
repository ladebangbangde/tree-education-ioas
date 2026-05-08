package com.treeeducation.ioas.task;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Task APIs for media and operators. */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Task", description = "媒体任务、运营任务与联动更新")
public class TaskController {
    private final TaskRepository repo;
    private final AuditLogRepository audits;

    public TaskController(TaskRepository repo, AuditLogRepository audits) {
        this.repo = repo;
        this.audits = audits;
    }

    @GetMapping("/media")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "媒体任务列表")
    public ApiResponse<PageResponse<TaskDtos.Response>> media(@RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(PageResponse.of(repo.findByRoleType(TaskRoleType.media).stream().map(TaskDtos::of).toList(), pageNum, pageSize));
    }

    @GetMapping("/operator")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "运营任务列表")
    public ApiResponse<PageResponse<TaskDtos.Response>> operator(@RequestParam(defaultValue = "1") int pageNum,
                                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(PageResponse.of(repo.findByRoleType(TaskRoleType.operator).stream().map(TaskDtos::of).toList(), pageNum, pageSize));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "任务联动更新")
    public ApiResponse<TaskDtos.Response> update(@PathVariable Long id, @RequestBody TaskDtos.UpdateRequest r,
                                                 @AuthenticationPrincipal UserPrincipal p) {
        Task t = repo.findById(id).orElseThrow(() -> BusinessException.notFound("任务不存在"));
        if (r.status() != null) t.setStatus(r.status());
        if (r.progress() != null) t.setProgress(r.progress());
        if (r.errorMessage() != null) t.setErrorMessage(r.errorMessage());
        if (r.assigneeId() != null) t.setAssigneeId(r.assigneeId());
        if (r.assigneeName() != null) t.setAssigneeName(r.assigneeName());
        if (MediaTaskStatus.success.name().equals(t.getStatus()) || OperatorTaskStatus.completed.name().equals(t.getStatus())) {
            t.setCompletedAt(Instant.now());
        }
        t.setUpdatedAt(Instant.now());
        repo.save(t);
        AuditLog log = new AuditLog();
        log.setAction(AuditAction.update_task);
        log.setTargetType("task");
        log.setTargetId(id);
        log.setActorId(p.id());
        log.setDetail(t.getStatus());
        audits.save(log);
        return ApiResponse.ok(TaskDtos.of(t));
    }
}

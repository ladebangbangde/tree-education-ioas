package com.treeeducation.ioas.task;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.common.PageResponse;
import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.assetfile.AssetFileRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.task.dto.TaskBatchDeleteRequest;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Task", description = "媒体任务、运营任务与联动更新")
public class TaskController {
    private final TaskRepository repo;
    private final ContentPackageRepository packages;
    private final AssetFileRepository assetFiles;
    private final AuditLogRepository audits;
    private final TaskLogService taskLogService;
    private final MinioClient minioClient;

    public TaskController(TaskRepository repo,
                          ContentPackageRepository packages,
                          AssetFileRepository assetFiles,
                          AuditLogRepository audits,
                          TaskLogService taskLogService,
                          @Qualifier("minioClient") MinioClient minioClient) {
        this.repo = repo;
        this.packages = packages;
        this.assetFiles = assetFiles;
        this.audits = audits;
        this.taskLogService = taskLogService;
        this.minioClient = minioClient;
    }

    @GetMapping("/media")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "媒体任务列表")
    public ApiResponse<PageResponse<TaskDtos.Response>> media(@RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @AuthenticationPrincipal UserPrincipal p) {
        List<TaskDtos.Response> rows = repo.findByRoleType(TaskRoleType.media).stream()
                .filter(t -> isSuperAdmin(p) || t.getAssigneeId() == null || p.id().equals(t.getAssigneeId()))
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(PageResponse.of(rows, pageNum, pageSize));
    }

    @GetMapping("/operator")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "运营任务列表")
    public ApiResponse<PageResponse<TaskDtos.Response>> operator(@RequestParam(defaultValue = "1") int pageNum,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @AuthenticationPrincipal UserPrincipal p) {
        List<TaskDtos.Response> rows = repo.findByRoleType(TaskRoleType.operator).stream()
                .filter(t -> isSuperAdmin(p) || t.getAssigneeId() == null || p.id().equals(t.getAssigneeId()))
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(PageResponse.of(rows, pageNum, pageSize));
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "查看任务日志")
    public ApiResponse<List<String>> logs(@PathVariable Long id,
                                          @RequestParam(defaultValue = "200") int lines,
                                          @AuthenticationPrincipal UserPrincipal p) {
        Task t = repo.findById(id).orElseThrow(() -> BusinessException.notFound("任务不存在"));
        assertOwner(t, p);
        return ApiResponse.ok(taskLogService.tail(id, Math.min(Math.max(lines, 1), 1000)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消任务")
    public ApiResponse<TaskDtos.Response> cancel(@PathVariable Long id,
                                                 @AuthenticationPrincipal UserPrincipal p) {
        Task t = repo.findById(id).orElseThrow(() -> BusinessException.notFound("任务不存在"));
        assertOwner(t, p);
        if ("success".equalsIgnoreCase(t.getStatus()) || "failed".equalsIgnoreCase(t.getStatus()) || "cancelled".equalsIgnoreCase(t.getStatus())) {
            return ApiResponse.ok(toResponse(t));
        }
        t.setStatus("cancelled");
        t.setProgress(100);
        t.setCompletedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        t.setErrorMessage("用户取消任务");
        repo.save(t);
        taskLogService.warn(id, "task cancelled by userId=" + p.id());
        return ApiResponse.ok(toResponse(t));
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除任务并可永久删除对象文件")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody TaskBatchDeleteRequest request,
                                                        @AuthenticationPrincipal UserPrincipal p) {
        List<Long> ids = request == null || request.taskIds() == null ? List.of() : request.taskIds().stream().distinct().toList();
        boolean purgeFiles = request == null || request.purgeFiles() == null || request.purgeFiles();
        if (ids.isEmpty()) {
            throw BusinessException.badRequest("请选择要删除的任务");
        }

        int deletedTasks = 0;
        int deletedObjects = 0;
        int deletedAssetRows = 0;
        Map<Long, String> skipped = new LinkedHashMap<>();

        for (Long id : ids) {
            Task task = repo.findById(id).orElse(null);
            if (task == null) {
                skipped.put(id, "任务不存在");
                continue;
            }
            try {
                assertOwner(task, p);
                if (purgeFiles) {
                    PurgeResult result = purgeUploadObject(task);
                    deletedObjects += result.objects();
                    deletedAssetRows += result.assetRows();
                }
                taskLogService.delete(id);
                repo.delete(task);
                deletedTasks++;
            } catch (RuntimeException ex) {
                skipped.put(id, ex.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedTasks", deletedTasks);
        result.put("deletedObjects", deletedObjects);
        result.put("deletedAssetRows", deletedAssetRows);
        result.put("skipped", skipped);
        return ApiResponse.ok(result);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "任务联动更新")
    public ApiResponse<TaskDtos.Response> update(@PathVariable Long id, @RequestBody TaskDtos.UpdateRequest r,
                                                 @AuthenticationPrincipal UserPrincipal p) {
        Task t = repo.findById(id).orElseThrow(() -> BusinessException.notFound("任务不存在"));
        assertOwner(t, p);
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
        taskLogService.info(id, "task updated status=" + t.getStatus() + ", progress=" + t.getProgress());
        return ApiResponse.ok(toResponse(t));
    }

    private PurgeResult purgeUploadObject(Task task) {
        String bucket = task.getUploadBucketName();
        String objectKey = task.getUploadObjectKey();
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            return new PurgeResult(0, 0);
        }

        int deletedObjects = 0;
        int deletedAssetRows = 0;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            deletedObjects = 1;
            taskLogService.warn(task.getId(), "permanently deleted object " + bucket + "/" + objectKey);
        } catch (Exception ex) {
            taskLogService.warn(task.getId(), "delete object ignored, bucket=" + bucket + ", objectKey=" + objectKey + ", message=" + ex.getMessage());
        }

        List<AssetFile> rows = assetFiles.findByBucketNameAndObjectKey(bucket, objectKey);
        if (!rows.isEmpty()) {
            assetFiles.deleteAll(rows);
            deletedAssetRows = rows.size();
        }
        return new PurgeResult(deletedObjects, deletedAssetRows);
    }

    private TaskDtos.Response toResponse(Task task) {
        ContentPackage contentPackage = task.getRelatedPackageId() == null
                ? null
                : packages.findById(task.getRelatedPackageId()).orElse(null);
        return TaskDtos.of(task, contentPackage);
    }

    private void assertOwner(Task t, UserPrincipal p) {
        if (isSuperAdmin(p) || t.getAssigneeId() == null || p.id().equals(t.getAssigneeId())) return;
        throw BusinessException.forbidden("只能操作自己的任务");
    }

    private boolean isSuperAdmin(UserPrincipal p) {
        return p != null && "SUPER_ADMIN".equalsIgnoreCase(p.role());
    }

    private record PurgeResult(int objects, int assetRows) {}
}

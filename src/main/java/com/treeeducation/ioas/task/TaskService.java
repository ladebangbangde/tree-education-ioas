package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Keeps media/operator tasks synchronized with package, upload and lead events. */
@Service
public class TaskService {
    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional
    public void ensureMediaUploadTask(ContentPackage p) {
        Task task = tasks.findFirstByTaskTypeAndRelatedPackageId(TaskType.media_upload, p.getId()).orElseGet(Task::new);
        task.setTaskType(TaskType.media_upload);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(p.getId());
        task.setAssigneeId(p.getCreatedBy());
        task.setAssigneeName(p.getCreatedByName());
        task.setStatus(MediaTaskStatus.pending_supplement.name());
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());
        tasks.save(task);
    }

    @Transactional
    public void refreshMediaUploadTask(ContentPackage p) {
        Task task = tasks.findFirstByTaskTypeAndRelatedPackageId(TaskType.media_upload, p.getId()).orElseGet(Task::new);
        task.setTaskType(TaskType.media_upload);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(p.getId());
        task.setAssigneeId(p.getCreatedBy());
        task.setAssigneeName(p.getCreatedByName());
        boolean hasAll = p.getScriptCount() > 0 && p.getVideoCount() > 0 && p.getImageCount() > 0;
        boolean hasAny = p.getScriptCount() > 0 || p.getVideoCount() > 0 || p.getImageCount() > 0;
        task.setStatus(hasAll ? MediaTaskStatus.success.name() : hasAny ? MediaTaskStatus.partial_success.name() : MediaTaskStatus.pending_supplement.name());
        task.setProgress(hasAll ? 100 : hasAny ? 60 : 0);
        task.setCompletedAt(hasAll ? Instant.now() : null);
        task.setUpdatedAt(Instant.now());
        tasks.save(task);
    }

    @Transactional
    public void createOperatorLeadTask(Long packageId, Long leadId, Long assigneeId, String assigneeName) {
        Task task = new Task();
        task.setTaskType(TaskType.operator_lead_generate);
        task.setRoleType(TaskRoleType.operator);
        task.setRelatedPackageId(packageId);
        task.setRelatedLeadId(leadId);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setStatus(OperatorTaskStatus.pending.name());
        task.setProgress(0);
        tasks.save(task);
    }
}

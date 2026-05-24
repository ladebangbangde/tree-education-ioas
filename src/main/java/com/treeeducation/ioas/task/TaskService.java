package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Keeps media/operator tasks synchronized with package, upload and lead events. */
@Service
public class TaskService {
    public static final String PACKAGE_UPLOAD_TASK_TYPE = "PACKAGE_MEDIA_UPLOAD";
    private static final String OPERATOR_LEAD_TASK_TYPE = "OPERATOR_LEAD";
    private static final String WEBSITE_LEAD_TASK_TYPE = "WEBSITE_LEAD";

    private final TaskRepository tasks;
    private final TaskLogService taskLogService;

    public TaskService(TaskRepository tasks, TaskLogService taskLogService) {
        this.tasks = tasks;
        this.taskLogService = taskLogService;
    }

    @Transactional
    public void createPackageCreatedTask(ContentPackage p) {
        Task task = new Task();
        task.setType(TaskType.package_create.name());
        task.setTitle("主题创建 - " + p.getTopicName());
        task.setTaskType(TaskType.package_create);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(p.getId());
        task.setAssigneeId(p.getCreatedBy());
        task.setAssigneeName(p.getCreatedByName());
        task.setStatus(MediaTaskStatus.success.name());
        task.setProgress(100);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "package created successfully, packageId=" + p.getId() + ", topicName=" + p.getTopicName());
    }

    @Transactional
    public void ensureMediaUploadTask(ContentPackage p) {
        Task task = tasks.findFirstByTaskTypeAndRelatedPackageIdAndType(TaskType.media_upload, p.getId(), PACKAGE_UPLOAD_TASK_TYPE).orElseGet(Task::new);
        task.setType(PACKAGE_UPLOAD_TASK_TYPE);
        task.setTitle("主题素材上传 - " + p.getTopicName());
        task.setTaskType(TaskType.media_upload);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(p.getId());
        task.setAssigneeId(p.getCreatedBy());
        task.setAssigneeName(p.getCreatedByName());
        task.setStatus(MediaTaskStatus.pending_supplement.name());
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "package-level media upload placeholder task created, packageId=" + p.getId() + ", topicName=" + p.getTopicName());
    }

    @Transactional
    public void refreshMediaUploadTask(ContentPackage p) {
        Task task = tasks.findFirstByTaskTypeAndRelatedPackageIdAndType(TaskType.media_upload, p.getId(), PACKAGE_UPLOAD_TASK_TYPE).orElseGet(Task::new);
        task.setType(PACKAGE_UPLOAD_TASK_TYPE);
        task.setTitle("主题素材上传 - " + p.getTopicName());
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
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "package media upload task refreshed, status=" + task.getStatus() + ", progress=" + task.getProgress() + "%");
    }

    @Transactional
    public void createOperatorLeadTask(Long packageId, Long leadId, Long assigneeId, String assigneeName) {
        Task task = new Task();
        task.setType(OPERATOR_LEAD_TASK_TYPE);
        task.setTitle("运营线索生成 - " + packageId);
        task.setTaskType(TaskType.operator_lead_generate);
        task.setRoleType(TaskRoleType.operator);
        task.setRelatedPackageId(packageId);
        task.setRelatedLeadId(leadId);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setStatus(OperatorTaskStatus.pending.name());
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "operator lead generation task created, packageId=" + packageId + ", leadId=" + leadId);
    }

    @Transactional
    public void createOfficialWebsiteLeadNotificationTask(Long leadId, String studentName, String targetCountry,
                                                          Long assigneeId, String assigneeName) {
        Task task = new Task();
        task.setType(WEBSITE_LEAD_TASK_TYPE);
        task.setTitle("官网1分钟咨询新线索 - " + safe(studentName) + " / " + safe(targetCountry));
        task.setTaskType(TaskType.operator_lead_generate);
        task.setRoleType(TaskRoleType.operator);
        task.setRelatedLeadId(leadId);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setStatus(OperatorTaskStatus.pending.name());
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "official website lead notification created, leadId=" + leadId + ", studentName=" + safe(studentName) + ", targetCountry=" + safe(targetCountry) + ", assigneeName=" + safe(assigneeName));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }
}

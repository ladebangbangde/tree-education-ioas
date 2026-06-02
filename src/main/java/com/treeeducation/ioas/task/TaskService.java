package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.notification.NotificationDtos;
import com.treeeducation.ioas.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Keeps media/operator/data tasks synchronized with package, upload and lead events. */
@Service
public class TaskService {
    public static final String PACKAGE_CREATE_TASK_TYPE = "PACKAGE_CREATE";
    public static final String PACKAGE_UPLOAD_TASK_TYPE = "PACKAGE_MEDIA_UPLOAD";
    public static final String DATA_COVER_UPLOAD_TASK_TYPE = "DATA_COVER_UPLOAD";
    public static final String DATA_SCREENSHOT_UPLOAD_TASK_TYPE = "DATA_SCREENSHOT_UPLOAD";
    public static final String DATA_DAILY_REPORT_TASK_TYPE = "DATA_DAILY_REPORT";
    private static final String OPERATOR_LEAD_TASK_TYPE = "OPERATOR_LEAD";
    private static final String WEBSITE_LEAD_TASK_TYPE = "WEBSITE_LEAD";

    private final TaskRepository tasks;
    private final TaskLogService taskLogService;
    private final NotificationService notifications;

    public TaskService(TaskRepository tasks, TaskLogService taskLogService, NotificationService notifications) {
        this.tasks = tasks;
        this.taskLogService = taskLogService;
        this.notifications = notifications;
    }

    @Transactional
    public void createPackageCreatedTask(ContentPackage p) {
        Task task = new Task();
        task.setType(PACKAGE_CREATE_TASK_TYPE);
        task.setTitle("主题创建完成 - " + p.getTopicName());
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
        notifyTask(saved, "主题创建完成", "主题《" + safe(p.getTopicName()) + "》已创建完成。", "PACKAGE_CREATE_SUCCESS", 20);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createPackageCreateFailedTask(String topicName, Long assigneeId, String assigneeName, String errorMessage) {
        Task task = new Task();
        task.setType(PACKAGE_CREATE_TASK_TYPE);
        task.setTitle("主题创建失败 - " + safe(topicName));
        task.setTaskType(TaskType.package_create);
        task.setRoleType(TaskRoleType.media);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setStatus(MediaTaskStatus.failed.name());
        task.setProgress(100);
        task.setErrorMessage(trim(errorMessage, 1000));
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.error(saved.getId(), "package creation failed, topicName=" + safe(topicName) + ", message=" + safe(errorMessage), null);
        notifyTask(saved, "主题创建失败", "主题《" + safe(topicName) + "》创建失败：" + safe(errorMessage), "PACKAGE_CREATE_FAILED", 30);
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
        task.setStatus(MediaTaskStatus.uploading.name());
        task.setProgress(0);
        task.setCompletedAt(null);
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "package-level media upload task started, packageId=" + p.getId() + ", topicName=" + p.getTopicName());
    }

    @Transactional
    public void refreshMediaUploadTask(ContentPackage p) {
        Task task = tasks.findFirstByTaskTypeAndRelatedPackageIdAndType(TaskType.media_upload, p.getId(), PACKAGE_UPLOAD_TASK_TYPE).orElseGet(Task::new);
        String oldStatus = task.getStatus();
        task.setType(PACKAGE_UPLOAD_TASK_TYPE);
        task.setTitle("主题素材上传 - " + p.getTopicName());
        task.setTaskType(TaskType.media_upload);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(p.getId());
        task.setAssigneeId(p.getCreatedBy());
        task.setAssigneeName(p.getCreatedByName());
        boolean hasAll = p.getScriptCount() > 0 && p.getVideoCount() > 0 && p.getImageCount() > 0;
        boolean hasAny = p.getScriptCount() > 0 || p.getVideoCount() > 0 || p.getImageCount() > 0;
        task.setStatus(hasAll ? MediaTaskStatus.success.name() : hasAny ? MediaTaskStatus.partial_success.name() : MediaTaskStatus.uploading.name());
        task.setProgress(hasAll ? 100 : hasAny ? 60 : 0);
        task.setCompletedAt(hasAll ? Instant.now() : null);
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "package media upload task refreshed, status=" + task.getStatus() + ", progress=" + task.getProgress() + "%");
        if (!MediaTaskStatus.success.name().equals(oldStatus) && MediaTaskStatus.success.name().equals(saved.getStatus())) {
            notifyTask(saved, "主题素材上传完成", "主题《" + safe(p.getTopicName()) + "》素材已上传完成。", "PACKAGE_UPLOAD_SUCCESS", 20);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMediaUploadFailed(ContentPackage p, String errorMessage) {
        Task task = tasks.findFirstByTaskTypeAndRelatedPackageIdAndType(TaskType.media_upload, p.getId(), PACKAGE_UPLOAD_TASK_TYPE).orElseGet(Task::new);
        task.setType(PACKAGE_UPLOAD_TASK_TYPE);
        task.setTitle("主题素材上传失败 - " + p.getTopicName());
        task.setTaskType(TaskType.media_upload);
        task.setRoleType(TaskRoleType.media);
        task.setRelatedPackageId(p.getId());
        task.setAssigneeId(p.getCreatedBy());
        task.setAssigneeName(p.getCreatedByName());
        task.setStatus(MediaTaskStatus.failed.name());
        task.setProgress(100);
        task.setErrorMessage(trim(errorMessage, 1000));
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.error(saved.getId(), "package media upload failed, packageId=" + p.getId() + ", message=" + safe(errorMessage), null);
        notifyTask(saved, "主题素材上传失败", "主题《" + safe(p.getTopicName()) + "》素材上传失败：" + safe(errorMessage), "PACKAGE_UPLOAD_FAILED", 30);
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

    @Transactional
    public void createDataCoverUploadTask(Long packageId, String title, String fileName, Long fileSize, String bucketName,
                                          String objectKey, String publicUrl, Long assigneeId, String assigneeName) {
        Task saved = createDataUploadTask(DATA_COVER_UPLOAD_TASK_TYPE, TaskType.data_cover_upload,
                "封面识别上传 - " + safe(title), packageId, fileName, fileSize, bucketName, objectKey, publicUrl, assigneeId, assigneeName);
        taskLogService.info(saved.getId(), "data cover uploaded, packageId=" + packageId + ", objectKey=" + safe(objectKey));
        notifyTask(saved, "封面上传完成", "封面《" + safe(fileName) + "》已上传，等待识别处理。", "DATA_COVER_UPLOADED", 20);
    }

    @Transactional
    public void createDataScreenshotUploadTask(Long packageId, String title, String fileName, Long fileSize, String bucketName,
                                               String objectKey, String publicUrl, Long assigneeId, String assigneeName) {
        Task saved = createDataUploadTask(DATA_SCREENSHOT_UPLOAD_TASK_TYPE, TaskType.data_screenshot_upload,
                "数据截图上传 - " + safe(title), packageId, fileName, fileSize, bucketName, objectKey, publicUrl, assigneeId, assigneeName);
        taskLogService.info(saved.getId(), "data screenshot uploaded, packageId=" + packageId + ", objectKey=" + safe(objectKey));
    }

    @Transactional
    public void createDataDailyReportTask(LocalDate reportDate, Long assigneeId, String assigneeName) {
        Task task = new Task();
        task.setType(DATA_DAILY_REPORT_TASK_TYPE);
        task.setTitle("数据日报生成 - " + (reportDate == null ? LocalDate.now() : reportDate));
        task.setTaskType(TaskType.data_daily_report_generate);
        task.setRoleType(TaskRoleType.data);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setStatus(MediaTaskStatus.success.name());
        task.setProgress(100);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);
        taskLogService.info(saved.getId(), "data daily report generated, reportDate=" + (reportDate == null ? LocalDate.now() : reportDate));
        notifyTask(saved, "数据日报生成完成", "当日数据报告已生成，请在数据操作模块查看。", "DATA_DAILY_REPORT_CREATED", 20);
    }

    private Task createDataUploadTask(String type, TaskType taskType, String title, Long packageId, String fileName, Long fileSize,
                                      String bucketName, String objectKey, String publicUrl, Long assigneeId, String assigneeName) {
        Task task = new Task();
        task.setType(type);
        task.setTitle(title);
        task.setTaskType(taskType);
        task.setRoleType(TaskRoleType.data);
        task.setRelatedPackageId(packageId);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setStatus(MediaTaskStatus.success.name());
        task.setProgress(100);
        task.setFileName(fileName);
        task.setFileSize(fileSize);
        task.setUploadedBytes(fileSize);
        task.setUploadBucketName(bucketName);
        task.setUploadObjectKey(objectKey);
        task.setUploadPublicUrl(publicUrl);
        task.setCompletedAt(Instant.now());
        task.setLastProgressAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return tasks.save(task);
    }

    private void notifyTask(Task task, String title, String content, String notificationType, Integer priority) {
        if (task.getAssigneeId() == null) return;
        try {
            notifications.sendToUser(new NotificationDtos.SendRequest(
                    task.getAssigneeId(),
                    notificationRole(task),
                    title,
                    content,
                    "task",
                    task.getId(),
                    "/tasks",
                    notificationType,
                    priority
            ));
        } catch (RuntimeException ex) {
            taskLogService.warn(task.getId(), "task notification failed: " + ex.getMessage());
        }
    }

    private String notificationRole(Task task) {
        if (task.getRoleType() == TaskRoleType.operator) return "OPERATOR";
        if (task.getRoleType() == TaskRoleType.data) return "DATA";
        return "MEDIA";
    }

    private String trim(String value, int maxLength) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }
}

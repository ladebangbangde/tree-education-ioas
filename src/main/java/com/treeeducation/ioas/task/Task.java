package com.treeeducation.ioas.task;

import jakarta.persistence.*;

import java.time.Instant;

/** Cross-module task linked to packages and leads. */
@Entity
@Table(name = "ioas_task")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50) private String type;
    @Column(nullable = false, length = 160) private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private TaskType taskType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TaskRoleType roleType;
    private Long relatedPackageId;
    private Long relatedLeadId;
    private Long assigneeId;
    @Column(length = 80) private String assigneeName;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private Integer progress = 0;
    @Column(length = 1000) private String errorMessage;
    @Column(length = 100) private String uploadBucketName;
    @Column(length = 500) private String uploadObjectKey;
    @Column(length = 300) private String uploadId;
    @Column(length = 1000) private String uploadPublicUrl;
    @Column(length = 300) private String fileName;
    private Long fileSize;
    private Long uploadedBytes;
    private Long speedBytesPerSecond;
    private Long averageSpeedBytesPerSecond;
    private Integer partCount;
    private Integer completedPartCount;
    private Instant lastProgressAt;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant completedAt;
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }
    public TaskRoleType getRoleType() { return roleType; }
    public void setRoleType(TaskRoleType roleType) { this.roleType = roleType; }
    public Long getRelatedPackageId() { return relatedPackageId; }
    public void setRelatedPackageId(Long relatedPackageId) { this.relatedPackageId = relatedPackageId; }
    public Long getRelatedLeadId() { return relatedLeadId; }
    public void setRelatedLeadId(Long relatedLeadId) { this.relatedLeadId = relatedLeadId; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getUploadBucketName() { return uploadBucketName; }
    public void setUploadBucketName(String uploadBucketName) { this.uploadBucketName = uploadBucketName; }
    public String getUploadObjectKey() { return uploadObjectKey; }
    public void setUploadObjectKey(String uploadObjectKey) { this.uploadObjectKey = uploadObjectKey; }
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public String getUploadPublicUrl() { return uploadPublicUrl; }
    public void setUploadPublicUrl(String uploadPublicUrl) { this.uploadPublicUrl = uploadPublicUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getUploadedBytes() { return uploadedBytes; }
    public void setUploadedBytes(Long uploadedBytes) { this.uploadedBytes = uploadedBytes; }
    public Long getSpeedBytesPerSecond() { return speedBytesPerSecond; }
    public void setSpeedBytesPerSecond(Long speedBytesPerSecond) { this.speedBytesPerSecond = speedBytesPerSecond; }
    public Long getAverageSpeedBytesPerSecond() { return averageSpeedBytesPerSecond; }
    public void setAverageSpeedBytesPerSecond(Long averageSpeedBytesPerSecond) { this.averageSpeedBytesPerSecond = averageSpeedBytesPerSecond; }
    public Integer getPartCount() { return partCount; }
    public void setPartCount(Integer partCount) { this.partCount = partCount; }
    public Integer getCompletedPartCount() { return completedPartCount; }
    public void setCompletedPartCount(Integer completedPartCount) { this.completedPartCount = completedPartCount; }
    public Instant getLastProgressAt() { return lastProgressAt; }
    public void setLastProgressAt(Instant lastProgressAt) { this.lastProgressAt = lastProgressAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

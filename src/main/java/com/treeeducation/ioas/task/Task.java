package com.treeeducation.ioas.task;

import jakarta.persistence.*;

import java.time.Instant;

/** Cross-module task linked to packages and leads. */
@Entity
@Table(name = "ioas_task")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
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
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant completedAt;
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

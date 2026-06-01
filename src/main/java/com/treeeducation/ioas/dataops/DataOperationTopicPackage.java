package com.treeeducation.ioas.dataops;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "data_operation_topic_package")
public class DataOperationTopicPackage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "package_no", nullable = false, unique = true, length = 40)
    private String packageNo;
    @Column(name = "topic_date", nullable = false)
    private LocalDate topicDate;
    @Column(name = "display_name", nullable = false, length = 500)
    private String displayName;
    @Column(name = "folder_name", nullable = false, length = 500)
    private String folderName;
    @Column(name = "operator_user_ids", length = 1000)
    private String operatorUserIds;
    @Column(name = "operator_names", length = 1000)
    private String operatorNames;
    @Column(name = "media_user_ids", length = 1000)
    private String mediaUserIds;
    @Column(name = "media_names", length = 1000)
    private String mediaNames;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_by_name", length = 80)
    private String createdByName;
    @Column(name = "status", nullable = false, length = 40)
    private String status = "draft";
    @Column(name = "report_status", nullable = false, length = 40)
    private String reportStatus = "not_generated";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public LocalDate getTopicDate() { return topicDate; }
    public void setTopicDate(LocalDate topicDate) { this.topicDate = topicDate; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getOperatorUserIds() { return operatorUserIds; }
    public void setOperatorUserIds(String operatorUserIds) { this.operatorUserIds = operatorUserIds; }
    public String getOperatorNames() { return operatorNames; }
    public void setOperatorNames(String operatorNames) { this.operatorNames = operatorNames; }
    public String getMediaUserIds() { return mediaUserIds; }
    public void setMediaUserIds(String mediaUserIds) { this.mediaUserIds = mediaUserIds; }
    public String getMediaNames() { return mediaNames; }
    public void setMediaNames(String mediaNames) { this.mediaNames = mediaNames; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

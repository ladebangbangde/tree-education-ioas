package com.treeeducation.ioas.media.contentpackage;

import jakarta.persistence.*;

import java.time.Instant;

/** Media topic package. Files are always uploaded under a package. */
@Entity
@Table(name = "content_package")
public class ContentPackage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 40) private String packageNo;
    @Column(nullable = false, length = 160) private String topicName;
    private Long operatorId;
    @Column(length = 80) private String operatorName;
    private Integer folderYear;
    private Integer folderMonth;
    private Integer folderDay;
    @Column(length = 600) private String fullPath;
    @Column(length = 1000) private String coverUrl;
    @Column(nullable = false) private Integer scriptCount = 0;
    @Column(nullable = false) private Integer videoCount = 0;
    @Column(nullable = false) private Integer imageCount = 0;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private ContentPackageStatus uploadStatus = ContentPackageStatus.pending_upload;
    @Column(nullable = false) private Long createdBy;
    @Column(length = 80) private String createdByName;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Column(nullable = false) private Boolean isDeleted = false;
    private Instant deletedAt;
    private Long deletedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public Integer getFolderYear() { return folderYear; }
    public void setFolderYear(Integer folderYear) { this.folderYear = folderYear; }
    public Integer getFolderMonth() { return folderMonth; }
    public void setFolderMonth(Integer folderMonth) { this.folderMonth = folderMonth; }
    public Integer getFolderDay() { return folderDay; }
    public void setFolderDay(Integer folderDay) { this.folderDay = folderDay; }
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Integer getScriptCount() { return scriptCount; }
    public void setScriptCount(Integer scriptCount) { this.scriptCount = scriptCount; }
    public Integer getVideoCount() { return videoCount; }
    public void setVideoCount(Integer videoCount) { this.videoCount = videoCount; }
    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }
    public ContentPackageStatus getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(ContentPackageStatus uploadStatus) { this.uploadStatus = uploadStatus; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
}

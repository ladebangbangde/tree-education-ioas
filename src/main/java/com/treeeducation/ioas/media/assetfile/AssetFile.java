package com.treeeducation.ioas.media.assetfile;

import jakarta.persistence.*;

import java.time.Instant;

/** Asset metadata stored in MySQL while object bytes live in MinIO/OSS. */
@Entity
@Table(name = "asset_file")
public class AssetFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 40) private String fileNo;
    @Column(nullable = false) private Long packageId;
    @Column(nullable = false, length = 255) private String fileName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private AssetFileType fileType;
    @Column(nullable = false, length = 120) private String mimeType;
    @Column(nullable = false) private Long fileSize;
    @Column(nullable = false, length = 100) private String bucketName;
    @Column(nullable = false, length = 500) private String objectKey;
    @Column(length = 1000) private String thumbnailUrl;
    @Column(length = 1000) private String previewUrl;
    @Column(nullable = false) private Integer sortOrder = 0;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private UploadStatus uploadStatus = UploadStatus.success;
    @Column(nullable = false) private Long createdBy;
    @Column(length = 80) private String createdByName;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Column(nullable = false) private Boolean isDeleted = false;
    private Instant deletedAt;
    private Long deletedBy;
    private Instant purgeAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileNo() { return fileNo; }
    public void setFileNo(String fileNo) { this.fileNo = fileNo; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public AssetFileType getFileType() { return fileType; }
    public void setFileType(AssetFileType fileType) { this.fileType = fileType; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public UploadStatus getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(UploadStatus uploadStatus) { this.uploadStatus = uploadStatus; }
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
    public Instant getPurgeAt() { return purgeAt; }
    public void setPurgeAt(Instant purgeAt) { this.purgeAt = purgeAt; }
}

package com.treeeducation.ioas.dataops;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "data_operation_platform_topic")
public class DataOperationPlatformTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "package_id", nullable = false)
    private Long packageId;
    @Column(name = "platform_code", nullable = false, length = 40)
    private String platformCode;
    @Column(name = "platform_name", nullable = false, length = 80)
    private String platformName;
    @Column(name = "sub_topic_name", nullable = false, length = 300)
    private String subTopicName;
    @Column(name = "cover_asset_id")
    private Long coverAssetId;
    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;
    @Column(name = "ocr_status", nullable = false, length = 40)
    private String ocrStatus = "pending";
    @Column(name = "ocr_title", length = 500)
    private String ocrTitle;
    @Column(name = "ocr_account_name", length = 200)
    private String ocrAccountName;
    @Column(name = "ocr_publish_time", length = 80)
    private String ocrPublishTime;
    @Column(name = "ocr_payload_json")
    private String ocrPayloadJson;
    @Column(name = "status", nullable = false, length = 40)
    private String status = "draft";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }
    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }
    public String getSubTopicName() { return subTopicName; }
    public void setSubTopicName(String subTopicName) { this.subTopicName = subTopicName; }
    public Long getCoverAssetId() { return coverAssetId; }
    public void setCoverAssetId(Long coverAssetId) { this.coverAssetId = coverAssetId; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
    public String getOcrTitle() { return ocrTitle; }
    public void setOcrTitle(String ocrTitle) { this.ocrTitle = ocrTitle; }
    public String getOcrAccountName() { return ocrAccountName; }
    public void setOcrAccountName(String ocrAccountName) { this.ocrAccountName = ocrAccountName; }
    public String getOcrPublishTime() { return ocrPublishTime; }
    public void setOcrPublishTime(String ocrPublishTime) { this.ocrPublishTime = ocrPublishTime; }
    public String getOcrPayloadJson() { return ocrPayloadJson; }
    public void setOcrPayloadJson(String ocrPayloadJson) { this.ocrPayloadJson = ocrPayloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

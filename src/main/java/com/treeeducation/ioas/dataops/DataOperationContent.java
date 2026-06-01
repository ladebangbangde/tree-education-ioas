package com.treeeducation.ioas.dataops;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "data_operation_content")
public class DataOperationContent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "package_id", nullable = false)
    private Long packageId;
    @Column(name = "platform_topic_id", nullable = false)
    private Long platformTopicId;
    @Column(name = "platform_code", nullable = false, length = 40)
    private String platformCode;
    @Column(name = "content_title", nullable = false, length = 500)
    private String contentTitle;
    @Column(name = "content_summary", length = 1000)
    private String contentSummary;
    @Column(name = "content_date")
    private LocalDate contentDate;
    @Column(name = "screenshot_count", nullable = false)
    private Integer screenshotCount = 0;
    @Column(name = "recognition_status", nullable = false, length = 40)
    private String recognitionStatus = "pending";
    @Column(name = "data_payload_json")
    private String dataPayloadJson;
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
    public Long getPlatformTopicId() { return platformTopicId; }
    public void setPlatformTopicId(Long platformTopicId) { this.platformTopicId = platformTopicId; }
    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }
    public String getContentTitle() { return contentTitle; }
    public void setContentTitle(String contentTitle) { this.contentTitle = contentTitle; }
    public String getContentSummary() { return contentSummary; }
    public void setContentSummary(String contentSummary) { this.contentSummary = contentSummary; }
    public LocalDate getContentDate() { return contentDate; }
    public void setContentDate(LocalDate contentDate) { this.contentDate = contentDate; }
    public Integer getScreenshotCount() { return screenshotCount == null ? 0 : screenshotCount; }
    public void setScreenshotCount(Integer screenshotCount) { this.screenshotCount = screenshotCount == null ? 0 : screenshotCount; }
    public String getRecognitionStatus() { return recognitionStatus; }
    public void setRecognitionStatus(String recognitionStatus) { this.recognitionStatus = recognitionStatus; }
    public String getDataPayloadJson() { return dataPayloadJson; }
    public void setDataPayloadJson(String dataPayloadJson) { this.dataPayloadJson = dataPayloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

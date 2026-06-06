package com.treeeducation.ioas.recognition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_recognition_records")
public class DataRecognitionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "platform", nullable = false, length = 64)
    private String platform = "UNKNOWN";

    @Column(name = "scene", nullable = false, length = 64)
    private String scene = "CONTENT_DETAIL";

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType = "UNKNOWN";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DataRecognitionStatus status = DataRecognitionStatus.PENDING_REVIEW;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_id", length = 128)
    private String accountId;

    @Column(name = "content_title", length = 512)
    private String contentTitle;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "metrics_json", columnDefinition = "LONGTEXT")
    private String metricsJson;

    @Column(name = "image_text_stats_json", columnDefinition = "LONGTEXT")
    private String imageTextStatsJson;

    @Column(name = "video_stats_json", columnDefinition = "LONGTEXT")
    private String videoStatsJson;

    @Column(name = "key_value_metrics_json", columnDefinition = "LONGTEXT")
    private String keyValueMetricsJson;

    @Column(name = "corrected_result_json", columnDefinition = "LONGTEXT")
    private String correctedResultJson;

    @Column(name = "review_remark", length = 1024)
    private String reviewRemark;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public DataRecognitionStatus getStatus() { return status; }
    public void setStatus(DataRecognitionStatus status) { this.status = status; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getContentTitle() { return contentTitle; }
    public void setContentTitle(String contentTitle) { this.contentTitle = contentTitle; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getImageTextStatsJson() { return imageTextStatsJson; }
    public void setImageTextStatsJson(String imageTextStatsJson) { this.imageTextStatsJson = imageTextStatsJson; }
    public String getVideoStatsJson() { return videoStatsJson; }
    public void setVideoStatsJson(String videoStatsJson) { this.videoStatsJson = videoStatsJson; }
    public String getKeyValueMetricsJson() { return keyValueMetricsJson; }
    public void setKeyValueMetricsJson(String keyValueMetricsJson) { this.keyValueMetricsJson = keyValueMetricsJson; }
    public String getCorrectedResultJson() { return correctedResultJson; }
    public void setCorrectedResultJson(String correctedResultJson) { this.correctedResultJson = correctedResultJson; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

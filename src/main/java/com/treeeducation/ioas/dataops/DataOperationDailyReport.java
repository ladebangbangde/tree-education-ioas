package com.treeeducation.ioas.dataops;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "data_operation_daily_report")
public class DataOperationDailyReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;
    @Column(name = "package_count", nullable = false)
    private Integer packageCount = 0;
    @Column(name = "content_count", nullable = false)
    private Integer contentCount = 0;
    @Column(name = "screenshot_count", nullable = false)
    private Integer screenshotCount = 0;
    @Column(name = "douyin_count", nullable = false)
    private Integer douyinCount = 0;
    @Column(name = "xiaohongshu_count", nullable = false)
    private Integer xiaohongshuCount = 0;
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;
    @Column(name = "report_status", nullable = false, length = 40)
    private String reportStatus = "created";
    @Column(name = "report_url", length = 1000)
    private String reportUrl;
    @Column(name = "summary_json")
    private String summaryJson;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public Integer getPackageCount() { return packageCount == null ? 0 : packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount == null ? 0 : packageCount; }
    public Integer getContentCount() { return contentCount == null ? 0 : contentCount; }
    public void setContentCount(Integer contentCount) { this.contentCount = contentCount == null ? 0 : contentCount; }
    public Integer getScreenshotCount() { return screenshotCount == null ? 0 : screenshotCount; }
    public void setScreenshotCount(Integer screenshotCount) { this.screenshotCount = screenshotCount == null ? 0 : screenshotCount; }
    public Integer getDouyinCount() { return douyinCount == null ? 0 : douyinCount; }
    public void setDouyinCount(Integer douyinCount) { this.douyinCount = douyinCount == null ? 0 : douyinCount; }
    public Integer getXiaohongshuCount() { return xiaohongshuCount == null ? 0 : xiaohongshuCount; }
    public void setXiaohongshuCount(Integer xiaohongshuCount) { this.xiaohongshuCount = xiaohongshuCount == null ? 0 : xiaohongshuCount; }
    public Integer getFailedCount() { return failedCount == null ? 0 : failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount == null ? 0 : failedCount; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public String getReportUrl() { return reportUrl; }
    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

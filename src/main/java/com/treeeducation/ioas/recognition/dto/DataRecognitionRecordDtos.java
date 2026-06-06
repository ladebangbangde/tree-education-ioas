package com.treeeducation.ioas.recognition.dto;

import com.treeeducation.ioas.recognition.DataRecognitionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DataRecognitionRecordDtos {
    private DataRecognitionRecordDtos() {
    }

    @Schema(description = "识别记录分页结果")
    public record PageResult<T>(
            long total,
            int pageNum,
            int pageSize,
            List<T> records
    ) {
    }

    @Schema(description = "识别记录列表项")
    public record RecordSummary(
            Long id,
            String requestId,
            String platform,
            String scene,
            String contentType,
            DataRecognitionStatus status,
            String accountName,
            String accountId,
            String contentTitle,
            BigDecimal confidence,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "识别记录详情")
    public record RecordDetail(
            Long id,
            String requestId,
            String platform,
            String scene,
            String contentType,
            DataRecognitionStatus status,
            String accountName,
            String accountId,
            String contentTitle,
            BigDecimal confidence,
            String rawText,
            Object result,
            Object metrics,
            Object imageTextStats,
            Object videoStats,
            Map<String, Object> keyValueMetrics,
            Object correctedResult,
            String reviewRemark,
            String reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "识别并保存响应")
    public record RecognizeAndSaveResponse(
            Long recordId,
            RecognitionDtos.RecognitionResponse recognition
    ) {
    }

    @Schema(description = "确认识别结果请求")
    public record ConfirmRequest(
            Object correctedResult,
            String reviewRemark,
            String reviewedBy
    ) {
    }

    @Schema(description = "驳回识别结果请求")
    public record RejectRequest(
            String reviewRemark,
            String reviewedBy
    ) {
    }
}

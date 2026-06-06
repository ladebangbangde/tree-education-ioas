package com.treeeducation.ioas.recognition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treeeducation.ioas.recognition.dto.RecognitionDtos.RecognitionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Persists OCR recognition results for manual review and later statistics ingestion. */
@Service
public class DataRecognitionService {
    private final DataRecognitionRecordRepository repository;
    private final ObjectMapper objectMapper;

    public DataRecognitionService(DataRecognitionRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DataRecognitionRecord savePendingReview(RecognitionResponse response) {
        DataRecognitionRecord record = new DataRecognitionRecord();
        record.setRequestId(response.requestId());
        record.setPlatform(defaultValue(response.platform(), "UNKNOWN"));
        record.setScene(defaultValue(response.scene(), "CONTENT_DETAIL"));
        record.setContentType(defaultValue(response.contentType(), "UNKNOWN"));
        record.setStatus(DataRecognitionStatus.PENDING_REVIEW);
        record.setRawText(response.rawText());
        record.setResultJson(toJson(response.result()));

        if (response.result() != null) {
            record.setAccountName(response.result().accountName());
            record.setAccountId(response.result().accountId());
            record.setContentTitle(response.result().contentTitle());
            if (response.result().confidence() != null) {
                record.setConfidence(BigDecimal.valueOf(response.result().confidence()));
            }
            record.setMetricsJson(toJson(response.result().metrics()));
            record.setImageTextStatsJson(toJson(response.result().imageTextStats()));
            record.setVideoStatsJson(toJson(response.result().videoStats()));
            record.setKeyValueMetricsJson(toJson(response.result().keyValueMetrics()));
        }
        return repository.save(record);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize recognition result", e);
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

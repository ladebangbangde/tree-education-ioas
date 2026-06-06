package com.treeeducation.ioas.recognition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.ConfirmRequest;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.PageResult;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RecordDetail;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RecordSummary;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RejectRequest;
import com.treeeducation.ioas.recognition.dto.RecognitionDtos.RecognitionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

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

    @Transactional(readOnly = true)
    public PageResult<RecordSummary> list(String status, String contentType, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(pageNum - 1, 0), Math.max(pageSize, 1), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DataRecognitionRecord> page;
        if (status != null && !status.isBlank()) {
            page = repository.findByStatusOrderByCreatedAtDesc(DataRecognitionStatus.valueOf(status.trim().toUpperCase()), pageable);
        } else if (contentType != null && !contentType.isBlank()) {
            page = repository.findByContentTypeOrderByCreatedAtDesc(contentType.trim().toUpperCase(), pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return new PageResult<>(page.getTotalElements(), pageNum, pageSize, page.getContent().stream().map(this::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public RecordDetail getDetail(Long id) {
        return toDetail(getRecord(id));
    }

    @Transactional
    public RecordDetail confirm(Long id, ConfirmRequest request) {
        DataRecognitionRecord record = getRecord(id);
        record.setStatus(DataRecognitionStatus.CONFIRMED);
        record.setCorrectedResultJson(toJson(request == null ? null : request.correctedResult()));
        record.setReviewRemark(request == null ? null : request.reviewRemark());
        record.setReviewedBy(request == null ? null : request.reviewedBy());
        record.setReviewedAt(LocalDateTime.now());
        return toDetail(repository.save(record));
    }

    @Transactional
    public RecordDetail reject(Long id, RejectRequest request) {
        DataRecognitionRecord record = getRecord(id);
        record.setStatus(DataRecognitionStatus.REJECTED);
        record.setReviewRemark(request == null ? null : request.reviewRemark());
        record.setReviewedBy(request == null ? null : request.reviewedBy());
        record.setReviewedAt(LocalDateTime.now());
        return toDetail(repository.save(record));
    }

    private DataRecognitionRecord getRecord(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "data recognition record not found"));
    }

    private RecordSummary toSummary(DataRecognitionRecord record) {
        return new RecordSummary(
                record.getId(),
                record.getRequestId(),
                record.getPlatform(),
                record.getScene(),
                record.getContentType(),
                record.getStatus(),
                record.getAccountName(),
                record.getAccountId(),
                record.getContentTitle(),
                record.getConfidence(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private RecordDetail toDetail(DataRecognitionRecord record) {
        return new RecordDetail(
                record.getId(),
                record.getRequestId(),
                record.getPlatform(),
                record.getScene(),
                record.getContentType(),
                record.getStatus(),
                record.getAccountName(),
                record.getAccountId(),
                record.getContentTitle(),
                record.getConfidence(),
                record.getRawText(),
                readJson(record.getResultJson()),
                readJson(record.getMetricsJson()),
                readJson(record.getImageTextStatsJson()),
                readJson(record.getVideoStatsJson()),
                readJsonMap(record.getKeyValueMetricsJson()),
                readJson(record.getCorrectedResultJson()),
                record.getReviewRemark(),
                record.getReviewedBy(),
                record.getReviewedAt(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
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

    private Object readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", value);
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

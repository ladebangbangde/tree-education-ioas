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
import com.treeeducation.ioas.recognition.dto.RecognitionDtos.RecognitionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DataRecognitionRecordService {
    private final DataRecognitionRecordRepository repository;
    private final ObjectMapper objectMapper;

    public DataRecognitionRecordService(DataRecognitionRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DataRecognitionRecord savePending(RecognitionResponse response) {
        DataRecognitionRecord record = new DataRecognitionRecord();
        record.setRequestId(response == null ? null : response.requestId());
        record.setPlatform(defaultValue(response == null ? null : response.platform(), "UNKNOWN"));
        record.setScene(defaultValue(response == null ? null : response.scene(), "CONTENT_DETAIL"));
        record.setContentType(defaultValue(response == null ? null : response.contentType(), "UNKNOWN"));
        record.setStatus(DataRecognitionStatus.PENDING_REVIEW);
        record.setRawText(response == null ? null : response.rawText());

        RecognitionResult result = response == null ? null : response.result();
        if (result != null) {
            record.setAccountName(result.accountName());
            record.setAccountId(result.accountId());
            record.setContentTitle(result.contentTitle());
            record.setContentType(defaultValue(result.contentType(), record.getContentType()));
            record.setConfidence(result.confidence() == null ? null : BigDecimal.valueOf(result.confidence()));
            record.setResultJson(toJson(result));
            record.setMetricsJson(toJson(result.metrics()));
            record.setImageTextStatsJson(toJson(result.imageTextStats()));
            record.setVideoStatsJson(toJson(result.videoStats()));
            record.setKeyValueMetricsJson(toJson(result.keyValueMetrics()));
        } else {
            record.setResultJson(toJson(response));
        }
        return repository.save(record);
    }

    @Transactional(readOnly = true)
    public PageResult<RecordSummary> list(String status, String contentType, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePageNum - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DataRecognitionRecord> page;
        if (status != null && !status.isBlank()) {
            page = repository.findByStatusOrderByCreatedAtDesc(DataRecognitionStatus.valueOf(status.toUpperCase()), pageable);
        } else if (contentType != null && !contentType.isBlank()) {
            page = repository.findByContentTypeOrderByCreatedAtDesc(contentType.toUpperCase(), pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return new PageResult<>(page.getTotalElements(), safePageNum, safePageSize, page.map(this::toSummary).toList());
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
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "data recognition record not found"));
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
                fromJson(record.getResultJson(), Object.class),
                fromJson(record.getMetricsJson(), Object.class),
                fromJson(record.getImageTextStatsJson(), Object.class),
                fromJson(record.getVideoStatsJson(), Object.class),
                fromJson(record.getKeyValueMetricsJson(), new TypeReference<Map<String, Object>>() {}),
                fromJson(record.getCorrectedResultJson(), Object.class),
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to serialize recognition data", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to deserialize recognition data", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to deserialize recognition data", e);
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

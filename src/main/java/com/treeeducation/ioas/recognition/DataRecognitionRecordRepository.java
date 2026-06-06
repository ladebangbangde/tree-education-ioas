package com.treeeducation.ioas.recognition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataRecognitionRecordRepository extends JpaRepository<DataRecognitionRecord, Long> {
    Page<DataRecognitionRecord> findByStatusOrderByCreatedAtDesc(DataRecognitionStatus status, Pageable pageable);
    Page<DataRecognitionRecord> findByContentTypeOrderByCreatedAtDesc(String contentType, Pageable pageable);
}

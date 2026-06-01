package com.treeeducation.ioas.dataops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DataOperationContentRepository extends JpaRepository<DataOperationContent, Long> {
    List<DataOperationContent> findByPackageIdOrderByCreatedAtDesc(Long packageId);
    List<DataOperationContent> findByPlatformTopicIdOrderByCreatedAtDesc(Long platformTopicId);
    long countByContentDate(LocalDate contentDate);
}

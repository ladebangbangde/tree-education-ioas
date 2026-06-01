package com.treeeducation.ioas.dataops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DataOperationTopicPackageRepository extends JpaRepository<DataOperationTopicPackage, Long> {
    List<DataOperationTopicPackage> findByTopicDateOrderByCreatedAtDesc(LocalDate topicDate);
    List<DataOperationTopicPackage> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    List<DataOperationTopicPackage> findAllByOrderByCreatedAtDesc();
}

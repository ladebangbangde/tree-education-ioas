package com.treeeducation.ioas.dataops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataOperationPlatformTopicRepository extends JpaRepository<DataOperationPlatformTopic, Long> {
    List<DataOperationPlatformTopic> findByPackageIdOrderByCreatedAtDesc(Long packageId);
}

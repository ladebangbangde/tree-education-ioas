package com.treeeducation.ioas.media.contentpackage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ContentPackageRepository extends JpaRepository<ContentPackage, Long> {
    List<ContentPackage> findByIsDeletedFalse();
    long countByIsDeletedFalseAndCreatedAtGreaterThanEqual(Instant createdAt);
}

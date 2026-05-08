package com.treeeducation.ioas.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {
    List<Lead> findByRelatedPackageId(Long relatedPackageId);
    long countByStatus(LeadStatus status);
    long countByCreatedAtGreaterThanEqual(Instant createdAt);
}

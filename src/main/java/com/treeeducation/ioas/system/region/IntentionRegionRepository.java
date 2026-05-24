package com.treeeducation.ioas.system.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntentionRegionRepository extends JpaRepository<IntentionRegion, Long> {
    List<IntentionRegion> findByEnabledTrueOrderByDisplayOrderAscIdAsc();
    Optional<IntentionRegion> findByCodeAndEnabledTrue(String code);
}

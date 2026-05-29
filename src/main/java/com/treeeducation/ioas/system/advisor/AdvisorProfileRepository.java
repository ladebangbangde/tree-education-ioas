package com.treeeducation.ioas.system.advisor;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AdvisorProfileRepository extends JpaRepository<AdvisorProfile, Long> {
    Optional<AdvisorProfile> findByUserId(Long userId);
    List<AdvisorProfile> findByEnabledTrueOrderBySortOrderAscIdAsc();
}

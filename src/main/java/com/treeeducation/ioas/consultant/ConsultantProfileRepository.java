package com.treeeducation.ioas.consultant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConsultantProfileRepository extends JpaRepository<ConsultantProfile, Long> {
    List<ConsultantProfile> findByEnabledTrueAndAssignEnabledTrueOrderByCurrentDailyLeadsAscLastAssignedAtAscIdAsc();
    Optional<ConsultantProfile> findByUserId(Long userId);
}

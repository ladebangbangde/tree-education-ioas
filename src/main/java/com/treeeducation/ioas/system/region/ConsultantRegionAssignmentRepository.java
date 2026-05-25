package com.treeeducation.ioas.system.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultantRegionAssignmentRepository extends JpaRepository<ConsultantRegionAssignment, Long> {
    List<ConsultantRegionAssignment> findByEnabledTrueOrderByPriorityAscIdAsc();
    List<ConsultantRegionAssignment> findByRegionCodeAndEnabledTrueOrderByPriorityAscIdAsc(String regionCode);
    Optional<ConsultantRegionAssignment> findFirstByRegionCodeAndEnabledTrueOrderByPriorityAscIdAsc(String regionCode);
}

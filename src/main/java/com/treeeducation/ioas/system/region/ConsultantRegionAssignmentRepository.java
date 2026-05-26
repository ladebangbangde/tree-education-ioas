package com.treeeducation.ioas.system.region;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConsultantRegionAssignmentRepository extends JpaRepository<ConsultantRegionAssignment, Long> {
    List<ConsultantRegionAssignment> findByEnabledTrueOrderByPriorityAscIdAsc();
    List<ConsultantRegionAssignment> findByRegionCodeAndEnabledTrueOrderByPriorityAscIdAsc(String regionCode);
    Optional<ConsultantRegionAssignment> findFirstByRegionCodeAndEnabledTrueOrderByPriorityAscIdAsc(String regionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from ConsultantRegionAssignment a
            where a.enabled = true
            order by a.otherAssignCount asc, a.priority asc, a.id asc
            """)
    List<ConsultantRegionAssignment> findEnabledForOtherRegionFairAssignmentWithLock();
}

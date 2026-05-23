package com.treeeducation.ioas.consultant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ConsultantAssignmentRepository extends JpaRepository<ConsultantProfile, Long> {
    @Query("""
            select c from ConsultantProfile c
            where c.enabled = true
              and c.assignEnabled = true
              and c.currentDailyLeads < c.maxDailyLeads
              and c.id in (
                  select s.consultantId from ConsultantScope s
                  where s.regionId = :regionId and s.enabled = true
              )
            order by c.currentDailyLeads asc, c.lastAssignedAt asc, c.id asc
            """)
    List<ConsultantProfile> candidatesByRegion(@Param("regionId") Long regionId);

    @Query("""
            select c from ConsultantProfile c
            where c.enabled = true
              and c.assignEnabled = true
              and c.currentDailyLeads < c.maxDailyLeads
            order by c.currentDailyLeads asc, c.lastAssignedAt asc, c.id asc
            """)
    List<ConsultantProfile> roundRobinCandidates();
}

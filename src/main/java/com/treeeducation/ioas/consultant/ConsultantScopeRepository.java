package com.treeeducation.ioas.consultant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ConsultantScopeRepository extends JpaRepository<ConsultantScope, Long> {
    @Query("select c from ConsultantProfile c, ConsultantScope s where s.consultantId = c.id and s.regionId = :regionId and s.enabled = true and c.enabled = true and c.assignEnabled = true and c.currentDailyLeads < c.maxDailyLeads order by c.currentDailyLeads asc, c.lastAssignedAt asc, c.id asc")
    List<ConsultantProfile> candidatesByRegion(@Param("regionId") Long regionId);

    @Query("select c from ConsultantProfile c where c.enabled = true and c.assignEnabled = true and c.currentDailyLeads < c.maxDailyLeads order by c.currentDailyLeads asc, c.lastAssignedAt asc, c.id asc")
    List<ConsultantProfile> roundRobinCandidates();

    @Query("select distinct r from ConsultantRegion r, ConsultantScope s, ConsultantProfile c where s.regionId = r.id and s.consultantId = c.id and r.enabled = true and s.enabled = true and c.enabled = true and c.assignEnabled = true order by r.sortOrder asc, r.id asc")
    List<ConsultantRegion> publicAvailableRegions();
}

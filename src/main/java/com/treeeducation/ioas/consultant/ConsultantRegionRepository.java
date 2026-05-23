package com.treeeducation.ioas.consultant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConsultantRegionRepository extends JpaRepository<ConsultantRegion, Long> {
    @Query("select r from ConsultantRegion r where r.regionCode = :code and r.enabled = true")
    Optional<ConsultantRegion> activeByCode(@Param("code") String code);

    @Query("select r from ConsultantRegion r where r.enabled = true order by r.sortOrder asc, r.id asc")
    List<ConsultantRegion> activeOptions();

    @Query("""
            select distinct r from ConsultantRegion r, ConsultantScope s, ConsultantProfile c
            where r.enabled = true
              and s.enabled = true
              and c.enabled = true
              and c.assignEnabled = true
              and s.regionId = r.id
              and s.consultantId = c.id
            order by r.sortOrder asc, r.id asc
            """)
    List<ConsultantRegion> publicCoveredOptions();
}

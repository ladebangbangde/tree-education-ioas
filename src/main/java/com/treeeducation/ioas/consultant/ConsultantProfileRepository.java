package com.treeeducation.ioas.consultant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ConsultantProfileRepository extends JpaRepository<ConsultantProfile, Long> {
    List<ConsultantProfile> findByEnabledTrueAndAssignEnabledTrueOrderByCurrentDailyLeadsAscLastAssignedAtAscIdAsc();
    Optional<ConsultantProfile> findByUserId(Long userId);

    @Query("select c from ConsultantProfile c order by c.sortOrder asc, c.id asc")
    List<ConsultantProfile> managementList();

    @Query("select c from ConsultantProfile c where c.enabled = true and c.displayOnOfficial = true order by c.sortOrder asc, c.id asc")
    List<ConsultantProfile> publicList();
}

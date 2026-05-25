package com.treeeducation.ioas.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultantRegionChangeRequestRepository extends JpaRepository<ConsultantRegionChangeRequest, Long> {
    List<ConsultantRegionChangeRequest> findByConsultantUserIdOrderByRequestedAtDesc(Long consultantUserId);
    List<ConsultantRegionChangeRequest> findByStatusOrderByRequestedAtDesc(ConsultantRegionChangeStatus status);
}

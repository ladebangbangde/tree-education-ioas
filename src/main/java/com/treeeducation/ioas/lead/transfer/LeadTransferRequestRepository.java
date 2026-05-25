package com.treeeducation.ioas.lead.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadTransferRequestRepository extends JpaRepository<LeadTransferRequest, Long> {
    boolean existsByLeadIdAndStatus(Long leadId, LeadTransferStatus status);
    List<LeadTransferRequest> findByToConsultantIdOrderByRequestedAtDesc(Long toConsultantId);
    List<LeadTransferRequest> findByFromConsultantIdOrderByRequestedAtDesc(Long fromConsultantId);
}

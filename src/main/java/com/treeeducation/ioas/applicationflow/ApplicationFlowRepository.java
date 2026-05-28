package com.treeeducation.ioas.applicationflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationFlowRepository extends JpaRepository<ApplicationFlow, Long> {
    Optional<ApplicationFlow> findByStudentProfileId(Long studentProfileId);
    List<ApplicationFlow> findByOwnerConsultantIdOrderByUpdatedAtDesc(Long ownerConsultantId);
    List<ApplicationFlow> findAllByOrderByUpdatedAtDesc();
}

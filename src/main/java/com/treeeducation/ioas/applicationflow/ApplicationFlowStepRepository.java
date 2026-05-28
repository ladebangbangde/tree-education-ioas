package com.treeeducation.ioas.applicationflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationFlowStepRepository extends JpaRepository<ApplicationFlowStep, Long> {
    List<ApplicationFlowStep> findByFlowIdOrderByOrderNoAsc(Long flowId);
    Optional<ApplicationFlowStep> findByFlowIdAndStepCode(Long flowId, ApplicationStepCode stepCode);
}

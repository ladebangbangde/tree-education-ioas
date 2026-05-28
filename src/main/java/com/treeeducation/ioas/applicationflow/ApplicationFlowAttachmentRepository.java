package com.treeeducation.ioas.applicationflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationFlowAttachmentRepository extends JpaRepository<ApplicationFlowAttachment, Long> {
    List<ApplicationFlowAttachment> findByFlowIdAndDeletedFalseOrderByCreatedAtDesc(Long flowId);
    List<ApplicationFlowAttachment> findByStepIdAndDeletedFalseOrderByCreatedAtDesc(Long stepId);
    long countByStepIdAndDeletedFalse(Long stepId);
}

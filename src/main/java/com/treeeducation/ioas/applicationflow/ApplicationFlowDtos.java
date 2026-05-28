package com.treeeducation.ioas.applicationflow;

import java.time.Instant;
import java.util.List;

public class ApplicationFlowDtos {
    public record StartRequest(String remark) {}
    public record AdvanceRequest(ApplicationStepStatus status, String consultantNote, String customerVisibleNote, Long version) {}

    public record AttachmentResponse(Long id, ApplicationAttachmentType attachmentType, String originalFilename, String contentType, Long sizeBytes, String fileUrl, String note, String uploadedByName, Instant createdAt) {}

    public record StepResponse(Long id, ApplicationStepCode stepCode, Integer orderNo, String stepName, ApplicationStepStatus status, Boolean required, Integer uploadedFileCount, String consultantNote, String customerVisibleNote, Instant startedAt, Instant completedAt, Long version, List<AttachmentResponse> attachments) {}

    public record Response(Long id, Long studentProfileId, String studentNo, String studentName, Long ownerConsultantId, String ownerConsultantName, ApplicationStepCode currentStep, Integer progressPercent, Boolean completed, String remark, Instant createdAt, Instant updatedAt, Long version, List<StepResponse> steps) {}
}

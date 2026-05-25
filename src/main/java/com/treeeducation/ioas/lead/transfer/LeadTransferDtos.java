package com.treeeducation.ioas.lead.transfer;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class LeadTransferDtos {
    private LeadTransferDtos() {}

    public record CreateRequest(@NotNull Long toConsultantId, String reason) {}
    public record RespondRequest(String remark) {}
    public record ConsultantOption(Long id, String username, String displayName) {}

    public record Response(Long id, Long leadId, String leadNo, String studentName,
                           Long fromConsultantId, String fromConsultantName,
                           Long toConsultantId, String toConsultantName,
                           String reason, LeadTransferStatus status,
                           Instant requestedAt, Instant respondedAt,
                           String responseRemark, Instant createdAt, Instant updatedAt) {
        public static Response of(LeadTransferRequest row) {
            return new Response(row.getId(), row.getLeadId(), row.getLeadNo(), row.getStudentName(),
                    row.getFromConsultantId(), row.getFromConsultantName(), row.getToConsultantId(), row.getToConsultantName(),
                    row.getReason(), row.getStatus(), row.getRequestedAt(), row.getRespondedAt(),
                    row.getResponseRemark(), row.getCreatedAt(), row.getUpdatedAt());
        }
    }
}

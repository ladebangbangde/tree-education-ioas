package com.treeeducation.ioas.consultation;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class PublicConsultationDtos {
    private PublicConsultationDtos() {}

    public record RegionOption(Long id, String regionCode, String regionName, String regionType) {}

    public record OptionsResponse(List<RegionOption> regions) {}

    public record SubmitRequest(@NotBlank String studentName,
                                String phone,
                                String wechat,
                                @NotBlank String intentionRegionCode,
                                String targetMajor,
                                String degreeLevel,
                                String budget,
                                String message,
                                String sourcePage,
                                String sourceChannel) {}

    public record SubmitResponse(Long leadId,
                                 String leadNo,
                                 String status,
                                 Long assignedTo,
                                 String assignedToName,
                                 String intentionRegionCode,
                                 String intentionRegionName,
                                 String assignMode,
                                 String assignReason,
                                 String notifyStatus,
                                 Instant createdAt) {}
}

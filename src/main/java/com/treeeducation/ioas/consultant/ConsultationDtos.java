package com.treeeducation.ioas.consultant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class ConsultationDtos {
    private ConsultationDtos() {}

    public record RegionOption(Long id, String regionCode, String regionName, String regionType) {}

    public record OptionsResponse(List<RegionOption> regions) {}

    @Schema(description = "官网一分钟咨询提交请求")
    public record CreateRequest(
            @NotBlank String studentName,
            String phone,
            String wechat,
            @NotBlank String intentionRegionCode,
            String targetMajor,
            String degreeLevel,
            String budget,
            String sourcePage,
            String sourceChannel,
            String message
    ) {}

    public record CreateResponse(Long leadId, String leadNo, String status, String assignedToName,
                                 String assignMode, String assignReason) {}
}

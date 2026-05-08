package com.treeeducation.ioas.lead;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Lead DTOs. */
public final class LeadDtos {
    private LeadDtos() {}

    @Schema(description = "创建线索请求")
    public record CreateRequest(@NotNull Long relatedPackageId, String sourceType, Long operatorId,
                                @NotBlank String studentName, String phone, String wechat, String sourceChannel,
                                String targetCountry, String targetMajor, String budget, String degreeLevel,
                                LeadStatus status, Long assignedTo, String assignedToName, String remark) {}

    @Schema(description = "更新线索状态请求")
    public record StatusRequest(@NotNull LeadStatus status) {}

    @Schema(description = "更新线索请求")
    public record UpdateRequest(String remark, Long assignedTo, String assignedToName, LeadStatus status) {}

    @Schema(description = "线索响应")
    public record Response(Long id, String sourceType, Long relatedPackageId, Long operatorId, String leadNo, String studentName, String phone, String wechat,
                           String sourceChannel, String targetCountry, String targetMajor, String budget,
                           String degreeLevel, LeadStatus status, Long assignedTo, String assignedToName,
                           String relatedPackageName, Instant createdAt,
                           Instant updatedAt, String remark) {}

    public static Response of(Lead l, String packageName) {
        return new Response(l.getId(), l.getSourceType(), l.getRelatedPackageId(), l.getOperatorId(), l.getLeadNo(),
                l.getStudentName(), l.getPhone(), l.getWechat(), l.getSourceChannel(), l.getTargetCountry(),
                l.getTargetMajor(), l.getBudget(), l.getDegreeLevel(), l.getStatus(), l.getAssignedTo(),
                l.getAssignedToName(), packageName, l.getCreatedAt(), l.getUpdatedAt(), l.getRemark());
    }
}

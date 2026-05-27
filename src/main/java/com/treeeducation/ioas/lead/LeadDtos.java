package com.treeeducation.ioas.lead;

import com.treeeducation.ioas.profile.ProfileDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Lead DTOs. */
public final class LeadDtos {
    private LeadDtos() {}

    @Schema(description = "官网1分钟咨询表单提交请求")
    public record OfficialWebsiteRequest(@NotBlank String name,
                                         String age,
                                         String education,
                                         String city,
                                         @NotBlank String phone,
                                         String wechat,
                                         String destination,
                                         String intentionRegionCode,
                                         String intentionRegionName,
                                         @NotBlank String budget,
                                         String remark,
                                         String source,
                                         LeadRole leadRole) {}

    @Schema(description = "创建线索请求")
    public record CreateRequest(@NotNull Long relatedPackageId, String sourceType, LeadRole leadRole, Long operatorId,
                                @NotBlank String studentName, String phone, String wechat, String sourceChannel,
                                String targetCountry, String targetMajor, String budget, String degreeLevel,
                                LeadStatus status, Long assignedTo, String assignedToName, String remark) {}

    @Schema(description = "更新线索状态请求")
    public record StatusRequest(@NotNull LeadStatus status) {}

    @Schema(description = "更新线索请求")
    public record UpdateRequest(String studentName, String phone, String wechat, String targetCountry,
                                String targetMajor, String budget, String degreeLevel, String remark,
                                Long assignedTo, String assignedToName, LeadStatus status, LeadRole leadRole) {}

    @Schema(description = "线索响应")
    public record Response(Long id, String sourceType, LeadRole leadRole, Long relatedPackageId, Long operatorId, String leadNo,
                           String studentName, String phone, String wechat, String sourceChannel, String sourcePage,
                           String targetCountry, String targetMajor, String budget, String degreeLevel,
                           Long intentionRegionId, String intentionRegionCode, String intentionRegionName,
                           String assignMode, String assignReason, Instant assignedAt, String notifyStatus,
                           LeadStatus status, Long assignedTo, String assignedToName, String relatedPackageName,
                           Long convertedStudentId, Instant convertedAt, Long convertedBy,
                           Boolean archived, Boolean mutable,
                           Instant createdAt, Instant updatedAt, String remark,
                           ProfileDtos.PublicConsultantResponse assignedConsultant) {}

    public static Response of(Lead l, String packageName) {
        return of(l, packageName, null);
    }

    public static Response of(Lead l, String packageName, ProfileDtos.PublicConsultantResponse consultant) {
        boolean archived = l.getConvertedStudentId() != null || l.getConvertedAt() != null || l.getStatus() == LeadStatus.converted;
        return new Response(l.getId(), l.getSourceType(), l.getLeadRole(), l.getRelatedPackageId(), l.getOperatorId(), l.getLeadNo(),
                l.getStudentName(), l.getPhone(), l.getWechat(), l.getSourceChannel(), l.getSourcePage(),
                l.getTargetCountry(), l.getTargetMajor(), l.getBudget(), l.getDegreeLevel(),
                l.getIntentionRegionId(), l.getIntentionRegionCode(), l.getIntentionRegionName(),
                l.getAssignMode(), l.getAssignReason(), l.getAssignedAt(), l.getNotifyStatus(),
                l.getStatus(), l.getAssignedTo(), l.getAssignedToName(), packageName,
                l.getConvertedStudentId(), l.getConvertedAt(), l.getConvertedBy(),
                archived, !archived,
                l.getCreatedAt(), l.getUpdatedAt(), l.getRemark(), consultant);
    }
}

package com.treeeducation.ioas.student;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class StudentProfileDtos {
    private StudentProfileDtos() {}

    public record ConvertRequest(String confirmRemark,
                                 String age,
                                 String educationLevel,
                                 String province,
                                 String city,
                                 String locationText,
                                 String targetMajor,
                                 String budget,
                                 String remark) {}

    public record UpdateRequest(String studentName,
                                String phone,
                                String wechat,
                                String age,
                                String educationLevel,
                                String province,
                                String city,
                                String locationText,
                                String targetCountry,
                                String targetMajor,
                                String budget,
                                StudentProfileStatus profileStatus,
                                String remark) {}

    public record Response(Long id, String studentNo, Long sourceLeadId, String sourceLeadNo,
                           Long ownerConsultantId, String ownerConsultantName,
                           String studentName, String phone, String wechat, String age,
                           String educationLevel, String province, String city, String locationText,
                           Long intentionRegionId, String intentionRegionCode, String intentionRegionName,
                           String targetCountry, String targetMajor, String budget,
                           StudentProfileStatus profileStatus, String remark,
                           Instant createdAt, Instant updatedAt) {
        public static Response of(StudentProfile s) {
            return new Response(s.getId(), s.getStudentNo(), s.getSourceLeadId(), s.getSourceLeadNo(),
                    s.getOwnerConsultantId(), s.getOwnerConsultantName(), s.getStudentName(), s.getPhone(), s.getWechat(), s.getAge(),
                    s.getEducationLevel(), s.getProvince(), s.getCity(), s.getLocationText(),
                    s.getIntentionRegionId(), s.getIntentionRegionCode(), s.getIntentionRegionName(),
                    s.getTargetCountry(), s.getTargetMajor(), s.getBudget(), s.getProfileStatus(), s.getRemark(),
                    s.getCreatedAt(), s.getUpdatedAt());
        }
    }
}

package com.treeeducation.ioas.profile;

import com.treeeducation.ioas.system.operatorprofile.OperatorProfile;
import com.treeeducation.ioas.system.user.User;

import java.time.Instant;
import java.util.List;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record MeResponse(Long userId, String username, String displayName, String roleCode, String department,
                             Long profileId, String teamName, String phone,
                             String consultantQrPublicUrl, String publicTitle, String publicBio,
                             String specialityRegionCodes, String specialityRegionNames) {
        public static MeResponse of(User user, OperatorProfile profile) {
            return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleCode(), user.getDepartment(),
                    profile == null ? null : profile.getId(),
                    profile == null ? null : profile.getTeamName(),
                    profile == null ? null : profile.getPhone(),
                    profile == null ? null : profile.getConsultantQrPublicUrl(),
                    profile == null ? null : profile.getPublicTitle(),
                    profile == null ? null : profile.getPublicBio(),
                    profile == null ? null : profile.getSpecialityRegionCodes(),
                    profile == null ? null : profile.getSpecialityRegionNames());
        }
    }

    public record RegionChangeRequest(String requestedRegionCodes, String requestedRegionNames, String reason) {}

    public record RegionChangeResponse(Long id, Long consultantUserId, Long consultantProfileId, String consultantName,
                                       String currentRegionCodes, String currentRegionNames,
                                       String requestedRegionCodes, String requestedRegionNames,
                                       String reason, ConsultantRegionChangeStatus status,
                                       String reviewerName, String reviewRemark,
                                       Instant requestedAt, Instant reviewedAt) {
        public static RegionChangeResponse of(ConsultantRegionChangeRequest row) {
            return new RegionChangeResponse(row.getId(), row.getConsultantUserId(), row.getConsultantProfileId(), row.getConsultantName(),
                    row.getCurrentRegionCodes(), row.getCurrentRegionNames(), row.getRequestedRegionCodes(), row.getRequestedRegionNames(),
                    row.getReason(), row.getStatus(), row.getReviewerName(), row.getReviewRemark(), row.getRequestedAt(), row.getReviewedAt());
        }
    }

    public record ReviewRequest(String remark) {}

    public record QrUploadResponse(String bucketName, String objectKey, String publicUrl, Long taskId) {}

    public record PublicConsultantResponse(Long userId, String name, String regionCode, String regionName,
                                           String publicTitle, String publicBio, String qrUrl) {
        public static PublicConsultantResponse of(OperatorProfile p, String regionCode, String regionName) {
            return new PublicConsultantResponse(p.getUserId(), p.getName(), regionCode, regionName,
                    p.getPublicTitle(), p.getPublicBio(), p.getConsultantQrPublicUrl());
        }
    }
}

package com.treeeducation.ioas.consultant;

import java.util.List;

public final class ConsultantAdminDtos {
    private ConsultantAdminDtos() {}

    public record CreateRequest(
            String username,
            String password,
            String displayName,
            String phone,
            String email,
            String teamName,
            String publicTitle,
            String publicBio,
            List<String> regionCodes,
            Boolean enabled,
            Boolean assignEnabled,
            Boolean displayOnOfficial,
            Integer maxDailyLeads,
            Integer sortOrder
    ) {}

    public record UpdateRequest(
            String consultantName,
            String phone,
            String email,
            String teamName,
            String publicTitle,
            String publicBio,
            List<String> regionCodes,
            Boolean enabled,
            Boolean assignEnabled,
            Boolean displayOnOfficial,
            Integer maxDailyLeads,
            Integer sortOrder
    ) {}

    public record Response(
            Long id,
            Long userId,
            String username,
            String consultantName,
            String phone,
            String email,
            String teamName,
            String avatarUrl,
            String publicTitle,
            String publicBio,
            List<RegionView> regions,
            Boolean enabled,
            Boolean assignEnabled,
            Boolean displayOnOfficial,
            Integer maxDailyLeads,
            Integer currentDailyLeads,
            Integer sortOrder
    ) {}

    public record RegionView(Long id, String regionCode, String regionName, Integer priority) {}
    public record AvatarResponse(Long consultantId, String avatarUrl) {}
}

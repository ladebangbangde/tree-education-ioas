package com.treeeducation.ioas.consultant;

import java.util.List;

public final class ConsultantAdminDtos {
    private ConsultantAdminDtos() {}

    public record CreateRequest(
            String displayName,
            List<String> regionCodes
    ) {}

    public record Response(
            Long id,
            Long userId,
            String username,
            String setupCode,
            String consultantName,
            String avatarUrl,
            String qrUrl,
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
}

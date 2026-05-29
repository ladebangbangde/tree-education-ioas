package com.treeeducation.ioas.system.advisor;

public final class AdvisorDtos {
    private AdvisorDtos() {}

    public record Response(
            Long userId,
            String name,
            String gender,
            String regionCode,
            String regionName,
            String publicTitle,
            String publicBio,
            String avatarUrl,
            Integer priority
    ) {}

    public record AvatarResponse(Long userId, String avatarUrl) {}
}

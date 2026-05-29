package com.treeeducation.ioas.system.user;

import java.time.Instant;

public final class UserAdminDtos {
    private UserAdminDtos() {}

    public record Response(
            Long id,
            String username,
            String displayName,
            String department,
            String roleCode,
            String status,
            Instant createdAt
    ) {}
}

package com.treeeducation.ioas.system.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class UserAdminDtos {
    private UserAdminDtos() {}

    public record UserResponse(Long id, String username, String displayName, String department, String roleCode, String roleName, UserStatus status, Integer tokenVersion, Instant createdAt) {}

    public record CreateUserRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 80) String displayName, @NotBlank @Size(max = 80) String department, @NotBlank @Size(max = 40) String roleCode, String initialCode, UserStatus status) {}

    public record UpdateUserRequest(@NotBlank @Size(max = 80) String displayName, @NotBlank @Size(max = 80) String department, @NotBlank @Size(max = 40) String roleCode, UserStatus status) {}

    public record UpdateStatusRequest(UserStatus status) {}

    public record ResetCodeRequest(String initialCode) {}

    public record InitialCodeResponse(Long userId, String username, String initialCode) {}

    public record OptionItem(String value, String label) {}

    public record UserOptions(List<OptionItem> roles, List<OptionItem> departments, List<OptionItem> statuses) {}
}

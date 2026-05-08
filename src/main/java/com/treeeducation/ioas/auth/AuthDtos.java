package com.treeeducation.ioas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Authentication request/response DTOs. */
public final class AuthDtos {
    private AuthDtos() {}
    @Schema(description = "登录请求") public record LoginRequest(@NotBlank @Schema(description = "用户名", example = "admin") String username, @NotBlank @Schema(description = "密码", example = "Admin@123456") String password) {}
    @Schema(description = "登录响应") public record LoginResponse(@Schema(description = "JWT access token") String accessToken, @Schema(description = "token 类型") String tokenType, @Schema(description = "当前用户") CurrentUserResponse user) {}
    @Schema(description = "当前用户") public record CurrentUserResponse(Long id, String username, String displayName, String roleCode) {}
}

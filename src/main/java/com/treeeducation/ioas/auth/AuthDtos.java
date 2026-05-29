package com.treeeducation.ioas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Authentication request/response DTOs aligned with frontend user model. */
public final class AuthDtos {
    private AuthDtos() {}

    @Schema(description = "登录请求")
    public record LoginRequest(
            @NotBlank @Schema(description = "用户名", example = "media") String username,
            @NotBlank @Schema(description = "口令", example = "Media@123456") String password) {}

    @Schema(description = "登录响应")
    public record LoginResponse(String accessToken, String tokenType, LoginUserResponse user) {}

    @Schema(description = "登录用户摘要")
    public record LoginUserResponse(Long id, String username, String userName, String role, String department) {}

    @Schema(description = "权限开关")
    public record PermissionResponse(boolean canUpload, boolean canDownload, boolean canDeleteFile,
                                     boolean canDeletePackage, boolean canGenerateLead) {}

    @Schema(description = "当前用户")
    public record CurrentUserResponse(Long id, String username, String userName, String role,
                                      String department, PermissionResponse permissions) {}

    @Schema(description = "当前用户更新登录凭证")
    public record ChangeSetupCodeRequest(
            @NotBlank String currentCode,
            @NotBlank String newCode
    ) {}

    @Schema(description = "超管更新用户登录凭证")
    public record AdminResetSetupCodeRequest(String setupCode) {}

    @Schema(description = "一次性登录凭证返回")
    public record SetupCodeResponse(Long userId, String username, String setupCode) {}
}

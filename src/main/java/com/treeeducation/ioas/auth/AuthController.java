package com.treeeducation.ioas.auth;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.system.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** Login and current-user APIs. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "登录、JWT 与当前用户")
public class AuthController {
    private final UserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) { this.users = users; this.encoder = encoder; this.jwt = jwt; }
    @PostMapping("/login") @Operation(summary = "用户名密码登录")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        var user = users.findByUsername(request.username()).orElseThrow(() -> BusinessException.badRequest("用户名或密码错误"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) throw BusinessException.badRequest("用户名或密码错误");
        var current = new AuthDtos.CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleCode());
        return ApiResponse.ok(new AuthDtos.LoginResponse(jwt.issue(user), "Bearer", current));
    }
    @GetMapping("/me") @Operation(summary = "获取当前登录用户")
    public ApiResponse<AuthDtos.CurrentUserResponse> me(@AuthenticationPrincipal UserPrincipal p) { return ApiResponse.ok(new AuthDtos.CurrentUserResponse(p.id(), p.username(), p.displayName(), p.roleCode())); }
}

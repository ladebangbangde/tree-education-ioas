package com.treeeducation.ioas.auth;

import com.treeeducation.ioas.audit.AuditAction;
import com.treeeducation.ioas.audit.AuditLog;
import com.treeeducation.ioas.audit.AuditLogRepository;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.system.user.User;
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
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditLogRepository audits;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditLogRepository audits) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audits = audits;
    }

    @PostMapping("/login")
    @Operation(summary = "用户名密码登录")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        User user = users.findByUsername(request.username()).orElseThrow(() -> BusinessException.badRequest("用户名或密码错误"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        auditLogin(user);
        return ApiResponse.ok(new AuthDtos.LoginResponse(jwt.issue(user), "Bearer", toLoginUser(user)));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户")
    public ApiResponse<AuthDtos.CurrentUserResponse> me(@AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(new AuthDtos.CurrentUserResponse(p.id(), p.username(), p.userName(), p.role(), p.department(), permissions(p.role())));
    }

    private void auditLogin(User user) {
        AuditLog log = new AuditLog();
        log.setAction(AuditAction.login);
        log.setTargetType("sys_user");
        log.setTargetId(user.getId());
        log.setActorId(user.getId());
        log.setDetail(user.getUsername());
        audits.save(log);
    }

    private AuthDtos.LoginUserResponse toLoginUser(User user) {
        return new AuthDtos.LoginUserResponse(user.getId(), user.getUsername(), user.getUserName(), user.getRoleCode(), user.getDepartment());
    }

    private AuthDtos.PermissionResponse permissions(String role) {
        boolean admin = "SUPER_ADMIN".equals(role);
        boolean media = "MEDIA".equals(role);
        boolean operator = "OPERATOR".equals(role);
        return new AuthDtos.PermissionResponse(admin || media, admin || media || operator,
                admin || media, admin || media, admin || operator);
    }
}

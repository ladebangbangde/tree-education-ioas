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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;

/** Login and current-user APIs. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "登录、JWT 与当前用户")
public class AuthController {
    private static final SecureRandom RANDOM = new SecureRandom();
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
    @Transactional
    @Operation(summary = "用户名密码登录")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        User user = users.findByUsername(request.username()).orElseThrow(() -> BusinessException.badRequest("用户名或密码错误"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        user.setTokenVersion(user.getTokenVersion() + 1);
        user = users.save(user);
        auditLogin(user);
        return ApiResponse.ok(new AuthDtos.LoginResponse(jwt.issue(user), "Bearer", toLoginUser(user)));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户")
    public ApiResponse<AuthDtos.CurrentUserResponse> me(@AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(new AuthDtos.CurrentUserResponse(p.id(), p.username(), p.userName(), p.role(), p.department(), permissions(p.role())));
    }

    @PostMapping("/setup-code/change")
    @Transactional
    @Operation(summary = "当前用户更新自己的登录凭证")
    public ApiResponse<Void> changeSetupCode(@AuthenticationPrincipal UserPrincipal p,
                                             @Valid @RequestBody AuthDtos.ChangeSetupCodeRequest request) {
        if (p == null || p.id() == null) throw BusinessException.forbidden("请先登录");
        User user = users.findById(p.id()).orElseThrow(() -> BusinessException.forbidden("登录用户不存在"));
        if (!encoder.matches(request.currentCode(), user.getPasswordHash())) {
            throw BusinessException.badRequest("当前登录凭证不正确");
        }
        validateSetupCode(request.newCode());
        user.setPasswordHash(encoder.encode(request.newCode()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);
        return ApiResponse.ok();
    }

    @PostMapping("/admin/users/{userId}/setup-code/reset")
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "超管重置指定用户登录凭证")
    public ApiResponse<AuthDtos.SetupCodeResponse> adminResetSetupCode(@PathVariable Long userId,
                                                                       @RequestBody(required = false) AuthDtos.AdminResetSetupCodeRequest request) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("用户不存在"));
        String setupCode = request == null || request.setupCode() == null || request.setupCode().isBlank()
                ? nextSetupCode()
                : request.setupCode().trim();
        validateSetupCode(setupCode);
        user.setPasswordHash(encoder.encode(setupCode));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user = users.save(user);
        return ApiResponse.ok(new AuthDtos.SetupCodeResponse(user.getId(), user.getUsername(), setupCode));
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

    private String nextSetupCode() {
        int number = 100000 + RANDOM.nextInt(900000);
        return "Tree@" + number;
    }

    private void validateSetupCode(String value) {
        if (value == null || value.trim().length() < 8) {
            throw BusinessException.badRequest("登录凭证长度至少 8 位");
        }
    }
}

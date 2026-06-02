package com.treeeducation.ioas.system.user;

import com.treeeducation.ioas.common.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class UserAdminService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<UserAdminDtos.OptionItem> ROLE_OPTIONS = List.of(
            new UserAdminDtos.OptionItem("SUPER_ADMIN", "System Admin"),
            new UserAdminDtos.OptionItem("CONSULTANT", "Consultant"),
            new UserAdminDtos.OptionItem("MEDIA", "Media"),
            new UserAdminDtos.OptionItem("OPERATOR", "Operator"),
            new UserAdminDtos.OptionItem("DATA", "Data Operator"),
            new UserAdminDtos.OptionItem("ADMINISTRATIVE", "Administrative")
    );
    private static final List<UserAdminDtos.OptionItem> DEPARTMENT_OPTIONS = List.of(
            new UserAdminDtos.OptionItem("SYSTEM", "System"),
            new UserAdminDtos.OptionItem("CONSULTING", "Consulting"),
            new UserAdminDtos.OptionItem("DELIVERY", "Delivery"),
            new UserAdminDtos.OptionItem("MEDIA", "Media"),
            new UserAdminDtos.OptionItem("OPERATION", "Operation"),
            new UserAdminDtos.OptionItem("DATA", "Data"),
            new UserAdminDtos.OptionItem("ADMIN", "Admin")
    );

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserAdminService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UserAdminDtos.UserResponse> list(String keyword, String department, String roleCode, UserStatus status) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String dept = department == null ? "" : department.trim();
        String role = normalizeRole(roleCode, false);
        return users.findAll().stream()
                .filter(user -> kw.isBlank() || safe(user.getUsername()).toLowerCase(Locale.ROOT).contains(kw) || safe(user.getDisplayName()).toLowerCase(Locale.ROOT).contains(kw))
                .filter(user -> dept.isBlank() || dept.equals(user.getDepartment()))
                .filter(user -> role.isBlank() || role.equals(user.getRoleCode()))
                .filter(user -> status == null || status == user.getStatus())
                .sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAdminDtos.UserOptions options() {
        return new UserAdminDtos.UserOptions(ROLE_OPTIONS, DEPARTMENT_OPTIONS, List.of(new UserAdminDtos.OptionItem("ACTIVE", "Active"), new UserAdminDtos.OptionItem("DISABLED", "Disabled")));
    }

    @Transactional
    public UserAdminDtos.InitialCodeResponse create(UserAdminDtos.CreateUserRequest request) {
        String username = trimRequired(request.username(), "username required");
        if (users.findByUsername(username).isPresent()) throw BusinessException.badRequest("username exists");
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(trimRequired(request.displayName(), "displayName required"));
        user.setDepartment(validateDepartment(request.department()));
        user.setRoleCode(validateRole(request.roleCode()));
        user.setStatus(request.status() == null ? UserStatus.ACTIVE : request.status());
        String code = normalizeInitialCode(request.initialCode());
        user.setPasswordHash(encoder.encode(code));
        user = users.save(user);
        return new UserAdminDtos.InitialCodeResponse(user.getId(), user.getUsername(), code);
    }

    @Transactional
    public UserAdminDtos.UserResponse update(Long id, UserAdminDtos.UpdateUserRequest request) {
        User user = users.findById(id).orElseThrow(() -> BusinessException.notFound("user not found"));
        user.setDisplayName(trimRequired(request.displayName(), "displayName required"));
        user.setDepartment(validateDepartment(request.department()));
        user.setRoleCode(validateRole(request.roleCode()));
        user.setStatus(request.status() == null ? UserStatus.ACTIVE : request.status());
        user.setTokenVersion(user.getTokenVersion() + 1);
        return toResponse(users.save(user));
    }

    @Transactional
    public UserAdminDtos.UserResponse updateStatus(Long id, UserStatus status) {
        if (status == null) throw BusinessException.badRequest("status required");
        User user = users.findById(id).orElseThrow(() -> BusinessException.notFound("user not found"));
        user.setStatus(status);
        user.setTokenVersion(user.getTokenVersion() + 1);
        return toResponse(users.save(user));
    }

    @Transactional
    public UserAdminDtos.InitialCodeResponse resetCode(Long id, String initialCode) {
        User user = users.findById(id).orElseThrow(() -> BusinessException.notFound("user not found"));
        String code = normalizeInitialCode(initialCode);
        user.setPasswordHash(encoder.encode(code));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user = users.save(user);
        return new UserAdminDtos.InitialCodeResponse(user.getId(), user.getUsername(), code);
    }

    private UserAdminDtos.UserResponse toResponse(User user) {
        return new UserAdminDtos.UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getDepartment(), user.getRoleCode(), roleName(user.getRoleCode()), user.getStatus(), user.getTokenVersion(), user.getCreatedAt());
    }

    private String validateRole(String value) {
        String role = normalizeRole(value, true);
        boolean exists = ROLE_OPTIONS.stream().anyMatch(item -> item.value().equals(role));
        if (!exists) throw BusinessException.badRequest("role not found");
        return role;
    }

    private String normalizeRole(String value, boolean required) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (role.startsWith("ROLE_")) role = role.substring("ROLE_".length());
        if (required && role.isBlank()) throw BusinessException.badRequest("role required");
        return role;
    }

    private String validateDepartment(String value) {
        String dept = trimRequired(value, "department required");
        boolean exists = DEPARTMENT_OPTIONS.stream().anyMatch(item -> item.value().equals(dept));
        if (!exists) throw BusinessException.badRequest("department not found");
        return dept;
    }

    private String roleName(String roleCode) {
        return ROLE_OPTIONS.stream().filter(item -> item.value().equals(roleCode)).map(UserAdminDtos.OptionItem::label).findFirst().orElse(roleCode);
    }

    private String normalizeInitialCode(String value) {
        String code = value == null ? "" : value.trim();
        if (code.isBlank()) code = "Tree@" + (100000 + RANDOM.nextInt(900000));
        if (code.length() < 6) throw BusinessException.badRequest("initial code too short");
        return code;
    }

    private String trimRequired(String value, String message) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank()) throw BusinessException.badRequest(message);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

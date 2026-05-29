package com.treeeducation.ioas.system.user;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserAdminController {
    private final UserRepository users;

    public UserAdminController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public ApiResponse<List<UserAdminDtos.Response>> list() {
        return ApiResponse.ok(users.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(user -> new UserAdminDtos.Response(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getDepartment(),
                        user.getRoleCode(),
                        user.getStatus() == null ? null : user.getStatus().name(),
                        user.getCreatedAt()
                ))
                .toList());
    }
}

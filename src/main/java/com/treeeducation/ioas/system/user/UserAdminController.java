package com.treeeducation.ioas.system.user;

import com.treeeducation.ioas.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserAdminController {
    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<UserAdminDtos.UserResponse>> list(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String department,
                                                               @RequestParam(required = false) String roleCode,
                                                               @RequestParam(required = false) UserStatus status) {
        return ApiResponse.ok(service.list(keyword, department, roleCode, status));
    }

    @GetMapping("/options")
    public ApiResponse<UserAdminDtos.UserOptions> options() {
        return ApiResponse.ok(service.options());
    }

    @PostMapping
    public ApiResponse<UserAdminDtos.InitialCodeResponse> create(@Valid @RequestBody UserAdminDtos.CreateUserRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAdminDtos.UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserAdminDtos.UpdateUserRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserAdminDtos.UserResponse> updateStatus(@PathVariable Long id, @RequestBody UserAdminDtos.UpdateStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request == null ? null : request.status()));
    }

    @PostMapping("/{id}/initial-code/reset")
    public ApiResponse<UserAdminDtos.InitialCodeResponse> resetCode(@PathVariable Long id, @RequestBody(required = false) UserAdminDtos.ResetCodeRequest request) {
        return ApiResponse.ok(service.resetCode(id, request == null ? null : request.initialCode()));
    }
}

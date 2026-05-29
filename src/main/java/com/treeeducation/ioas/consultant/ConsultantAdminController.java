package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/consultants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ConsultantAdminController {
    private final ConsultantAdminService service;

    public ConsultantAdminController(ConsultantAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ConsultantAdminDtos.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<ConsultantAdminDtos.Response> create(@RequestBody ConsultantAdminDtos.CreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

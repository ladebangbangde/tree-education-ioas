package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ConsultantPublicController {
    private final ConsultantAdminService service;

    public ConsultantPublicController(ConsultantAdminService service) {
        this.service = service;
    }

    @GetMapping({"/api/v1/public/consultants", "/api/public/consultants"})
    public ApiResponse<List<ConsultantAdminDtos.Response>> list() {
        return ApiResponse.ok(service.publicList());
    }

    @GetMapping("/api/public/consultant-regions")
    public ApiResponse<List<ConsultantAdminDtos.RegionView>> consultantRegions() {
        return ApiResponse.ok(service.publicRegionOptions());
    }
}

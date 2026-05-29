package com.treeeducation.ioas.system.advisor;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AdvisorPublicController {
    private final AdvisorProfileService service;

    public AdvisorPublicController(AdvisorProfileService service) {
        this.service = service;
    }

    @GetMapping({"/api/v1/public/consultants", "/api/public/consultants"})
    public ApiResponse<List<AdvisorDtos.Response>> list() {
        return ApiResponse.ok(service.list());
    }
}

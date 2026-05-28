package com.treeeducation.ioas.customertracking;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/customer-progress")
public class CustomerProgressPublicController {
    private final CustomerTrackingService service;

    public CustomerProgressPublicController(CustomerTrackingService service) {
        this.service = service;
    }

    @GetMapping("/{customerId}")
    public ApiResponse<CustomerTrackingDtos.Detail> detail(@PathVariable Long customerId) {
        return ApiResponse.ok(service.detail(customerId));
    }
}

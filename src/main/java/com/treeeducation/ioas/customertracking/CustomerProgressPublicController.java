package com.treeeducation.ioas.customertracking;

import com.treeeducation.ioas.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/customer-progress")
public class CustomerProgressPublicController {
    private final CustomerTrackingService service;

    @GetMapping("/{customerId}")
    public ApiResponse<CustomerTrackingDtos.Detail> detail(@PathVariable Long customerId) {
        return ApiResponse.ok(service.detail(customerId));
    }
}

package com.treeeducation.ioas.customertracking;

import com.treeeducation.ioas.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer-trackings")
public class CustomerTrackingController {
    private final CustomerTrackingService service;

    @GetMapping
    public ApiResponse<List<CustomerTrackingDtos.Summary>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{customerId}")
    public ApiResponse<CustomerTrackingDtos.Detail> detail(@PathVariable Long customerId) {
        return ApiResponse.ok(service.detail(customerId));
    }
}

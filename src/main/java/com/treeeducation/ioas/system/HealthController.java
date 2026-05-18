package com.treeeducation.ioas.system;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Public runtime endpoints used by Docker, FRP and simple browser checks. */
@RestController
public class HealthController {

    @GetMapping("/")
    public ApiResponse<Map<String, Object>> root() {
        return ApiResponse.ok(Map.of(
                "service", "tree-education-ioas",
                "status", "running",
                "swagger", "/swagger-ui.html",
                "time", Instant.now().toString()
        ));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "tree-education-ioas",
                "time", Instant.now().toString()
        ));
    }
}

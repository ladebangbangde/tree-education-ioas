package com.treeeducation.ioas.system.region;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/intention-regions")
@Tag(name = "Public Intention Region", description = "Official website intention region options")
public class IntentionRegionController {
    private final IntentionRegionRepository repository;

    public IntentionRegionController(IntentionRegionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "List enabled intention regions")
    public ApiResponse<List<Response>> list() {
        return ApiResponse.ok(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(region -> new Response(region.getId(), region.getCode(), region.getName()))
                .toList());
    }

    public record Response(Long id, String code, String name) {}
}

package com.treeeducation.ioas.system.region;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/consultant-regions")
@Tag(name = "Public Consultant Region", description = "Regions currently handled by active consultants")
public class PublicConsultantRegionController {
    private final ConsultantRegionAssignmentRepository repository;

    public PublicConsultantRegionController(ConsultantRegionAssignmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "List regions handled by active consultants")
    public ApiResponse<List<Response>> list() {
        LinkedHashMap<String, Response> grouped = new LinkedHashMap<>();
        repository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .sorted(Comparator.comparing(ConsultantRegionAssignment::getPriority).thenComparing(ConsultantRegionAssignment::getId))
                .forEach(row -> grouped.putIfAbsent(row.getRegionCode(), new Response(row.getRegionId(), row.getRegionCode(), row.getRegionName(), row.getPriority())));
        return ApiResponse.ok(grouped.values().stream().toList());
    }

    public record Response(Long id, String code, String name, Integer priority) {}
}

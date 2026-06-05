package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/platform-topics")
public class DataOperationMetricController {
    private final DataOperationMetricService metricService;

    public DataOperationMetricController(DataOperationMetricService metricService) {
        this.metricService = metricService;
    }

    @GetMapping("/{topicId}/metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','CONSULTANT')")
    public ApiResponse<Map<String, Object>> listMetrics(@PathVariable Long topicId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("status", metricService.computeTopicStatus(topicId));
        result.put("rows", metricService.listTopicMetrics(topicId));
        return ApiResponse.ok(result);
    }

    @GetMapping("/{topicId}/recognition-status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','CONSULTANT')")
    public ApiResponse<Map<String, Object>> recognitionStatus(@PathVariable Long topicId) {
        return ApiResponse.ok(metricService.computeTopicStatus(topicId));
    }
}

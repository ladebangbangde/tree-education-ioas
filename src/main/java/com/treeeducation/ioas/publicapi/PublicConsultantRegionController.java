package com.treeeducation.ioas.publicapi;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.system.region.ConsultantRegionAssignment;
import com.treeeducation.ioas.system.region.ConsultantRegionAssignmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PublicConsultantRegionController {
    private final ConsultantRegionAssignmentRepository assignments;

    public PublicConsultantRegionController(ConsultantRegionAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @GetMapping({"/api/v1/public/consultant-regions", "/api/public/consultant-regions"})
    public ApiResponse<List<RegionOption>> list() {
        Map<String, RegionOption> grouped = new LinkedHashMap<>();
        for (ConsultantRegionAssignment assignment : assignments.findByEnabledTrueOrderByPriorityAscIdAsc()) {
            if (assignment.getRegionCode() == null || assignment.getRegionName() == null) continue;
            String code = normalizeCode(assignment.getRegionCode());
            if ("OTHER".equals(code)) continue;
            RegionOption current = grouped.get(code);
            if (current == null) {
                grouped.put(code, new RegionOption(code, normalizeName(code, assignment.getRegionName()), 1));
            } else {
                grouped.put(code, new RegionOption(current.code(), current.name(), current.consultantCount() + 1));
            }
        }
        List<RegionOption> options = new ArrayList<>(grouped.values());
        options.add(new RegionOption("OTHER", "其他区域", 0));
        return ApiResponse.ok(options);
    }

    private String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase();
        return switch (code) {
            case "AUSTRALIA", "澳洲", "澳大利亚" -> "AU";
            case "USA", "US", "美国" -> "US";
            case "UK", "英国" -> "UK";
            case "EUROPE", "EU", "欧洲" -> "EU";
            case "CANADA", "CA", "加拿大" -> "CA";
            case "SINGAPORE", "SG", "新加坡" -> "SG";
            case "JAPAN", "JP", "日本" -> "JP";
            case "HONGKONG", "HONG_KONG", "HK", "中国香港", "香港" -> "HK";
            default -> code.isBlank() ? "OTHER" : code;
        };
    }

    private String normalizeName(String code, String fallback) {
        return switch (code) {
            case "AU" -> "澳洲";
            case "US" -> "美国";
            case "UK" -> "英国";
            case "EU" -> "欧洲";
            case "CA" -> "加拿大";
            case "SG" -> "新加坡";
            case "JP" -> "日本";
            case "HK" -> "中国香港";
            default -> fallback;
        };
    }

    public record RegionOption(String code, String name, Integer consultantCount) {}
}

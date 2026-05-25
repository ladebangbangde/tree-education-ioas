package com.treeeducation.ioas.system.region;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfile;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.system.user.UserRepository;
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
    private final OperatorProfileRepository profiles;
    private final UserRepository users;

    public PublicConsultantRegionController(ConsultantRegionAssignmentRepository repository,
                                            OperatorProfileRepository profiles,
                                            UserRepository users) {
        this.repository = repository;
        this.profiles = profiles;
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List regions handled by active consultants")
    public ApiResponse<List<Response>> list() {
        LinkedHashMap<String, Response> grouped = new LinkedHashMap<>();
        repository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .filter(this::isActiveAssignment)
                .sorted(Comparator.comparing(ConsultantRegionAssignment::getPriority).thenComparing(ConsultantRegionAssignment::getId))
                .forEach(row -> grouped.putIfAbsent(row.getRegionCode(), new Response(row.getRegionId(), row.getRegionCode(), row.getRegionName(), row.getPriority())));
        return ApiResponse.ok(grouped.values().stream().toList());
    }

    private boolean isActiveAssignment(ConsultantRegionAssignment assignment) {
        if (assignment == null || assignment.getConsultantProfileId() == null || assignment.getConsultantUserId() == null || !Boolean.TRUE.equals(assignment.getEnabled())) {
            return false;
        }
        return profiles.findById(assignment.getConsultantProfileId())
                .filter(profile -> Boolean.TRUE.equals(profile.getEnabled()))
                .filter(profile -> assignment.getConsultantUserId().equals(profile.getUserId()))
                .map(OperatorProfile::getUserId)
                .flatMap(users::findById)
                .filter(user -> "CONSULTANT".equalsIgnoreCase(user.getRoleCode()))
                .filter(user -> user.getStatus() != null && "ACTIVE".equalsIgnoreCase(user.getStatus().name()))
                .isPresent();
    }

    public record Response(Long id, String code, String name, Integer priority) {}
}

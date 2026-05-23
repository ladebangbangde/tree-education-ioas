package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ConsultantAssignmentService {
    private final ConsultantRegionRepository regions;
    private final ConsultantAssignmentRepository assignments;
    private final ConsultantProfileRepository consultants;

    public ConsultantAssignmentService(ConsultantRegionRepository regions,
                                       ConsultantAssignmentRepository assignments,
                                       ConsultantProfileRepository consultants) {
        this.regions = regions;
        this.assignments = assignments;
        this.consultants = consultants;
    }

    @Transactional
    public AssignmentResult assign(String regionCode) {
        ConsultantRegion region = regions.activeByCode(regionCode)
                .orElseThrow(() -> BusinessException.badRequest("意向区域不存在或未启用"));

        List<ConsultantProfile> matched = assignments.candidatesByRegion(region.getId());
        if (!matched.isEmpty()) {
            ConsultantProfile consultant = markAssigned(matched.get(0));
            return new AssignmentResult(region, consultant, "REGION_MATCH", "命中顾问负责区域：" + region.getRegionName());
        }

        List<ConsultantProfile> fallback = assignments.roundRobinCandidates();
        if (fallback.isEmpty()) {
            return new AssignmentResult(region, null, "UNASSIGNED", "暂无可接收线索的启用顾问");
        }

        ConsultantProfile consultant = markAssigned(fallback.get(0));
        return new AssignmentResult(region, consultant, "ROUND_ROBIN", "意向区域暂无负责顾问，进入顾问池轮询分配");
    }

    private ConsultantProfile markAssigned(ConsultantProfile consultant) {
        consultant.setCurrentDailyLeads((consultant.getCurrentDailyLeads() == null ? 0 : consultant.getCurrentDailyLeads()) + 1);
        consultant.setLastAssignedAt(Instant.now());
        consultant.setUpdatedAt(Instant.now());
        return consultants.save(consultant);
    }

    public record AssignmentResult(ConsultantRegion region, ConsultantProfile consultant, String assignMode, String assignReason) {}
}

package com.treeeducation.ioas.consultation;

import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.consultant.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service("consultationModuleAssignmentService")
public class ConsultantAssignmentService {
    private final ConsultantRegionRepository regions;
    private final ConsultantScopeRepository scopes;
    private final ConsultantProfileRepository consultants;

    public ConsultantAssignmentService(ConsultantRegionRepository regions, ConsultantScopeRepository scopes,
                                       ConsultantProfileRepository consultants) {
        this.regions = regions;
        this.scopes = scopes;
        this.consultants = consultants;
    }

    public List<ConsultantRegion> publicOptions() {
        return regions.publicCoveredOptions();
    }

    @Transactional
    public AssignmentResult assign(String regionCode) {
        ConsultantRegion region = regions.activeByCode(regionCode)
                .orElseThrow(() -> BusinessException.badRequest("意向区域不存在或已停用"));
        List<ConsultantProfile> matched = scopes.candidatesByRegion(region.getId());
        String mode = "REGION_MATCH";
        String reason = "命中顾问负责区域：" + region.getRegionName();
        ConsultantProfile picked;
        if (matched.isEmpty()) {
            List<ConsultantProfile> fallback = scopes.roundRobinCandidates();
            if (fallback.isEmpty()) {
                throw BusinessException.badRequest("暂无可分配顾问");
            }
            picked = fallback.get(0);
            mode = "ROUND_ROBIN";
            reason = "意向区域暂无可用负责顾问，进入顾问池轮询分配";
        } else {
            picked = matched.get(0);
        }
        picked.setCurrentDailyLeads((picked.getCurrentDailyLeads() == null ? 0 : picked.getCurrentDailyLeads()) + 1);
        picked.setLastAssignedAt(Instant.now());
        picked.setUpdatedAt(Instant.now());
        consultants.save(picked);
        return new AssignmentResult(region, picked, mode, reason);
    }

    public record AssignmentResult(ConsultantRegion region, ConsultantProfile consultant, String mode, String reason) {}
}
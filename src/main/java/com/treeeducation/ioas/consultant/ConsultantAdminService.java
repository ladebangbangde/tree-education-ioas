package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfile;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.system.region.ConsultantRegionAssignment;
import com.treeeducation.ioas.system.region.ConsultantRegionAssignmentRepository;
import com.treeeducation.ioas.system.user.User;
import com.treeeducation.ioas.system.user.UserRepository;
import com.treeeducation.ioas.system.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConsultantAdminService {
    private final UserRepository users;
    private final OperatorProfileRepository operatorProfiles;
    private final ConsultantProfileRepository consultants;
    private final ConsultantRegionRepository regions;
    private final ConsultantScopeRepository scopes;
    private final ConsultantRegionAssignmentRepository legacyAssignments;
    private final PasswordEncoder passwordEncoder;

    public ConsultantAdminService(UserRepository users,
                                  OperatorProfileRepository operatorProfiles,
                                  ConsultantProfileRepository consultants,
                                  ConsultantRegionRepository regions,
                                  ConsultantScopeRepository scopes,
                                  ConsultantRegionAssignmentRepository legacyAssignments,
                                  PasswordEncoder passwordEncoder) {
        this.users = users;
        this.operatorProfiles = operatorProfiles;
        this.consultants = consultants;
        this.regions = regions;
        this.scopes = scopes;
        this.legacyAssignments = legacyAssignments;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ConsultantAdminDtos.Response> list() {
        Map<Long, User> userMap = users.findAll().stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, OperatorProfile> operatorMap = operatorProfiles.findAll().stream().collect(Collectors.toMap(OperatorProfile::getUserId, p -> p, (a, b) -> a));
        return consultants.managementList().stream().map(c -> buildResponse(c, userMap.get(c.getUserId()), operatorMap.get(c.getUserId()))).toList();
    }

    public List<ConsultantAdminDtos.Response> publicList() {
        Map<Long, User> userMap = users.findAll().stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, OperatorProfile> operatorMap = operatorProfiles.findAll().stream().collect(Collectors.toMap(OperatorProfile::getUserId, p -> p, (a, b) -> a));
        return consultants.publicList().stream().map(c -> buildResponse(c, userMap.get(c.getUserId()), operatorMap.get(c.getUserId()))).toList();
    }

    @Transactional
    public ConsultantAdminDtos.Response create(ConsultantAdminDtos.CreateRequest req) {
        String displayName = required(req.displayName(), "顾问姓名不能为空").trim();
        if (req.regionCodes() == null || req.regionCodes().isEmpty()) throw BusinessException.badRequest("请至少选择一个负责区域");

        String username = nextUsername(displayName);
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setDepartment("顾问团队");
        user.setRoleCode("CONSULTANT");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setTokenVersion(0);
        user = users.save(user);

        ConsultantProfile profile = new ConsultantProfile();
        profile.setUserId(user.getId());
        profile.setConsultantName(displayName);
        profile.setTeamName("顾问团队");
        profile.setPublicTitle(null);
        profile.setPublicBio(null);
        profile.setAvatarUrl(null);
        profile.setDisplayOnOfficial(true);
        profile.setEnabled(true);
        profile.setAssignEnabled(true);
        profile.setMaxDailyLeads(30);
        profile.setCurrentDailyLeads(0);
        profile.setSortOrder(0);
        profile.setUpdatedAt(Instant.now());
        profile = consultants.save(profile);

        OperatorProfile operatorProfile = new OperatorProfile();
        operatorProfile.setUserId(user.getId());
        operatorProfile.setName(displayName);
        operatorProfile.setTeamName("顾问团队");
        operatorProfile.setEnabled(true);
        operatorProfile.setSpecialityRegionCodes(String.join(",", cleanCodes(req.regionCodes())));
        operatorProfile.setSpecialityRegionNames(cleanCodes(req.regionCodes()).stream().map(this::regionName).collect(Collectors.joining(",")));
        operatorProfiles.save(operatorProfile);

        replaceRegions(profile, req.regionCodes());
        return buildResponse(profile, user, operatorProfile);
    }

    @Transactional
    public void delete(Long consultantId) {
        ConsultantProfile profile = consultants.findById(consultantId).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        Long userId = profile.getUserId();

        List<ConsultantScope> oldScopes = scopes.findAll().stream()
                .filter(s -> Objects.equals(s.getConsultantId(), profile.getId()))
                .toList();
        scopes.deleteAll(oldScopes);

        legacyAssignments.deleteAll(legacyAssignments.findByConsultantUserIdOrderByPriorityAscIdAsc(userId));
        operatorProfiles.findByUserId(userId).ifPresent(operatorProfiles::delete);
        consultants.delete(profile);
        users.findById(userId).ifPresent(users::delete);
    }

    private void replaceRegions(ConsultantProfile profile, List<String> regionCodes) {
        List<String> codes = cleanCodes(regionCodes);
        if (codes.isEmpty()) throw BusinessException.badRequest("请至少选择一个负责区域");
        List<ConsultantScope> oldScopes = scopes.findAll().stream().filter(s -> Objects.equals(s.getConsultantId(), profile.getId())).toList();
        scopes.deleteAll(oldScopes);
        legacyAssignments.deleteAll(legacyAssignments.findByConsultantUserIdOrderByPriorityAscIdAsc(profile.getUserId()));

        int priority = 10;
        for (String code : codes) {
            ConsultantRegion region = regions.activeByCode(code).orElseGet(() -> createRegion(code));
            ConsultantScope scope = new ConsultantScope();
            scope.setConsultantId(profile.getId());
            scope.setRegionId(region.getId());
            scope.setEnabled(true);
            scope.setPriority(priority);
            scope.setUpdatedAt(Instant.now());
            scopes.save(scope);

            ConsultantRegionAssignment legacy = new ConsultantRegionAssignment();
            legacy.setConsultantProfileId(profile.getId());
            legacy.setConsultantUserId(profile.getUserId());
            legacy.setRegionId(region.getId());
            legacy.setRegionCode(region.getRegionCode());
            legacy.setRegionName(region.getRegionName());
            legacy.setPriority(priority);
            legacy.setEnabled(true);
            legacyAssignments.save(legacy);

            priority += 10;
        }
    }

    private ConsultantRegion createRegion(String code) {
        ConsultantRegion region = new ConsultantRegion();
        region.setRegionCode(code);
        region.setRegionName(regionName(code));
        region.setRegionType("REGION");
        region.setEnabled(true);
        region.setSortOrder(999);
        region.setRemark("超管注册顾问时自动创建");
        return regions.save(region);
    }

    private ConsultantAdminDtos.Response buildResponse(ConsultantProfile c, User user, OperatorProfile operatorProfile) {
        Map<Long, ConsultantRegion> regionMap = regions.findAll().stream().collect(Collectors.toMap(ConsultantRegion::getId, r -> r));
        List<ConsultantAdminDtos.RegionView> regionViews = scopes.findAll().stream()
                .filter(s -> Objects.equals(s.getConsultantId(), c.getId()) && Boolean.TRUE.equals(s.getEnabled()))
                .sorted(Comparator.comparing(ConsultantScope::getPriority))
                .map(s -> {
                    ConsultantRegion r = regionMap.get(s.getRegionId());
                    return new ConsultantAdminDtos.RegionView(s.getRegionId(), r == null ? null : r.getRegionCode(), r == null ? null : r.getRegionName(), s.getPriority());
                }).toList();

        String avatarUrl = operatorProfile != null && operatorProfile.getConsultantAvatarPublicUrl() != null ? operatorProfile.getConsultantAvatarPublicUrl() : c.getAvatarUrl();
        String publicTitle = operatorProfile != null && operatorProfile.getPublicTitle() != null ? operatorProfile.getPublicTitle() : c.getPublicTitle();
        String publicBio = operatorProfile != null && operatorProfile.getPublicBio() != null ? operatorProfile.getPublicBio() : c.getPublicBio();

        return new ConsultantAdminDtos.Response(
                c.getId(), c.getUserId(), user == null ? null : user.getUsername(), c.getConsultantName(), avatarUrl,
                publicTitle, publicBio, regionViews, c.getEnabled(), c.getAssignEnabled(), c.getDisplayOnOfficial(),
                c.getMaxDailyLeads(), c.getCurrentDailyLeads(), c.getSortOrder()
        );
    }

    private List<String> cleanCodes(List<String> regionCodes) {
        if (regionCodes == null) return List.of();
        return regionCodes.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }

    private String nextUsername(String displayName) {
        String base = displayName == null ? "consultant" : displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
        if (base.isBlank()) base = "consultant";
        String candidate = base;
        int seq = 1;
        while (users.findByUsername(candidate).isPresent()) {
            candidate = base + seq++;
        }
        return candidate;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest(message);
        return value;
    }

    private String regionName(String code) {
        return switch (code) {
            case "AU" -> "澳洲";
            case "US" -> "美国";
            case "UK" -> "英国";
            case "EU" -> "欧洲";
            case "CA" -> "加拿大";
            case "SG" -> "新加坡";
            case "JP" -> "日本";
            case "HK" -> "中国香港";
            default -> code;
        };
    }
}

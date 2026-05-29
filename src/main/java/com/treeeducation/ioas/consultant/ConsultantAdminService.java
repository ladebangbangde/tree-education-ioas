package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.system.user.User;
import com.treeeducation.ioas.system.user.UserRepository;
import com.treeeducation.ioas.system.user.UserStatus;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConsultantAdminService {
    private final UserRepository users;
    private final ConsultantProfileRepository consultants;
    private final ConsultantRegionRepository regions;
    private final ConsultantScopeRepository scopes;
    private final PasswordEncoder passwordEncoder;
    private final MinioClient minioClient;

    @Value("${ioas.storage.bucket}")
    private String bucketName;
    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    public ConsultantAdminService(UserRepository users,
                                  ConsultantProfileRepository consultants,
                                  ConsultantRegionRepository regions,
                                  ConsultantScopeRepository scopes,
                                  PasswordEncoder passwordEncoder,
                                  MinioClient minioClient) {
        this.users = users;
        this.consultants = consultants;
        this.regions = regions;
        this.scopes = scopes;
        this.passwordEncoder = passwordEncoder;
        this.minioClient = minioClient;
    }

    public List<ConsultantAdminDtos.Response> list() {
        Map<Long, User> userMap = users.findAll().stream().collect(Collectors.toMap(User::getId, u -> u));
        return consultants.managementList().stream().map(c -> toResponse(c, userMap.get(c.getUserId()))).toList();
    }

    public List<ConsultantAdminDtos.Response> publicList() {
        Map<Long, User> userMap = users.findAll().stream().collect(Collectors.toMap(User::getId, u -> u));
        return consultants.publicList().stream().map(c -> toResponse(c, userMap.get(c.getUserId()))).toList();
    }

    @Transactional
    public ConsultantAdminDtos.Response create(ConsultantAdminDtos.CreateRequest req) {
        String username = required(req.username(), "登录账号不能为空").trim();
        if (users.findByUsername(username).isPresent()) throw BusinessException.badRequest("登录账号已存在");
        String displayName = required(req.displayName(), "顾问姓名不能为空").trim();
        String password = required(req.password(), "初始密码不能为空");
        if (req.regionCodes() == null || req.regionCodes().isEmpty()) throw BusinessException.badRequest("请至少选择一个负责区域");

        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setDepartment(blankToDefault(req.teamName(), "顾问团队"));
        user.setRoleCode("CONSULTANT");
        user.setStatus(Boolean.FALSE.equals(req.enabled()) ? UserStatus.DISABLED : UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setTokenVersion(0);
        user = users.save(user);

        ConsultantProfile profile = new ConsultantProfile();
        profile.setUserId(user.getId());
        applyProfile(profile, displayName, req.phone(), req.email(), req.teamName(), req.publicTitle(), req.publicBio(), req.enabled(), req.assignEnabled(), req.displayOnOfficial(), req.maxDailyLeads(), req.sortOrder());
        profile = consultants.save(profile);
        replaceRegions(profile, req.regionCodes());
        return toResponse(profile, user);
    }

    @Transactional
    public ConsultantAdminDtos.Response update(Long consultantId, ConsultantAdminDtos.UpdateRequest req) {
        ConsultantProfile profile = consultants.findById(consultantId).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        User user = users.findById(profile.getUserId()).orElse(null);
        applyProfile(profile,
                blankToDefault(req.consultantName(), profile.getConsultantName()),
                req.phone(), req.email(), req.teamName(), req.publicTitle(), req.publicBio(),
                req.enabled(), req.assignEnabled(), req.displayOnOfficial(), req.maxDailyLeads(), req.sortOrder());
        profile.setUpdatedAt(Instant.now());
        profile = consultants.save(profile);
        if (req.regionCodes() != null) replaceRegions(profile, req.regionCodes());
        return toResponse(profile, user);
    }

    @Transactional
    public ConsultantAdminDtos.AvatarResponse uploadAvatar(Long consultantId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("请上传顾问头像文件");
        ConsultantProfile profile = consultants.findById(consultantId).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        String url = upload(file);
        profile.setAvatarUrl(url);
        profile.setUpdatedAt(Instant.now());
        consultants.save(profile);
        return new ConsultantAdminDtos.AvatarResponse(profile.getId(), url);
    }

    private void applyProfile(ConsultantProfile profile, String name, String phone, String email, String teamName,
                              String publicTitle, String publicBio, Boolean enabled, Boolean assignEnabled,
                              Boolean displayOnOfficial, Integer maxDailyLeads, Integer sortOrder) {
        profile.setConsultantName(name);
        profile.setPhone(phone);
        profile.setEmail(email);
        profile.setTeamName(blankToDefault(teamName, "顾问团队"));
        profile.setPublicTitle(blankToDefault(publicTitle, profile.getTeamName() + "规划顾问"));
        profile.setPublicBio(blankToDefault(publicBio, "资深留学规划顾问，擅长结合学生背景制定清晰可执行的申请方案。"));
        profile.setEnabled(!Boolean.FALSE.equals(enabled));
        profile.setAssignEnabled(!Boolean.FALSE.equals(assignEnabled));
        profile.setDisplayOnOfficial(!Boolean.FALSE.equals(displayOnOfficial));
        profile.setMaxDailyLeads(maxDailyLeads == null || maxDailyLeads <= 0 ? 30 : maxDailyLeads);
        profile.setCurrentDailyLeads(profile.getCurrentDailyLeads() == null ? 0 : profile.getCurrentDailyLeads());
        profile.setSortOrder(sortOrder == null ? 0 : sortOrder);
        profile.setUpdatedAt(Instant.now());
    }

    private void replaceRegions(ConsultantProfile profile, List<String> regionCodes) {
        List<String> codes = regionCodes.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
        if (codes.isEmpty()) throw BusinessException.badRequest("请至少选择一个负责区域");
        List<ConsultantScope> oldScopes = scopes.findAll().stream().filter(s -> Objects.equals(s.getConsultantId(), profile.getId())).toList();
        oldScopes.forEach(s -> { s.setEnabled(false); s.setUpdatedAt(Instant.now()); });
        scopes.saveAll(oldScopes);
        int priority = 10;
        for (String code : codes) {
            ConsultantRegion region = regions.activeByCode(code).orElseGet(() -> createRegion(code));
            ConsultantScope scope = oldScopes.stream().filter(s -> Objects.equals(s.getRegionId(), region.getId())).findFirst().orElseGet(ConsultantScope::new);
            scope.setConsultantId(profile.getId());
            scope.setRegionId(region.getId());
            scope.setEnabled(true);
            scope.setPriority(priority);
            scope.setUpdatedAt(Instant.now());
            scopes.save(scope);
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

    private ConsultantAdminDtos.Response toResponse(ConsultantProfile c, User user) {
        Map<Long, ConsultantRegion> regionMap = regions.findAll().stream().collect(Collectors.toMap(ConsultantRegion::getId, r -> r));
        List<ConsultantAdminDtos.RegionView> regionViews = scopes.findAll().stream()
                .filter(s -> Objects.equals(s.getConsultantId(), c.getId()) && Boolean.TRUE.equals(s.getEnabled()))
                .sorted(Comparator.comparing(ConsultantScope::getPriority))
                .map(s -> {
                    ConsultantRegion r = regionMap.get(s.getRegionId());
                    return new ConsultantAdminDtos.RegionView(s.getRegionId(), r == null ? null : r.getRegionCode(), r == null ? null : r.getRegionName(), s.getPriority());
                }).toList();
        return new ConsultantAdminDtos.Response(c.getId(), c.getUserId(), user == null ? null : user.getUsername(), c.getConsultantName(), c.getPhone(), c.getEmail(), c.getTeamName(), c.getAvatarUrl(), c.getPublicTitle(), c.getPublicBio(), regionViews, c.getEnabled(), c.getAssignEnabled(), c.getDisplayOnOfficial(), c.getMaxDailyLeads(), c.getCurrentDailyLeads(), c.getSortOrder());
    }

    private String upload(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = "consultant-avatar/" + datePath + "/" + UUID.randomUUID() + suffix;
        minioClient.putObject(PutObjectArgs.builder().bucket(bucketName).object(objectKey).stream(file.getInputStream(), file.getSize(), -1).contentType(file.getContentType()).build());
        return publicBaseUrl + "/" + objectKey;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest(message);
        return value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
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

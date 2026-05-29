package com.treeeducation.ioas.profile;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.consultant.*;
import com.treeeducation.ioas.notification.NotificationDtos;
import com.treeeducation.ioas.notification.NotificationService;
import com.treeeducation.ioas.storage.ObjectStorageService;
import com.treeeducation.ioas.storage.StoredObject;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfile;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.system.region.ConsultantRegionAssignment;
import com.treeeducation.ioas.system.region.ConsultantRegionAssignmentRepository;
import com.treeeducation.ioas.system.user.User;
import com.treeeducation.ioas.system.user.UserRepository;
import com.treeeducation.ioas.task.MediaTaskStatus;
import com.treeeducation.ioas.task.Task;
import com.treeeducation.ioas.task.TaskRepository;
import com.treeeducation.ioas.task.TaskRoleType;
import com.treeeducation.ioas.task.TaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProfileService {
    private static final int PUBLIC_BIO_MAX_LENGTH = 80;

    private final UserRepository users;
    private final OperatorProfileRepository profiles;
    private final ConsultantRegionAssignmentRepository assignments;
    private final ConsultantRegionChangeRequestRepository changeRequests;
    private final ConsultantProfileRepository consultantProfiles;
    private final ConsultantRegionRepository consultantRegions;
    private final ConsultantScopeRepository consultantScopes;
    private final ObjectStorageService storage;
    private final TaskRepository tasks;
    private final NotificationService notifications;

    public ProfileService(UserRepository users, OperatorProfileRepository profiles,
                          ConsultantRegionAssignmentRepository assignments,
                          ConsultantRegionChangeRequestRepository changeRequests,
                          ConsultantProfileRepository consultantProfiles,
                          ConsultantRegionRepository consultantRegions,
                          ConsultantScopeRepository consultantScopes,
                          ObjectStorageService storage, TaskRepository tasks,
                          NotificationService notifications) {
        this.users = users;
        this.profiles = profiles;
        this.assignments = assignments;
        this.changeRequests = changeRequests;
        this.consultantProfiles = consultantProfiles;
        this.consultantRegions = consultantRegions;
        this.consultantScopes = consultantScopes;
        this.storage = storage;
        this.tasks = tasks;
        this.notifications = notifications;
    }

    public ProfileDtos.MeResponse me(UserPrincipal principal) {
        User user = currentUser(principal);
        OperatorProfile profile = profiles.findByUserId(user.getId()).orElse(null);
        return ProfileDtos.MeResponse.of(user, profile);
    }

    @Transactional
    public ProfileDtos.MeResponse updateConsultantPublicProfile(ProfileDtos.ConsultantPublicProfileUpdateRequest request, UserPrincipal principal) {
        User user = currentUser(principal);
        if (!"CONSULTANT".equalsIgnoreCase(user.getRoleCode())) {
            throw BusinessException.forbidden("只有顾问账号可以编辑自己的官网展示简介");
        }
        OperatorProfile profile = profiles.findByUserId(user.getId()).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        String publicBio = request == null ? null : trim(request.publicBio());
        if (publicBio != null && publicBio.length() > PUBLIC_BIO_MAX_LENGTH) {
            throw BusinessException.badRequest("个人简介最多只能填写" + PUBLIC_BIO_MAX_LENGTH + "个字");
        }
        profile.setPublicBio(publicBio);
        consultantProfiles.findByUserId(user.getId()).ifPresent(cp -> {
            cp.setPublicBio(publicBio);
            cp.setUpdatedAt(Instant.now());
            consultantProfiles.save(cp);
        });
        return ProfileDtos.MeResponse.of(user, profile);
    }

    @Transactional
    public ProfileDtos.AvatarUploadResponse uploadConsultantAvatar(MultipartFile file, UserPrincipal principal) {
        User user = currentUser(principal);
        if (!"CONSULTANT".equalsIgnoreCase(user.getRoleCode())) {
            throw BusinessException.forbidden("只有顾问账号可以上传自己的官网头像");
        }
        OperatorProfile profile = profiles.findByUserId(user.getId()).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        validateImage(file, "官网头像图片不能为空", "请上传图片格式的官网头像");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String prefix = "consultant/avatar/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + String.format("%02d", today.getDayOfMonth()) + "/" + safe(profile.getName());
        StoredObject object = storage.put(prefix, file);
        profile.setConsultantAvatarPublicUrl(object.previewUrl());
        consultantProfiles.findByUserId(user.getId()).ifPresent(cp -> {
            cp.setAvatarUrl(object.previewUrl());
            cp.setUpdatedAt(Instant.now());
            consultantProfiles.save(cp);
        });

        notifications.sendToUser(new NotificationDtos.SendRequest(user.getId(), "CONSULTANT", "官网头像上传完成",
                "你的官网展示头像已上传成功，官网顾问团队区域会使用这张头像。",
                "profile", profile.getId(), "/profile/settings", "AVATAR_UPLOAD", 20));

        return new ProfileDtos.AvatarUploadResponse(object.bucketName(), object.objectKey(), object.previewUrl());
    }

    @Transactional
    public ProfileDtos.QrUploadResponse uploadConsultantQr(MultipartFile file, UserPrincipal principal) {
        User user = currentUser(principal);
        if (!"CONSULTANT".equalsIgnoreCase(user.getRoleCode())) {
            throw BusinessException.forbidden("只有顾问账号可以上传自己的企业微信二维码");
        }
        OperatorProfile profile = profiles.findByUserId(user.getId()).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        validateImage(file, "二维码图片不能为空", "请上传图片格式的企业微信二维码");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String prefix = "consultant/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + String.format("%02d", today.getDayOfMonth()) + "/" + safe(profile.getName());
        StoredObject object = storage.put(prefix, file);

        profile.setConsultantQrBucketName(object.bucketName());
        profile.setConsultantQrObjectKey(object.objectKey());
        profile.setConsultantQrPublicUrl(object.previewUrl());

        Task task = new Task();
        task.setType("CONSULTANT_QR_UPLOAD");
        task.setTitle("企业微信二维码上传完成 - " + profile.getName());
        task.setTaskType(TaskType.consultant_qr_upload);
        task.setRoleType(TaskRoleType.media);
        task.setAssigneeId(user.getId());
        task.setAssigneeName(profile.getName());
        task.setStatus(MediaTaskStatus.success.name());
        task.setProgress(100);
        task.setUploadBucketName(object.bucketName());
        task.setUploadObjectKey(object.objectKey());
        task.setUploadPublicUrl(object.previewUrl());
        task.setFileName(file.getOriginalFilename());
        task.setFileSize(file.getSize());
        task.setUploadedBytes(file.getSize());
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        Task saved = tasks.save(task);

        notifications.sendToUser(new NotificationDtos.SendRequest(user.getId(), "CONSULTANT", "企业微信二维码上传完成",
                "你的企业微信二维码已上传成功，官网咨询提交后会展示给分配到你的学生。",
                "profile", saved.getId(), "/profile/settings", "CONSULTANT_QR_UPLOAD_SUCCESS", 20));

        return new ProfileDtos.QrUploadResponse(object.bucketName(), object.objectKey(), object.previewUrl(), saved.getId());
    }

    @Transactional
    public ProfileDtos.RegionChangeResponse requestRegionChange(ProfileDtos.RegionChangeRequest request, UserPrincipal principal) {
        User user = currentUser(principal);
        if (!"CONSULTANT".equalsIgnoreCase(user.getRoleCode())) {
            throw BusinessException.forbidden("只有顾问账号可以提交擅长地区变更申请");
        }
        OperatorProfile profile = profiles.findByUserId(user.getId()).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        if (request == null || blank(request.requestedRegionCodes()) || blank(request.requestedRegionNames())) {
            throw BusinessException.badRequest("请选择要申请的擅长地区");
        }
        ConsultantRegionChangeRequest row = new ConsultantRegionChangeRequest();
        row.setConsultantUserId(user.getId());
        row.setConsultantProfileId(profile.getId());
        row.setConsultantName(profile.getName());
        row.setCurrentRegionCodes(profile.getSpecialityRegionCodes());
        row.setCurrentRegionNames(profile.getSpecialityRegionNames());
        row.setRequestedRegionCodes(normalizeRegionCodesCsv(request.requestedRegionCodes()));
        row.setRequestedRegionNames(normalizeRegionNamesCsv(row.getRequestedRegionCodes(), request.requestedRegionNames()));
        row.setReason(request.reason());
        row = changeRequests.save(row);

        Task task = new Task();
        task.setType("CONSULTANT_REGION_CHANGE");
        task.setTitle("顾问擅长地区变更待审批 - " + profile.getName());
        task.setTaskType(TaskType.consultant_region_change);
        task.setRoleType(TaskRoleType.media);
        task.setAssigneeId(user.getId());
        task.setAssigneeName(profile.getName());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setUpdatedAt(Instant.now());
        tasks.save(task);
        return ProfileDtos.RegionChangeResponse.of(row);
    }

    public List<ProfileDtos.RegionChangeResponse> myRegionChangeRequests(UserPrincipal principal) {
        User user = currentUser(principal);
        return changeRequests.findByConsultantUserIdOrderByRequestedAtDesc(user.getId()).stream().map(ProfileDtos.RegionChangeResponse::of).toList();
    }

    public List<ProfileDtos.RegionChangeResponse> pendingRegionChangeRequests() {
        return changeRequests.findByStatusOrderByRequestedAtDesc(ConsultantRegionChangeStatus.PENDING).stream().map(ProfileDtos.RegionChangeResponse::of).toList();
    }

    @Transactional
    public ProfileDtos.RegionChangeResponse approveRegionChange(Long id, ProfileDtos.ReviewRequest request, UserPrincipal principal) {
        User reviewer = currentUser(principal);
        ConsultantRegionChangeRequest row = changeRequests.findById(id).orElseThrow(() -> BusinessException.notFound("申请不存在"));
        OperatorProfile profile = profiles.findById(row.getConsultantProfileId()).orElseThrow(() -> BusinessException.notFound("顾问档案不存在"));
        row.setStatus(ConsultantRegionChangeStatus.APPROVED);
        row.setReviewerUserId(reviewer.getId());
        row.setReviewerName(reviewer.getDisplayName());
        row.setReviewRemark(request == null ? null : request.remark());
        row.setReviewedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        profile.setSpecialityRegionCodes(row.getRequestedRegionCodes());
        profile.setSpecialityRegionNames(row.getRequestedRegionNames());
        profile.setPublicTitle(firstCsvValue(row.getRequestedRegionNames(), "留学") + "留学规划顾问");
        syncApprovedRegionAssignment(profile, row);
        syncConsultantModule(profile, row);
        markRegionChangeTasks(profile, "APPROVED");
        return ProfileDtos.RegionChangeResponse.of(row);
    }

    @Transactional
    public ProfileDtos.RegionChangeResponse rejectRegionChange(Long id, ProfileDtos.ReviewRequest request, UserPrincipal principal) {
        User reviewer = currentUser(principal);
        ConsultantRegionChangeRequest row = changeRequests.findById(id).orElseThrow(() -> BusinessException.notFound("申请不存在"));
        OperatorProfile profile = profiles.findById(row.getConsultantProfileId()).orElse(null);
        row.setStatus(ConsultantRegionChangeStatus.REJECTED);
        row.setReviewerUserId(reviewer.getId());
        row.setReviewerName(reviewer.getDisplayName());
        row.setReviewRemark(request == null ? null : request.remark());
        row.setReviewedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        if (profile != null) markRegionChangeTasks(profile, "REJECTED");
        return ProfileDtos.RegionChangeResponse.of(row);
    }

    public ProfileDtos.PublicConsultantResponse publicConsultantByUserId(Long userId, String regionCode, String regionName) {
        OperatorProfile profile = profiles.findByUserId(userId).orElse(null);
        return profile == null ? null : ProfileDtos.PublicConsultantResponse.of(profile, regionCode, regionName);
    }

    public List<ProfileDtos.PublicConsultantCardResponse> publicConsultants() {
        Map<Long, ProfileDtos.PublicConsultantCardResponse> result = new LinkedHashMap<>();
        assignments.findByEnabledTrueOrderByPriorityAscIdAsc().forEach(row -> {
            if (row.getConsultantProfileId() == null || result.containsKey(row.getConsultantProfileId())) return;
            profiles.findById(row.getConsultantProfileId())
                    .filter(profile -> Boolean.TRUE.equals(profile.getEnabled()))
                    .ifPresent(profile -> result.put(profile.getId(), ProfileDtos.PublicConsultantCardResponse.of(profile, row.getRegionCode(), row.getRegionName(), row.getPriority())));
        });
        return result.values().stream().toList();
    }

    private void validateImage(MultipartFile file, String emptyMessage, String typeMessage) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(emptyMessage);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            throw BusinessException.badRequest(typeMessage);
        }
    }

    private void syncApprovedRegionAssignment(OperatorProfile profile, ConsultantRegionChangeRequest row) {
        List<String> codes = splitCsv(row.getRequestedRegionCodes());
        List<String> names = splitCsv(row.getRequestedRegionNames());
        List<ConsultantRegionAssignment> rows = assignments.findByConsultantUserIdOrderByPriorityAscIdAsc(profile.getUserId());
        assignments.deleteAll(rows);
        for (int i = 0; i < codes.size(); i++) {
            ConsultantRegionAssignment item = new ConsultantRegionAssignment();
            item.setConsultantProfileId(profile.getId());
            item.setConsultantUserId(profile.getUserId());
            item.setRegionCode(codes.get(i));
            item.setRegionName(i < names.size() ? names.get(i) : regionName(codes.get(i)));
            item.setPriority((i + 1) * 10);
            item.setEnabled(true);
            assignments.save(item);
        }
    }

    private void syncConsultantModule(OperatorProfile profile, ConsultantRegionChangeRequest row) {
        ConsultantProfile cp = consultantProfiles.findByUserId(profile.getUserId()).orElse(null);
        if (cp == null) return;
        cp.setPublicTitle(profile.getPublicTitle());
        cp.setPublicBio(profile.getPublicBio());
        cp.setAvatarUrl(profile.getConsultantAvatarPublicUrl());
        cp.setUpdatedAt(Instant.now());
        consultantProfiles.save(cp);

        consultantScopes.deleteAll(consultantScopes.findAll().stream().filter(s -> Objects.equals(s.getConsultantId(), cp.getId())).toList());
        List<String> codes = splitCsv(row.getRequestedRegionCodes());
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            ConsultantRegion region = consultantRegions.activeByCode(code).orElseGet(() -> createConsultantRegion(code));
            ConsultantScope scope = new ConsultantScope();
            scope.setConsultantId(cp.getId());
            scope.setRegionId(region.getId());
            scope.setPriority((i + 1) * 10);
            scope.setEnabled(true);
            scope.setUpdatedAt(Instant.now());
            consultantScopes.save(scope);
        }
    }

    private ConsultantRegion createConsultantRegion(String code) {
        ConsultantRegion region = new ConsultantRegion();
        region.setRegionCode(code);
        region.setRegionName(regionName(code));
        region.setRegionType("REGION");
        region.setEnabled(true);
        region.setSortOrder(999);
        region.setRemark("顾问地区变更审批自动补齐");
        return consultantRegions.save(region);
    }

    private void markRegionChangeTasks(OperatorProfile profile, String status) {
        tasks.findAll().stream()
                .filter(task -> task.getTaskType() == TaskType.consultant_region_change)
                .filter(task -> profile.getUserId().equals(task.getAssigneeId()))
                .filter(task -> "PENDING".equalsIgnoreCase(task.getStatus()))
                .forEach(task -> {
                    task.setStatus(status);
                    task.setProgress(100);
                    task.setCompletedAt(Instant.now());
                    task.setUpdatedAt(Instant.now());
                    task.setTitle(("APPROVED".equalsIgnoreCase(status) ? "顾问擅长地区变更已通过 - " : "顾问擅长地区变更已拒绝 - ") + profile.getName());
                    tasks.save(task);
                });
    }

    private String firstCsvValue(String value, String defaultValue) {
        String cleaned = trim(value);
        if (cleaned == null) return defaultValue;
        String[] parts = cleaned.split(",");
        return parts.length == 0 || parts[0].isBlank() ? defaultValue : parts[0].trim();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String safe(String value) { return (value == null || value.isBlank()) ? "consultant" : value.replaceAll("[^a-zA-Z0-9一-龥_-]", "_"); }
    private User currentUser(UserPrincipal principal) {
        if (principal == null) throw BusinessException.forbidden("请先登录");
        return users.findById(principal.id()).orElseThrow(() -> BusinessException.forbidden("登录用户不存在"));
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private String normalizeRegionCodesCsv(String value) {
        return splitCsv(value).stream().map(this::normalizeRegionCode).distinct().collect(Collectors.joining(","));
    }

    private String normalizeRegionNamesCsv(String codesCsv, String fallbackNamesCsv) {
        List<String> fallback = splitCsv(fallbackNamesCsv);
        List<String> codes = splitCsv(codesCsv);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            String fallbackName = i < fallback.size() ? fallback.get(i) : null;
            names.add(regionName(code, fallbackName));
        }
        return String.join(",", names);
    }

    private String normalizeRegionCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase();
        return switch (code) {
            case "AUSTRALIA", "澳洲" -> "AU";
            case "USA", "US", "美国" -> "US";
            case "UK", "英国" -> "UK";
            case "EUROPE", "EU", "欧洲" -> "EU";
            case "CANADA", "CA", "加拿大" -> "CA";
            case "SINGAPORE", "SG", "新加坡" -> "SG";
            case "JAPAN", "JP", "日本" -> "JP";
            case "HONGKONG", "HONG_KONG", "HK", "中国香港" -> "HK";
            default -> code.isBlank() ? "OTHER" : code;
        };
    }

    private String regionName(String code) { return regionName(code, null); }
    private String regionName(String code, String fallback) {
        String normalized = normalizeRegionCode(code);
        return switch (normalized) {
            case "AU" -> "澳洲";
            case "US" -> "美国";
            case "UK" -> "英国";
            case "EU" -> "欧洲";
            case "CA" -> "加拿大";
            case "SG" -> "新加坡";
            case "JP" -> "日本";
            case "HK" -> "中国香港";
            default -> (fallback == null || fallback.isBlank()) ? normalized : fallback;
        };
    }
}

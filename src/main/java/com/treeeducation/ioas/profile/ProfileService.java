package com.treeeducation.ioas.profile;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProfileService {
    private static final int PUBLIC_BIO_MAX_LENGTH = 80;

    private final UserRepository users;
    private final OperatorProfileRepository profiles;
    private final ConsultantRegionAssignmentRepository assignments;
    private final ConsultantRegionChangeRequestRepository changeRequests;
    private final ObjectStorageService storage;
    private final TaskRepository tasks;
    private final NotificationService notifications;

    public ProfileService(UserRepository users, OperatorProfileRepository profiles,
                          ConsultantRegionAssignmentRepository assignments,
                          ConsultantRegionChangeRequestRepository changeRequests,
                          ObjectStorageService storage, TaskRepository tasks,
                          NotificationService notifications) {
        this.users = users;
        this.profiles = profiles;
        this.assignments = assignments;
        this.changeRequests = changeRequests;
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
        String prefix = "consultant-avatar/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + String.format("%02d", today.getDayOfMonth()) + "/" + safe(profile.getName());
        StoredObject object = storage.put(prefix, file);
        profile.setConsultantAvatarPublicUrl(object.previewUrl());

        notifications.sendToUser(new NotificationDtos.SendRequest(user.getId(), "CONSULTANT", "官网头像上传完成",
                "你的官网展示头像已上传成功，官网顾问团队区域会使用这张头像。",
                "profile", profile.getId(), "/profile/settings", "CONSULTANT_AVATAR_UPLOAD_SUCCESS", 20));

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
        row.setRequestedRegionCodes(request.requestedRegionCodes());
        row.setRequestedRegionNames(request.requestedRegionNames());
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
        String regionCode = firstCsvValue(row.getRequestedRegionCodes(), "OTHER");
        String regionName = firstCsvValue(row.getRequestedRegionNames(), "其他区域");
        List<ConsultantRegionAssignment> rows = assignments.findByConsultantUserIdOrderByPriorityAscIdAsc(profile.getUserId());
        if (rows.isEmpty()) {
            return;
        }
        ConsultantRegionAssignment primary = rows.get(0);
        primary.setConsultantProfileId(profile.getId());
        primary.setConsultantUserId(profile.getUserId());
        primary.setRegionCode(regionCode);
        primary.setRegionName(regionName);
        primary.setEnabled(true);
        assignments.save(primary);
        for (int i = 1; i < rows.size(); i++) {
            ConsultantRegionAssignment extra = rows.get(i);
            extra.setEnabled(false);
            assignments.save(extra);
        }
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
        return parts.length == 0 || parts[0].trim().isEmpty() ? defaultValue : parts[0].trim();
    }

    private User currentUser(UserPrincipal principal) {
        if (principal == null || principal.id() == null) throw BusinessException.forbidden("请先登录");
        return users.findById(principal.id()).orElseThrow(() -> BusinessException.forbidden("登录用户不存在"));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
    }
}

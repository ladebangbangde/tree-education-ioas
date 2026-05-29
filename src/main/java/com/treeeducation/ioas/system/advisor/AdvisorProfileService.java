package com.treeeducation.ioas.system.advisor;

import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.consultant.ConsultantProfile;
import com.treeeducation.ioas.consultant.ConsultantProfileRepository;
import com.treeeducation.ioas.system.user.User;
import com.treeeducation.ioas.system.user.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class AdvisorProfileService {
    private final AdvisorProfileRepository advisorProfiles;
    private final ConsultantProfileRepository consultantProfiles;
    private final UserRepository users;
    private final MinioClient minioClient;

    @Value("${ioas.storage.bucket}")
    private String bucketName;

    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    public AdvisorProfileService(AdvisorProfileRepository advisorProfiles,
                                 ConsultantProfileRepository consultantProfiles,
                                 UserRepository users,
                                 MinioClient minioClient) {
        this.advisorProfiles = advisorProfiles;
        this.consultantProfiles = consultantProfiles;
        this.users = users;
        this.minioClient = minioClient;
    }

    public List<AdvisorDtos.Response> list() {
        return advisorProfiles.findByEnabledTrueOrderBySortOrderAscIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdvisorDtos.AvatarResponse uploadAvatar(Long userId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传顾问头像文件");
        }
        AdvisorProfile advisor = ensureAdvisorProfile(userId);
        ensureConsultantProfile(userId, advisor);
        String avatarUrl = upload(file);
        advisor.setAvatarUrl(avatarUrl);
        advisorProfiles.save(advisor);
        return new AdvisorDtos.AvatarResponse(userId, avatarUrl);
    }

    @Transactional
    public AdvisorProfile ensureAdvisorProfile(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("顾问账号不存在"));
        return advisorProfiles.findByUserId(userId).orElseGet(() -> {
            AdvisorProfile profile = new AdvisorProfile();
            profile.setUserId(userId);
            profile.setDisplayName(user.getDisplayName());
            profile.setGender("UNKNOWN");
            profile.setResponsibleRegion(defaultRegionName(user));
            profile.setLocationRegion(defaultRegionName(user));
            profile.setRegionCode(defaultRegionCode(user));
            profile.setPublicTitle(defaultRegionName(user) + "规划顾问");
            profile.setBio("资深留学规划顾问，擅长结合学生背景制定清晰可执行的申请方案。");
            profile.setEnabled(true);
            profile.setSortOrder(999);
            return advisorProfiles.save(profile);
        });
    }

    @Transactional
    public ConsultantProfile ensureConsultantProfile(Long userId, AdvisorProfile advisor) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("顾问账号不存在"));
        return consultantProfiles.findByUserId(userId).orElseGet(() -> {
            ConsultantProfile profile = new ConsultantProfile();
            profile.setUserId(userId);
            profile.setConsultantName(advisor.getDisplayName() == null ? user.getDisplayName() : advisor.getDisplayName());
            profile.setTeamName((advisor.getResponsibleRegion() == null ? "顾问" : advisor.getResponsibleRegion()) + "顾问组");
            profile.setEnabled(true);
            profile.setAssignEnabled(true);
            profile.setMaxDailyLeads(30);
            profile.setCurrentDailyLeads(0);
            return consultantProfiles.save(profile);
        });
    }

    private AdvisorDtos.Response toResponse(AdvisorProfile p) {
        return new AdvisorDtos.Response(
                p.getUserId(),
                p.getDisplayName(),
                p.getGender(),
                p.getRegionCode(),
                p.getResponsibleRegion(),
                p.getPublicTitle() == null ? p.getResponsibleRegion() + "规划顾问" : p.getPublicTitle(),
                p.getBio(),
                p.getAvatarUrl(),
                p.getSortOrder()
        );
    }

    private String upload(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = "advisor-avatar/" + datePath + "/" + UUID.randomUUID() + suffix;
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return publicBaseUrl + "/" + objectKey;
    }

    private String defaultRegionName(User user) {
        String department = user.getDepartment() == null ? "" : user.getDepartment();
        if (department.contains("澳")) return "澳洲";
        if (department.contains("英")) return "英国";
        if (department.contains("美")) return "美国";
        if (department.contains("欧")) return "欧洲";
        return "全球";
    }

    private String defaultRegionCode(User user) {
        String region = defaultRegionName(user);
        if ("澳洲".equals(region)) return "AU";
        if ("英国".equals(region)) return "UK";
        if ("美国".equals(region)) return "US";
        if ("欧洲".equals(region)) return "EU";
        return "GLOBAL";
    }
}

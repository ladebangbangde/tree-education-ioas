package com.treeeducation.ioas.system.advisor;

import com.treeeducation.ioas.system.user.User;
import com.treeeducation.ioas.system.user.UserRepository;
import com.treeeducation.ioas.system.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdvisorAccountSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final AdvisorProfileRepository advisorProfiles;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public AdvisorAccountSeeder(UserRepository users,
                                AdvisorProfileRepository advisorProfiles,
                                PasswordEncoder passwordEncoder,
                                @Value("${ioas.seed.consultants.password:}") String seedPassword) {
        this.users = users;
        this.advisorProfiles = advisorProfiles;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsert("emily.carter", "Emily Carter", "澳洲顾问组", "FEMALE", "澳洲", "澳洲",
                "专注澳洲本科、硕士申请规划，熟悉澳洲八大院校申请节奏、材料准备与签证流程。", 10);
        upsert("sophie.williams", "Sophie Williams", "英国顾问组", "FEMALE", "英国", "英国",
                "擅长英国院校定位、文书方向梳理和申请时间线管理，重点服务商科、教育、传媒方向学生。", 20);
        upsert("daniel.miller", "Daniel Miller", "美国顾问组", "MALE", "美国", "美国",
                "负责美国本科与研究生申请规划，熟悉院校筛选、背景提升、推荐信和申请流程管理。", 30);
    }

    private void upsert(String username, String displayName, String department, String gender,
                        String responsibleRegion, String locationRegion, String bio, int sortOrder) {
        User user = users.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setDepartment(department);
        user.setRoleCode("CONSULTANT");
        user.setStatus(hasSeedPassword() ? UserStatus.ACTIVE : UserStatus.DISABLED);
        if (hasSeedPassword()) {
            user.setPasswordHash(passwordEncoder.encode(seedPassword));
        } else if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode("disabled-account-placeholder"));
        }
        user = users.save(user);

        AdvisorProfile profile = advisorProfiles.findByUserId(user.getId()).orElseGet(AdvisorProfile::new);
        profile.setUserId(user.getId());
        profile.setDisplayName(displayName);
        profile.setGender(gender);
        profile.setResponsibleRegion(responsibleRegion);
        profile.setLocationRegion(locationRegion);
        profile.setBio(bio);
        profile.setEnabled(true);
        profile.setSortOrder(sortOrder);
        advisorProfiles.save(profile);
    }

    private boolean hasSeedPassword() {
        return seedPassword != null && !seedPassword.isBlank();
    }
}

package com.treeeducation.ioas.publicapi;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.profile.ProfileDtos;
import com.treeeducation.ioas.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/legacy-consultants")
@Tag(name = "Public Consultants Legacy", description = "旧官网公开顾问展示接口，保留兼容但不占用正式路径")
public class PublicConsultantController {
    private final ProfileService profileService;

    public PublicConsultantController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "旧版官网公开顾问展示列表")
    public ApiResponse<List<ProfileDtos.PublicConsultantCardResponse>> list() {
        return ApiResponse.ok(profileService.publicConsultants());
    }
}

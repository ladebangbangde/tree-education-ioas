package com.treeeducation.ioas.system.advisor;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/advisors")
public class AdvisorProfileController {
    private final AdvisorProfileService service;

    public AdvisorProfileController(AdvisorProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdvisorDtos.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping(value = "/{userId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdvisorDtos.AvatarResponse> uploadAvatar(@PathVariable Long userId,
                                                                 @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(service.uploadAvatar(userId, file));
    }
}

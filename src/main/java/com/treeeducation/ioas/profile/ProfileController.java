package com.treeeducation.ioas.profile;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile Settings", description = "个人信息设置、顾问二维码和擅长地区变更申请")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户个人信息设置")
    public ApiResponse<ProfileDtos.MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.me(principal));
    }

    @PostMapping("/consultant/qr")
    @PreAuthorize("hasRole('CONSULTANT')")
    @Operation(summary = "顾问上传自己的企业微信二维码")
    public ApiResponse<ProfileDtos.QrUploadResponse> uploadConsultantQr(@RequestPart("file") MultipartFile file,
                                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.uploadConsultantQr(file, principal));
    }

    @PostMapping("/consultant/region-change-requests")
    @PreAuthorize("hasRole('CONSULTANT')")
    @Operation(summary = "顾问提交擅长地区变更申请")
    public ApiResponse<ProfileDtos.RegionChangeResponse> requestRegionChange(@RequestBody ProfileDtos.RegionChangeRequest request,
                                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.requestRegionChange(request, principal));
    }

    @GetMapping("/consultant/region-change-requests/mine")
    @PreAuthorize("hasRole('CONSULTANT')")
    @Operation(summary = "顾问查看自己的擅长地区变更申请")
    public ApiResponse<List<ProfileDtos.RegionChangeResponse>> myRegionChangeRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.myRegionChangeRequests(principal));
    }

    @GetMapping("/admin/region-change-requests")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "超管查看待审批的顾问擅长地区变更申请")
    public ApiResponse<List<ProfileDtos.RegionChangeResponse>> pendingRegionChangeRequests() {
        return ApiResponse.ok(service.pendingRegionChangeRequests());
    }

    @PostMapping("/admin/region-change-requests/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "超管通过顾问擅长地区变更申请")
    public ApiResponse<ProfileDtos.RegionChangeResponse> approve(@PathVariable Long id,
                                                                  @RequestBody(required = false) ProfileDtos.ReviewRequest request,
                                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.approveRegionChange(id, request, principal));
    }

    @PostMapping("/admin/region-change-requests/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "超管拒绝顾问擅长地区变更申请")
    public ApiResponse<ProfileDtos.RegionChangeResponse> reject(@PathVariable Long id,
                                                                 @RequestBody(required = false) ProfileDtos.ReviewRequest request,
                                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.rejectRegionChange(id, request, principal));
    }
}

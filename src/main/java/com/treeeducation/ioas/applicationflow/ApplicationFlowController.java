package com.treeeducation.ioas.applicationflow;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/application-flows")
@Tag(name = "Application Flow", description = "顾问对接后的申请流程管理")
public class ApplicationFlowController {
    private final ApplicationFlowService service;

    public ApplicationFlowController(ApplicationFlowService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "申请流程列表")
    public ApiResponse<PageResponse<ApplicationFlowDtos.Response>> list(@RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.list(keyword, pageNum, pageSize, principal));
    }

    @PostMapping("/students/{studentProfileId}/start")
    @Operation(summary = "为客户档案创建申请流程")
    public ApiResponse<ApplicationFlowDtos.Response> start(@PathVariable Long studentProfileId,
                                                           @RequestBody(required = false) ApplicationFlowDtos.StartRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.start(studentProfileId, request, principal));
    }

    @GetMapping("/{flowId}")
    @Operation(summary = "申请流程详情")
    public ApiResponse<ApplicationFlowDtos.Response> detail(@PathVariable Long flowId,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.detail(flowId, principal));
    }

    @GetMapping("/students/{studentProfileId}")
    @Operation(summary = "按客户档案查询申请流程")
    public ApiResponse<ApplicationFlowDtos.Response> detailByStudent(@PathVariable Long studentProfileId,
                                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.detailByStudent(studentProfileId, principal));
    }

    @PatchMapping("/{flowId}/steps/{stepCode}")
    @Operation(summary = "更新或推进申请流程节点")
    public ApiResponse<ApplicationFlowDtos.Response> updateStep(@PathVariable Long flowId,
                                                                @PathVariable ApplicationStepCode stepCode,
                                                                @RequestBody ApplicationFlowDtos.AdvanceRequest request,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.updateStep(flowId, stepCode, request, principal));
    }

    @PostMapping("/{flowId}/steps/{stepCode}/attachments")
    @Operation(summary = "上传申请流程节点材料到 MinIO")
    public ApiResponse<ApplicationFlowDtos.AttachmentResponse> upload(@PathVariable Long flowId,
                                                                      @PathVariable ApplicationStepCode stepCode,
                                                                      @RequestParam(required = false) ApplicationAttachmentType attachmentType,
                                                                      @RequestParam(required = false) String note,
                                                                      @RequestPart("file") MultipartFile file,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.upload(flowId, stepCode, attachmentType, note, file, principal));
    }

    @GetMapping("/public/students/{studentProfileId}/progress")
    @Operation(summary = "客资端申请进度查询接口")
    public ApiResponse<ApplicationFlowDtos.Response> customerProgress(@PathVariable Long studentProfileId) {
        return ApiResponse.ok(service.customerProgress(studentProfileId));
    }
}

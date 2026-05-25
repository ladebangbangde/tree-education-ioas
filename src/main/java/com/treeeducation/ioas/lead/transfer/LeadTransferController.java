package com.treeeducation.ioas.lead.transfer;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Lead Transfer", description = "顾问线索转让申请、同意与拒绝")
public class LeadTransferController {
    private final LeadTransferService service;

    public LeadTransferController(LeadTransferService service) {
        this.service = service;
    }

    @GetMapping("/consultants")
    @Operation(summary = "查询可转让目标顾问")
    public ApiResponse<List<LeadTransferDtos.ConsultantOption>> consultants(@AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.consultants(p));
    }

    @PostMapping("/{leadId}/transfer-requests")
    @Operation(summary = "顾问发起线索转让申请")
    public ApiResponse<LeadTransferDtos.Response> create(@PathVariable Long leadId,
                                                         @Valid @RequestBody LeadTransferDtos.CreateRequest request,
                                                         @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.create(leadId, request, p));
    }

    @GetMapping("/transfer-requests/mine")
    @Operation(summary = "查询我的线索转让申请")
    public ApiResponse<List<LeadTransferDtos.Response>> mine(@RequestParam(required = false, defaultValue = "received") String scope,
                                                             @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.mine(scope, p));
    }

    @PostMapping("/transfer-requests/{id}/accept")
    @Operation(summary = "目标顾问同意接收线索")
    public ApiResponse<LeadTransferDtos.Response> accept(@PathVariable Long id,
                                                         @RequestBody(required = false) LeadTransferDtos.RespondRequest request,
                                                         @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.accept(id, request, p));
    }

    @PostMapping("/transfer-requests/{id}/reject")
    @Operation(summary = "目标顾问拒绝接收线索")
    public ApiResponse<LeadTransferDtos.Response> reject(@PathVariable Long id,
                                                         @RequestBody(required = false) LeadTransferDtos.RespondRequest request,
                                                         @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.reject(id, request, p));
    }
}

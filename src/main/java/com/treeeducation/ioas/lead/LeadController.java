package com.treeeducation.ioas.lead;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Lead APIs. */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Lead", description = "线索列表、详情、创建、分配与状态更新")
public class LeadController {
    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "线索列表")
    public ApiResponse<PageResponse<LeadDtos.Response>> list(@RequestParam(required = false) String tab,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Long relatedPackageId,
                                                             @RequestParam(required = false) Long operatorId,
                                                             @RequestParam(defaultValue = "1") int pageNum,
                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                             @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(PageResponse.of(service.list(tab, keyword, relatedPackageId, operatorId, p), pageNum, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "基于主题包创建线索")
    public ApiResponse<LeadDtos.Response> create(@Valid @RequestBody LeadDtos.CreateRequest r, @AuthenticationPrincipal UserPrincipal p) {
        Lead lead = service.create(r, p);
        return ApiResponse.ok(service.detail(lead.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "线索详情")
    public ApiResponse<LeadDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "更新线索状态")
    public ApiResponse<LeadDtos.Response> status(@PathVariable Long id, @Valid @RequestBody LeadDtos.StatusRequest r,
                                                  @AuthenticationPrincipal UserPrincipal p) {
        Lead lead = service.updateStatus(id, r.status(), p);
        return ApiResponse.ok(service.detail(lead.getId()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新线索备注、分配人和状态")
    public ApiResponse<LeadDtos.Response> update(@PathVariable Long id, @RequestBody LeadDtos.UpdateRequest r,
                                                 @AuthenticationPrincipal UserPrincipal p) {
        Lead lead = service.update(id, r, p);
        return ApiResponse.ok(service.detail(lead.getId()));
    }
}

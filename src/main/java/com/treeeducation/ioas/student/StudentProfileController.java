package com.treeeducation.ioas.student;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Student Profile", description = "学生档案管理与线索转化")
public class StudentProfileController {
    private final StudentProfileService service;

    public StudentProfileController(StudentProfileService service) {
        this.service = service;
    }

    @PostMapping("/leads/{leadId}/convert-student")
    @Operation(summary = "顾问确认线索并生成学生档案")
    public ApiResponse<StudentProfileDtos.Response> convert(@PathVariable Long leadId,
                                                            @RequestBody(required = false) StudentProfileDtos.ConvertRequest request,
                                                            @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.convertFromLead(leadId, request, p));
    }

    @GetMapping("/students")
    @Operation(summary = "查询学生档案列表")
    public ApiResponse<PageResponse<StudentProfileDtos.Response>> list(@RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Long ownerConsultantId,
                                                                       @RequestParam(required = false) String intentionRegionCode,
                                                                       @RequestParam(required = false) StudentProfileStatus profileStatus,
                                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                                       @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.list(keyword, ownerConsultantId, intentionRegionCode, profileStatus, pageNum, pageSize, p));
    }

    @GetMapping("/students/{id}")
    @Operation(summary = "查询学生档案详情")
    public ApiResponse<StudentProfileDtos.Response> detail(@PathVariable Long id,
                                                           @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.detail(id, p));
    }

    @PatchMapping("/students/{id}")
    @Operation(summary = "修改学生档案")
    public ApiResponse<StudentProfileDtos.Response> update(@PathVariable Long id,
                                                           @RequestBody StudentProfileDtos.UpdateRequest request,
                                                           @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.update(id, request, p));
    }

    @DeleteMapping("/students/{id}")
    @Operation(summary = "软删除学生档案")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserPrincipal p) {
        service.remove(id, p);
        return ApiResponse.ok(Map.of("deleted", true, "id", id));
    }
}

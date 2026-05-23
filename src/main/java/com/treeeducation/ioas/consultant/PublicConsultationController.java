package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Consultation", description = "官网公开咨询入口")
public class PublicConsultationController {
    private final PublicConsultationService service;

    public PublicConsultationController(PublicConsultationService service) {
        this.service = service;
    }

    @GetMapping("/consultation-options")
    @Operation(summary = "获取官网咨询表单选项")
    public ApiResponse<ConsultationDtos.OptionsResponse> options() {
        return ApiResponse.ok(service.options());
    }

    @PostMapping("/consultations")
    @Operation(summary = "提交官网一分钟咨询")
    public ApiResponse<ConsultationDtos.CreateResponse> create(@Valid @RequestBody ConsultationDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }
}

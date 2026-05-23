package com.treeeducation.ioas.consultation;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Consultation", description = "官网一分钟咨询公开接口")
public class PublicConsultationController {
    private final ConsultantAssignmentService assignmentService;
    private final PublicConsultationService consultationService;

    public PublicConsultationController(ConsultantAssignmentService assignmentService,
                                        PublicConsultationService consultationService) {
        this.assignmentService = assignmentService;
        this.consultationService = consultationService;
    }

    @GetMapping("/consultation-options")
    @Operation(summary = "获取官网咨询表单可选意向区域")
    public ApiResponse<PublicConsultationDtos.OptionsResponse> options() {
        List<PublicConsultationDtos.RegionOption> regions = assignmentService.publicOptions().stream()
                .map(r -> new PublicConsultationDtos.RegionOption(r.getId(), r.getRegionCode(), r.getRegionName(), r.getRegionType()))
                .toList();
        return ApiResponse.ok(new PublicConsultationDtos.OptionsResponse(regions));
    }

    @PostMapping("/consultations")
    @Operation(summary = "提交官网一分钟咨询并自动分配顾问")
    public ApiResponse<PublicConsultationDtos.SubmitResponse> submit(@Valid @RequestBody PublicConsultationDtos.SubmitRequest request) {
        return ApiResponse.ok(consultationService.submit(request));
    }
}

package com.treeeducation.ioas.official.lead;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/official/leads")
@Tag(name = "OfficialLead", description = "官网咨询线索")
public class OfficialLeadController {

    private final OfficialLeadService service;

    public OfficialLeadController(OfficialLeadService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "提交官网咨询线索")
    public ApiResponse<OfficialLeadDtos.LeadResponse> create(
            @Valid @RequestBody OfficialLeadDtos.CreateLeadRequest request
    ) {
        return ApiResponse.ok(service.create(request));
    }
}

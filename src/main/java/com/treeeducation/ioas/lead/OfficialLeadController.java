package com.treeeducation.ioas.lead;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/official/leads")
@Tag(name = "Website Lead", description = "Website consultation lead endpoint")
public class OfficialLeadController {
    private final LeadService leadService;

    public OfficialLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @Operation(summary = "Submit website consultation and create lead")
    public ApiResponse<LeadDtos.Response> create(@Valid @RequestBody LeadDtos.OfficialWebsiteRequest request,
                                                 HttpServletRequest httpRequest) {
        String sourcePage = httpRequest.getHeader("Referer");
        String userAgent = httpRequest.getHeader("User-Agent");
        Lead lead = leadService.createOfficialWebsiteLead(request, sourcePage, userAgent);
        return ApiResponse.ok(leadService.detail(lead.getId()));
    }
}

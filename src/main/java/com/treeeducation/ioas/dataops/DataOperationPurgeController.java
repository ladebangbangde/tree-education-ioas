package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-ops/admin/purge")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DataOperationPurgeController {
    private final DataOperationPurgeService purgeService;

    public DataOperationPurgeController(DataOperationPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @PostMapping("/preview")
    public ApiResponse<DataOperationPurgeDtos.PreviewResponse> preview(@RequestBody(required = false) DataOperationPurgeDtos.CreateJobRequest request,
                                                                       @RequestParam(required = false) String scopeType) {
        String scope = request != null && request.scopeType() != null ? request.scopeType() : scopeType;
        return ApiResponse.ok(purgeService.preview(scope));
    }

    @PostMapping("/jobs")
    public ApiResponse<DataOperationPurgeDtos.JobResponse> createJob(@RequestBody DataOperationPurgeDtos.CreateJobRequest request,
                                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(purgeService.createHardDeleteJob(request, principal));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<DataOperationPurgeDtos.JobResponse> getJob(@PathVariable Long jobId) {
        return ApiResponse.ok(purgeService.getJob(jobId));
    }

    @PostMapping("/jobs/{jobId}/retry-minio")
    public ApiResponse<DataOperationPurgeDtos.JobResponse> retryMinio(@PathVariable Long jobId,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(purgeService.retryMinio(jobId, principal));
    }
}

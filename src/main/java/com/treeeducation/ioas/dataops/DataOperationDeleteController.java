package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationDeleteController {
    private final DataOperationDeleteService deleteService;

    public DataOperationDeleteController(DataOperationDeleteService deleteService) {
        this.deleteService = deleteService;
    }

    @DeleteMapping("/contents/{contentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> deleteContent(@PathVariable Long contentId) {
        return ApiResponse.ok(deleteService.deleteContent(contentId));
    }

    @DeleteMapping("/platform-topics/{topicId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> deletePlatformTopic(@PathVariable Long topicId) {
        return ApiResponse.ok(deleteService.deletePlatformTopic(topicId));
    }

    @DeleteMapping("/packages/{packageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> deletePackage(@PathVariable Long packageId) {
        return ApiResponse.ok(deleteService.deletePackage(packageId));
    }
}

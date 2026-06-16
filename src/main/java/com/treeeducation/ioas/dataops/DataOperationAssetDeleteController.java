package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/data-ops/assets")
public class DataOperationAssetDeleteController {
    private final DataOperationDeleteService deleteService;

    public DataOperationAssetDeleteController(DataOperationDeleteService deleteService) {
        this.deleteService = deleteService;
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    @Transactional
    public ApiResponse<Map<String, Object>> deleteAsset(@PathVariable Long assetId,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> result = new LinkedHashMap<>(deleteService.deleteAsset(assetId));
        result.put("assetIds", List.of(assetId));
        result.put("operator", currentUserName(principal));
        return ApiResponse.ok(result);
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    @Transactional
    public ApiResponse<Map<String, Object>> batchDeleteAssets(@RequestBody BatchDeleteAssetRequest request,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        List<Long> assetIds = cleanIds(request == null ? null : request.assetIds());
        if (assetIds.isEmpty()) throw BusinessException.badRequest("请选择要删除的图片");
        int deletedAssets = 0;
        for (Long assetId : assetIds) {
            Map<String, Object> result = deleteService.deleteAsset(assetId);
            Object deleted = result.get("deletedAssets");
            if (deleted instanceof Number number) deletedAssets += number.intValue();
        }
        return ApiResponse.ok(Map.of(
                "scope", "ASSET_BATCH",
                "deletedAssets", deletedAssets,
                "assetIds", assetIds,
                "operator", currentUserName(principal)
        ));
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String currentUserName(UserPrincipal principal) {
        return principal == null ? "system" : principal.userName();
    }

    public record BatchDeleteAssetRequest(List<Long> assetIds) {}
}

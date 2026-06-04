package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/data-ops/assets")
public class DataOperationAssetDeleteController {
    private final JdbcTemplate jdbc;
    private final DataOperationAssetStorageService storageService;

    public DataOperationAssetDeleteController(JdbcTemplate jdbc, DataOperationAssetStorageService storageService) {
        this.jdbc = jdbc;
        this.storageService = storageService;
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    @Transactional
    public ApiResponse<Map<String, Object>> deleteAsset(@PathVariable Long assetId,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        DeleteResult result = deleteOne(assetId);
        return ApiResponse.ok(Map.of(
                "deleted", result.deletedCount(),
                "assetIds", List.of(assetId),
                "operator", currentUserName(principal)
        ));
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    @Transactional
    public ApiResponse<Map<String, Object>> batchDeleteAssets(@RequestBody BatchDeleteAssetRequest request,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        List<Long> assetIds = cleanIds(request == null ? null : request.assetIds());
        if (assetIds.isEmpty()) throw BusinessException.badRequest("请选择要删除的图片");
        int deleted = 0;
        for (Long assetId : assetIds) {
            deleted += deleteOne(assetId).deletedCount();
        }
        return ApiResponse.ok(Map.of(
                "deleted", deleted,
                "assetIds", assetIds,
                "operator", currentUserName(principal)
        ));
    }

    private DeleteResult deleteOne(Long assetId) {
        if (assetId == null) throw BusinessException.badRequest("图片ID不能为空");
        Map<String, Object> asset = queryOne("select * from data_operation_asset where id = ?", assetId);
        String bucketName = objectToString(asset.get("bucket_name"));
        String objectKey = objectToString(asset.get("object_key"));
        String assetType = objectToString(asset.get("asset_type"));
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        Long contentId = numberToLong(asset.get("content_id"));

        storageService.delete(bucketName, objectKey);

        jdbc.update("delete from data_operation_asset where id = ?", assetId);

        if ("COVER".equalsIgnoreCase(assetType) && topicId != null) {
            jdbc.update("""
                    update data_operation_platform_topic
                    set cover_asset_id = null,
                        cover_image_url = null,
                        ocr_status = 'pending',
                        updated_at = current_timestamp(6)
                    where id = ?
                    """, topicId);
        }
        if ("DATA_SCREENSHOT".equalsIgnoreCase(assetType) && contentId != null) {
            jdbc.update("""
                    update data_operation_content
                    set screenshot_count = greatest(screenshot_count - 1, 0),
                        updated_at = current_timestamp(6)
                    where id = ?
                    """, contentId);
        }
        return new DeleteResult(1);
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("图片不存在或已删除");
        return new LinkedHashMap<>(rows.get(0));
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String currentUserName(UserPrincipal principal) {
        return principal == null ? "system" : principal.userName();
    }

    public record BatchDeleteAssetRequest(List<Long> assetIds) {}
    private record DeleteResult(int deletedCount) {}
}

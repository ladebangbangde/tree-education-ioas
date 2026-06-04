package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/admin")
public class DataOperationAdminPurgeController {
    private static final String CONFIRM_CODE = "PURGE_DATA_OPERATION";

    private final JdbcTemplate jdbc;
    private final DataOperationAssetStorageService storageService;

    public DataOperationAdminPurgeController(JdbcTemplate jdbc, DataOperationAssetStorageService storageService) {
        this.jdbc = jdbc;
        this.storageService = storageService;
    }

    @PostMapping("/purge-all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Map<String, Object>> purgeAll(@RequestBody PurgeRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        if (request == null || !CONFIRM_CODE.equals(request.confirmCode())) {
            throw BusinessException.badRequest("危险操作确认码错误");
        }

        List<Map<String, Object>> assets = jdbc.queryForList("select id, bucket_name, object_key from data_operation_asset order by id asc");
        int fileDeleted = 0;
        int fileFailed = 0;
        for (Map<String, Object> asset : assets) {
            try {
                storageService.delete(objectToString(asset.get("bucket_name")), objectToString(asset.get("object_key")));
                fileDeleted++;
            } catch (RuntimeException ex) {
                fileFailed++;
            }
        }

        int assetRows = jdbc.update("delete from data_operation_asset");
        int contentRows = jdbc.update("delete from data_operation_content");
        int topicRows = jdbc.update("delete from data_operation_platform_topic");
        int reportRows = jdbc.update("delete from data_operation_daily_report");
        int packageRows = jdbc.update("delete from data_operation_topic_package");
        int taskRows = jdbc.update("delete from ioas_task where type in ('DATA_COVER_UPLOAD','DATA_SCREENSHOT_UPLOAD','DATA_DAILY_REPORT','DATA_RECOGNITION_JOB')");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operator", principal == null ? "system" : principal.userName());
        result.put("assetFilesDeleted", fileDeleted);
        result.put("assetFilesFailed", fileFailed);
        result.put("assetRowsDeleted", assetRows);
        result.put("contentRowsDeleted", contentRows);
        result.put("topicRowsDeleted", topicRows);
        result.put("reportRowsDeleted", reportRows);
        result.put("packageRowsDeleted", packageRows);
        result.put("taskRowsDeleted", taskRows);
        return ApiResponse.ok(result);
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record PurgeRequest(String confirmCode) {}
}

package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationPackageMaintenanceController {
    private final JdbcTemplate jdbc;
    private final DataOperationAssetStorageService storageService;

    public DataOperationPackageMaintenanceController(JdbcTemplate jdbc, DataOperationAssetStorageService storageService) {
        this.jdbc = jdbc;
        this.storageService = storageService;
    }

    @DeleteMapping("/packages/{packageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    @Transactional
    public ApiResponse<Map<String, Object>> deletePackage(@PathVariable Long packageId) {
        Integer exists = jdbc.queryForObject("select count(*) from data_operation_topic_package where id = ?", Integer.class, packageId);
        if (exists == null || exists == 0) throw BusinessException.notFound("主题包不存在");

        List<Map<String, Object>> assets = jdbc.queryForList("select id, bucket_name, object_key from data_operation_asset where package_id = ?", packageId);
        for (Map<String, Object> asset : assets) {
            storageService.delete(stringValue(asset.get("bucket_name")), stringValue(asset.get("object_key")));
        }

        safeUpdate("delete from data_operation_metric_value where topic_package_id = ?", packageId);
        safeUpdate("delete from data_operation_video where topic_package_id = ?", packageId);
        safeUpdate("delete from data_operation_account where topic_package_id = ?", packageId);
        safeUpdate("delete from data_operation_asset where package_id = ?", packageId);
        safeUpdate("delete from data_operation_content where package_id = ?", packageId);
        safeUpdate("delete from data_operation_platform_topic where package_id = ?", packageId);
        safeUpdate("delete from data_operation_daily_report where id in (select id from data_operation_daily_report where 1 = 0)");
        safeUpdate("delete from task where biz_type like 'DATA_%' and biz_id = ?", packageId);
        jdbc.update("delete from data_operation_topic_package where id = ?", packageId);

        return ApiResponse.ok(Map.of("packageId", packageId, "deletedAssets", assets.size(), "status", "deleted"));
    }

    private void safeUpdate(String sql, Object... args) {
        try { jdbc.update(sql, args); } catch (RuntimeException ignored) {}
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

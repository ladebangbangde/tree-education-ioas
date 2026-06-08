package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/platform-topics")
public class DataOperationAccountConfirmController {
    private final JdbcTemplate jdbc;
    private final DataOperationVideoHierarchyService hierarchyService;

    public DataOperationAccountConfirmController(JdbcTemplate jdbc, DataOperationVideoHierarchyService hierarchyService) {
        this.jdbc = jdbc;
        this.hierarchyService = hierarchyService;
    }

    @PostMapping("/{topicId}/account/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','CONSULTANT')")
    public ApiResponse<Map<String, Object>> confirmAccount(@PathVariable Long topicId, @RequestBody AccountConfirmRequest request) {
        ensureColumns();
        String accountName = trimToNull(request.accountName());
        String platformUserId = trimToNull(request.platformUserId());
        if (accountName == null || platformUserId == null) {
            throw new IllegalArgumentException("账号名称和平台账号ID不能为空");
        }
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        String platformCode = stringValue(topic.get("platform_code"));
        Long packageId = numberToLong(topic.get("package_id"));
        Long accountId = findPackageAccount(packageId, platformCode, platformUserId);
        if (accountId == null) {
            accountId = hierarchyService.upsertAccountFromCover(topicId, platformCode, accountName, platformUserId);
        } else {
            jdbc.update("""
                    update data_operation_account
                    set account_name = ?, recognition_status = 'SUCCESS', updated_at = current_timestamp(6)
                    where id = ?
                    """, accountName, accountId);
            attachTopicCoverToAccount(topic, accountId);
        }
        reuseAccountCoverForTopic(topicId, accountId);
        jdbc.update("""
                update data_operation_platform_topic
                set ocr_status = 'success',
                    ocr_account_name = ?,
                    ocr_platform_user_id = ?,
                    account_confirmed_flag = 1,
                    account_confirmed_at = current_timestamp(6),
                    confirmed_account_id = ?,
                    ocr_fail_reason = null,
                    recognized_at = ?,
                    updated_at = current_timestamp(6)
                where id = ?
                """, accountName, platformUserId, accountId, LocalDateTime.now(), topicId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("accountId", accountId);
        result.put("accountName", accountName);
        result.put("platformUserId", platformUserId);
        result.put("confirmed", true);
        result.put("nextStep", "CHOOSE_CONTENT_TYPE");
        result.put("status", "SUCCESS");
        return ApiResponse.ok(result);
    }

    private Long findPackageAccount(Long packageId, String platformCode, String platformUserId) {
        if (packageId == null || platformCode == null || platformUserId == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id
                from data_operation_account
                where topic_package_id = ? and platform_code = ? and platform_user_id = ?
                order by id asc
                limit 1
                """, packageId, platformCode, platformUserId);
        if (rows.isEmpty()) return null;
        return numberToLong(rows.get(0).get("id"));
    }

    private void attachTopicCoverToAccount(Map<String, Object> topic, Long accountId) {
        Long coverAssetId = numberToLong(topic.get("cover_asset_id"));
        String coverUrl = stringValue(topic.get("cover_image_url"));
        if (coverAssetId == null || accountId == null) return;
        jdbc.update("""
                update data_operation_account
                set cover_asset_id = ?, cover_image_url = ?, updated_at = current_timestamp(6)
                where id = ?
                """, coverAssetId, coverUrl, accountId);
        jdbc.update("update data_operation_asset set account_id = ? where id = ?", accountId, coverAssetId);
    }

    private void reuseAccountCoverForTopic(Long topicId, Long accountId) {
        if (topicId == null || accountId == null) return;
        List<Map<String, Object>> rows = jdbc.queryForList("select cover_asset_id, cover_image_url from data_operation_account where id = ?", accountId);
        if (rows.isEmpty()) return;
        Long coverAssetId = numberToLong(rows.get(0).get("cover_asset_id"));
        String coverUrl = stringValue(rows.get(0).get("cover_image_url"));
        if (coverAssetId == null) return;
        jdbc.update("""
                update data_operation_platform_topic
                set cover_asset_id = coalesce(cover_asset_id, ?),
                    cover_image_url = coalesce(cover_image_url, ?),
                    updated_at = current_timestamp(6)
                where id = ?
                """, coverAssetId, coverUrl, topicId);
    }

    private void ensureColumns() {
        ensureColumn("data_operation_platform_topic", "account_confirmed_flag", "alter table data_operation_platform_topic add column account_confirmed_flag tinyint(1) not null default 0 after ocr_platform_user_id");
        ensureColumn("data_operation_platform_topic", "account_confirmed_at", "alter table data_operation_platform_topic add column account_confirmed_at datetime(6) null after account_confirmed_flag");
        ensureColumn("data_operation_platform_topic", "confirmed_account_id", "alter table data_operation_platform_topic add column confirmed_account_id bigint null after account_confirmed_at");
        ensureColumn("data_operation_account", "cover_asset_id", "alter table data_operation_account add column cover_asset_id bigint null after platform_user_id");
        ensureColumn("data_operation_account", "cover_image_url", "alter table data_operation_account add column cover_image_url varchar(500) null after cover_asset_id");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", Integer.class, table, column);
            if (count != null && count == 0) jdbc.execute(ddl);
        } catch (RuntimeException ignored) {}
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new IllegalArgumentException("平台子主题不存在");
        return rows.get(0);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record AccountConfirmRequest(String accountName, String platformUserId) {}
}

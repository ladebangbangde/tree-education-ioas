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
        String accountName = trimToNull(request.accountName());
        String platformUserId = trimToNull(request.platformUserId());
        if (accountName == null || platformUserId == null) {
            throw new IllegalArgumentException("账号名称和平台账号ID不能为空");
        }
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        String platformCode = stringValue(topic.get("platform_code"));
        jdbc.update("""
                update data_operation_platform_topic
                set ocr_status = 'success',
                    ocr_account_name = ?,
                    ocr_platform_user_id = ?,
                    ocr_fail_reason = null,
                    recognized_at = ?,
                    updated_at = current_timestamp(6)
                where id = ?
                """, accountName, platformUserId, LocalDateTime.now(), topicId);
        Long accountId = hierarchyService.upsertAccountFromCover(topicId, platformCode, accountName, platformUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("accountId", accountId);
        result.put("accountName", accountName);
        result.put("platformUserId", platformUserId);
        result.put("status", "SUCCESS");
        return ApiResponse.ok(result);
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record AccountConfirmRequest(String accountName, String platformUserId) {}
}

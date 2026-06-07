package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationAccountController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public DataOperationAccountController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @PostMapping("/platform-topics/{topicId}/account/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> confirmAccount(@PathVariable Long topicId,
                                                           @RequestBody ConfirmAccountRequest request) {
        if (request == null) throw BusinessException.badRequest("请求不能为空");
        String accountName = clean(request.accountName());
        String platformUserId = clean(request.platformUserId());
        if (accountName == null) throw BusinessException.badRequest("请确认账号名称");
        if (platformUserId == null) throw BusinessException.badRequest("请确认平台账号ID");

        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = numberToLong(topic.get("package_id"));
        String platformCode = normalizePlatform(String.valueOf(topic.get("platform_code")));

        jdbc.update("""
                update data_operation_platform_topic
                set ocr_account_name = ?,
                    ocr_platform_user_id = ?,
                    account_confirmed_flag = 1,
                    ocr_status = coalesce(nullif(ocr_status, ''), 'success'),
                    updated_at = current_timestamp(6)
                where id = ?
                """, accountName, platformUserId, topicId);

        upsertAccount(packageId, topicId, platformCode, accountName, platformUserId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("topicId", topicId);
        response.put("accountName", accountName);
        response.put("platformUserId", platformUserId);
        response.put("status", "CONFIRMED");
        return ApiResponse.ok(response);
    }

    private void upsertAccount(Long packageId, Long topicId, String platformCode, String accountName, String platformUserId) {
        try {
            namedJdbc.update("""
                    insert into data_operation_account
                    (topic_package_id, platform_topic_id, platform_code, account_name, platform_user_id, recognition_status, source)
                    values (:packageId, :topicId, :platformCode, :accountName, :platformUserId, 'SUCCESS', 'MANUAL')
                    on duplicate key update account_name = values(account_name), recognition_status = 'SUCCESS', source = 'MANUAL', updated_at = current_timestamp(6)
                    """, new MapSqlParameterSource()
                    .addValue("packageId", packageId)
                    .addValue("topicId", topicId)
                    .addValue("platformCode", platformCode)
                    .addValue("accountName", accountName)
                    .addValue("platformUserId", platformUserId));
        } catch (RuntimeException ignored) {
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String normalizePlatform(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("DOUYIN".equals(code) || "抖音".equals(code)) return "DOUYIN";
        if ("XIAOHONGSHU".equals(code) || "XHS".equals(code) || "小红书".equals(code)) return "XIAOHONGSHU";
        if ("WECHAT_CHANNEL".equals(code) || "VIDEO_ACCOUNT".equals(code) || "视频号".equals(code)) return "WECHAT_CHANNEL";
        return code.isBlank() ? "DOUYIN" : code;
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }

    public record ConfirmAccountRequest(String accountName, String platformUserId) {}
}

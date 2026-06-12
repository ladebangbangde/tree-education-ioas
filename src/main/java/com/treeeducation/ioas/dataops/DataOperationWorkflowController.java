package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationWorkflowController {
    private final JdbcTemplate jdbc;
    private final DataOperationVideoHierarchyService hierarchyService;

    public DataOperationWorkflowController(JdbcTemplate jdbc, DataOperationVideoHierarchyService hierarchyService) {
        this.jdbc = jdbc;
        this.hierarchyService = hierarchyService;
    }

    @PostConstruct
    public void initSchema() {
        ensureColumn("data_operation_platform_topic", "content_type", "alter table data_operation_platform_topic add column content_type varchar(32) null");
        ensureColumn("data_operation_platform_topic", "account_confirmed_flag", "alter table data_operation_platform_topic add column account_confirmed_flag tinyint(1) not null default 0");
        ensureColumn("data_operation_platform_topic", "confirmed_account_id", "alter table data_operation_platform_topic add column confirmed_account_id bigint null");
        ensureColumn("data_operation_content", "content_type", "alter table data_operation_content add column content_type varchar(32) null");
    }

    @PostMapping("/platform-topics/{topicId}/account/confirm-workflow")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> confirmAccountWorkflow(@PathVariable Long topicId, @RequestBody ConfirmAccountRequest request) {
        if (request == null || blank(request.accountName()) || blank(request.platformUserId())) throw BusinessException.badRequest("请确认账号名称和平台账号ID");
        Map<String, Object> topic = one("select * from data_operation_platform_topic where id = ?", topicId);
        Long accountId = hierarchyService.upsertAccountFromCover(topicId, str(topic.get("platform_code")), request.accountName().trim(), request.platformUserId().trim());
        jdbc.update("update data_operation_platform_topic set ocr_account_name = ?, ocr_platform_user_id = ?, account_confirmed_flag = 1, confirmed_account_id = ?, updated_at = current_timestamp(6) where id = ?", request.accountName().trim(), request.platformUserId().trim(), accountId, topicId);
        return ApiResponse.ok(Map.of("topicId", topicId, "accountId", accountId, "accountName", request.accountName().trim(), "platformUserId", request.platformUserId().trim(), "status", "CONFIRMED"));
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", Integer.class, table, column);
        if (count != null && count == 0) jdbc.execute(ddl);
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String str(Object value) { return value == null ? null : String.valueOf(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record ConfirmAccountRequest(String accountName, String platformUserId) {}
}

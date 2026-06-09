package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationMetricEditController {
    private final JdbcTemplate jdbc;

    public DataOperationMetricEditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PatchMapping("/metrics/{metricId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','OPERATOR')")
    public ApiResponse<Map<String, Object>> updateMetric(@PathVariable Long metricId,
                                                         @RequestBody MetricEditRequest request) {
        ensureColumns();
        Map<String, Object> old = queryOne("select * from data_operation_metric_value where id = ?", metricId);
        if (old.isEmpty()) throw BusinessException.notFound("数据行不存在");
        String oldValue = stringValue(old.get("metric_value"));
        String newValue = request == null ? null : trimToNull(request.metricValue());
        if (newValue == null) throw BusinessException.badRequest("请填写修改后的数据");
        boolean changed = !newValue.equals(oldValue == null ? "" : oldValue);
        if (changed && !Boolean.TRUE.equals(request.confirmed())) {
            throw BusinessException.badRequest("修改值与识别值不同，请确认后再保存");
        }
        jdbc.update("""
                update data_operation_metric_value
                set original_metric_value = coalesce(original_metric_value, metric_value),
                    metric_value = ?,
                    metric_numeric = ?,
                    recognition_status = 'SUCCESS',
                    source = case when ? then 'MANUAL' else coalesce(source, 'OCR') end,
                    fail_reason = null,
                    updated_at = current_timestamp(6)
                where id = ?
                """, newValue, metricNumeric(newValue), changed, metricId);
        return ApiResponse.ok(queryOne("select * from data_operation_metric_value where id = ?", metricId));
    }

    private void ensureColumns() {
        ensureColumn("data_operation_metric_value", "original_metric_value", "alter table data_operation_metric_value add column original_metric_value varchar(128) null after metric_value");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", Integer.class, table, column);
            if (count != null && count == 0) jdbc.execute(ddl);
        } catch (RuntimeException ignored) {}
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : new LinkedHashMap<>(rows.get(0));
    }

    private BigDecimal metricNumeric(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace("%", "").replace(",", "").replace("+", "").trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record MetricEditRequest(String metricValue, Boolean confirmed) {}
}

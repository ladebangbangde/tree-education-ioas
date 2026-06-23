package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/platform-topics/metrics")
public class DataOperationMetricManualEditController {
    private final JdbcTemplate jdbc;

    public DataOperationMetricManualEditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PatchMapping("/{metricValueId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> updateMetricValue(@PathVariable Long metricValueId,
                                                              @RequestBody(required = false) ManualMetricUpdateRequest request) {
        if (metricValueId == null) {
            throw BusinessException.badRequest("指标ID不能为空");
        }
        String value = normalizeValue(request == null ? null : request.metricValue());
        String status = value == null ? "PENDING" : "SUCCESS";
        int updated = jdbc.update("""
                update data_operation_metric_value
                set metric_value = ?,
                    metric_numeric = ?,
                    recognition_status = ?,
                    source = 'MANUAL',
                    fail_reason = null,
                    recognized_at = current_timestamp(6),
                    updated_at = current_timestamp(6)
                where id = ?
                """, value, metricNumeric(value), status, metricValueId);
        if (updated == 0) {
            throw BusinessException.notFound("识别指标不存在");
        }
        return ApiResponse.ok(queryMetric(metricValueId));
    }

    private Map<String, Object> queryMetric(Long metricValueId) {
        return new LinkedHashMap<>(jdbc.queryForMap("""
                select v.id,
                       v.platform_topic_id as platformTopicId,
                       v.account_id as accountId,
                       a.account_name as accountName,
                       a.platform_user_id as platformUserId,
                       v.video_id as videoId,
                       vid.video_title as videoTitle,
                       v.content_id as contentId,
                       c.content_title as contentTitle,
                       v.asset_id as assetId,
                       v.platform_code as platformCode,
                       v.content_type as contentType,
                       v.metric_group as metricGroup,
                       v.metric_key as metricKey,
                       v.metric_label as metricLabel,
                       v.metric_value as metricValue,
                       v.metric_numeric as metricNumeric,
                       v.metric_unit as metricUnit,
                       v.recognition_status as recognitionStatus,
                       v.confidence,
                       v.source,
                       v.fail_reason as failReason,
                       v.recognized_at as recognizedAt,
                       d.display_order as displayOrder
                from data_operation_metric_value v
                left join data_operation_metric_definition d
                  on d.platform_code = v.platform_code
                 and d.content_type = v.content_type
                 and d.metric_group = v.metric_group
                 and d.metric_key = v.metric_key
                left join data_operation_account a on a.id = v.account_id
                left join data_operation_video vid on vid.id = v.video_id
                left join data_operation_content c on c.id = v.content_id
                where v.id = ?
                """, metricValueId));
    }

    private String normalizeValue(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text) || "-".equals(text)) return null;
        return text;
    }

    private BigDecimal metricNumeric(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replace("%", "")
                .replace(",", "")
                .replace("万", "")
                .replace("w", "")
                .replace("W", "")
                .trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record ManualMetricUpdateRequest(String metricValue) {}
}

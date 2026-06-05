package com.treeeducation.ioas.dataops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DataOperationMetricService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DataOperationMetricService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                create table if not exists data_operation_metric_definition (
                    id bigint primary key auto_increment,
                    platform_code varchar(32) not null,
                    content_type varchar(32) not null,
                    metric_group varchar(64) not null,
                    metric_key varchar(128) not null,
                    metric_label varchar(128) not null,
                    metric_unit varchar(32) null,
                    display_order int not null default 0,
                    required_flag tinyint(1) not null default 0,
                    enabled tinyint(1) not null default 1,
                    created_at datetime(6) not null default current_timestamp(6),
                    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
                    unique key uk_metric_def (platform_code, content_type, metric_group, metric_key),
                    index idx_metric_def_group (platform_code, content_type, metric_group, enabled)
                )
                """);
        jdbc.execute("""
                create table if not exists data_operation_metric_value (
                    id bigint primary key auto_increment,
                    topic_package_id bigint not null,
                    platform_topic_id bigint not null,
                    content_id bigint null,
                    asset_id bigint null,
                    platform_code varchar(32) not null,
                    content_type varchar(32) not null,
                    metric_group varchar(64) not null,
                    metric_key varchar(128) not null,
                    metric_label varchar(128) not null,
                    metric_value varchar(128) null,
                    metric_numeric decimal(18,4) null,
                    metric_unit varchar(32) null,
                    recognition_status varchar(32) not null default 'PENDING',
                    confidence decimal(6,4) null,
                    source varchar(64) null,
                    fail_reason varchar(500) null,
                    recognized_at datetime(6) null,
                    created_at datetime(6) not null default current_timestamp(6),
                    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
                    unique key uk_metric_asset_key (asset_id, metric_group, metric_key),
                    index idx_topic_metric (platform_topic_id, metric_group, display_order_placeholder),
                    index idx_package_metric (topic_package_id, metric_key),
                    index idx_status (recognition_status)
                )
                """.replace("display_order_placeholder", "metric_key"));
        ensureColumn("data_operation_platform_topic", "ocr_platform_user_id", "alter table data_operation_platform_topic add column ocr_platform_user_id varchar(128) null comment '平台账号ID/抖音号/视频号' after ocr_account_name");
        ensureColumn("data_operation_platform_topic", "ocr_content_title", "alter table data_operation_platform_topic add column ocr_content_title varchar(255) null comment '识别出的作品标题' after ocr_platform_user_id");
        ensureColumn("data_operation_platform_topic", "ocr_fail_reason", "alter table data_operation_platform_topic add column ocr_fail_reason varchar(500) null comment '封面识别失败原因' after ocr_status");
        ensureColumn("data_operation_platform_topic", "recognized_at", "alter table data_operation_platform_topic add column recognized_at datetime(6) null comment '封面识别时间' after ocr_payload_json");
        seedDefinitions("DOUYIN", "IMAGE_TEXT");
        seedDefinitions("DOUYIN", "VIDEO");
    }

    @Transactional
    public void upsertAssetMetrics(Map<String, Object> asset, Map<String, Object> extractedPayload, String platformCode, String contentType) {
        Long assetId = numberToLong(asset.get("id"));
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        Long contentId = numberToLong(asset.get("content_id"));
        if (assetId == null || topicId == null) return;
        Long packageId = numberToLong(asset.get("package_id"));
        if (packageId == null && contentId != null) {
            packageId = queryLong("select package_id from data_operation_content where id = ?", contentId);
        }
        if (packageId == null) {
            packageId = queryLong("select package_id from data_operation_platform_topic where id = ?", topicId);
        }
        if (packageId == null) return;
        String group = normalizeMetricGroup(stringValue(extractedPayload.get("assetGroup")), stringValue(asset.get("asset_group")), stringValue(asset.get("object_key")));
        String effectivePlatform = nonBlank(platformCode, queryString("select platform_code from data_operation_platform_topic where id = ?", topicId), "DOUYIN");
        String effectiveContentType = nonBlank(contentType, queryString("select content_type from data_operation_platform_topic where id = ?", topicId), "IMAGE_TEXT");

        ensureMetricRows(packageId, topicId, contentId, assetId, effectivePlatform, effectiveContentType, group);

        Map<String, Object> metrics = normalizedMetricValues(extractedPayload.get("metrics"));
        if (metrics.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            MetricDefinition def = definitionByLabel(effectivePlatform, effectiveContentType, group, entry.getKey());
            if (def == null) continue;
            String value = metricValue(entry.getValue());
            jdbc.update("""
                    update data_operation_metric_value
                    set metric_value = ?,
                        metric_numeric = ?,
                        recognition_status = 'SUCCESS',
                        source = 'OCR',
                        fail_reason = null,
                        recognized_at = ?,
                        updated_at = current_timestamp(6)
                    where asset_id = ? and metric_group = ? and metric_key = ?
                    """, value, metricNumeric(value), now, assetId, group, def.metricKey());
        }
    }

    public List<Map<String, Object>> listTopicMetrics(Long topicId) {
        if (topicId == null) return List.of();
        return jdbc.queryForList("""
                select v.id,
                       v.platform_topic_id as platformTopicId,
                       v.content_id as contentId,
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
                where v.platform_topic_id = ?
                order by field(v.metric_group, 'OVERVIEW', 'OVERVIEW_CHART', 'FLOW_ANALYSIS'), d.display_order, v.id
                """, topicId);
    }

    public Map<String, Object> computeTopicStatus(Long topicId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (topicId == null) {
            result.put("status", "NOT_STARTED");
            result.put("label", "未开始");
            result.put("color", "default");
            return result;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select recognition_status, metric_value from data_operation_metric_value where platform_topic_id = ?", topicId);
        long total = rows.size();
        long success = rows.stream().filter(row -> "SUCCESS".equalsIgnoreCase(stringValue(row.get("recognition_status")))).count();
        long failed = rows.stream().filter(row -> "FAILED".equalsIgnoreCase(stringValue(row.get("recognition_status")))).count();
        long nullCount = rows.stream().filter(row -> row.get("metric_value") == null).count();
        String status;
        String label;
        String color;
        if (total == 0) {
            status = "DATA_EMPTY";
            label = "未上传";
            color = "default";
        } else if (failed > 0 && success == 0) {
            status = "DATA_FAILED";
            label = "识别失败";
            color = "red";
        } else if (success == total && nullCount == 0) {
            status = "DATA_SUCCESS";
            label = "已完成";
            color = "green";
        } else if (success > 0) {
            status = "DATA_PARTIAL";
            label = "部分完成";
            color = "orange";
        } else {
            status = "DATA_PENDING";
            label = "排队中";
            color = "blue";
        }
        result.put("status", status);
        result.put("label", label);
        result.put("color", color);
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("missing", nullCount);
        return result;
    }

    private void ensureMetricRows(Long packageId, Long topicId, Long contentId, Long assetId, String platformCode, String contentType, String group) {
        List<Map<String, Object>> defs = jdbc.queryForList("""
                select metric_key, metric_label, metric_unit
                from data_operation_metric_definition
                where platform_code = ? and content_type = ? and metric_group = ? and enabled = 1
                order by display_order asc
                """, platformCode, contentType, group);
        for (Map<String, Object> def : defs) {
            jdbc.update("""
                    insert into data_operation_metric_value
                        (topic_package_id, platform_topic_id, content_id, asset_id, platform_code, content_type,
                         metric_group, metric_key, metric_label, metric_unit, recognition_status, source)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'OCR')
                    on duplicate key update
                        topic_package_id = values(topic_package_id),
                        platform_topic_id = values(platform_topic_id),
                        content_id = values(content_id),
                        platform_code = values(platform_code),
                        content_type = values(content_type),
                        metric_label = values(metric_label),
                        metric_unit = values(metric_unit),
                        updated_at = current_timestamp(6)
                    """, packageId, topicId, contentId, assetId, platformCode, contentType, group,
                    def.get("metric_key"), def.get("metric_label"), def.get("metric_unit"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizedMetricValues(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String label = String.valueOf(entry.getKey());
            Object item = entry.getValue();
            if (item instanceof Map<?, ?> itemMap) {
                Object metricValue = itemMap.get("value");
                if (metricValue != null) result.put(label, metricValue);
            } else if (item != null) {
                result.put(label, item);
            }
        }
        return result;
    }

    private MetricDefinition definitionByLabel(String platformCode, String contentType, String group, String label) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select metric_key, metric_label, metric_unit
                from data_operation_metric_definition
                where platform_code = ? and content_type = ? and metric_group = ? and enabled = 1
                """, platformCode, contentType, group);
        String normalized = normalizeLabel(label);
        for (Map<String, Object> row : rows) {
            if (normalizeLabel(stringValue(row.get("metric_label"))).equals(normalized)) {
                return new MetricDefinition(stringValue(row.get("metric_key")), stringValue(row.get("metric_label")), stringValue(row.get("metric_unit")));
            }
        }
        return null;
    }

    private String normalizeMetricGroup(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String marker = value.toUpperCase(Locale.ROOT);
            if (marker.contains("FLOW_ANALYSIS")) return "FLOW_ANALYSIS";
            if (marker.contains("OVERVIEW_CHART")) return "OVERVIEW_CHART";
            if (marker.contains("DOUYIN_FLOW_ANALYSIS")) return "FLOW_ANALYSIS";
            if (marker.contains("DOUYIN_OVERVIEW_CHART")) return "OVERVIEW_CHART";
        }
        return "OVERVIEW";
    }

    private String metricValue(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?> || value.getClass().isArray()) return toJson(value);
        return String.valueOf(value).trim();
    }

    private BigDecimal metricNumeric(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replace("%", "").replace(",", "").replace("万", "").replace("w", "").replace("W", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void seedDefinitions(String platform, String contentType) {
        seed(platform, contentType, "OVERVIEW", "view_count", "播放量", "次", 10, true);
        seed(platform, contentType, "OVERVIEW", "like_count", "点赞量", "次", 20, true);
        seed(platform, contentType, "OVERVIEW", "comment_count", "评论量", "次", 30, true);
        seed(platform, contentType, "OVERVIEW", "share_count", "分享量", "次", 40, false);
        seed(platform, contentType, "OVERVIEW", "favorite_count", "收藏量", "次", 50, true);
        seed(platform, contentType, "OVERVIEW", "completion_rate", "完播率", "%", 60, true);
        seed(platform, contentType, "OVERVIEW", "skip_rate", "划走率", "%", 70, false);
        seed(platform, contentType, "OVERVIEW_CHART", "trend_values", "趋势曲线数值", null, 10, false);
        seed(platform, contentType, "OVERVIEW_CHART", "follower_gain", "涨粉量", "人", 20, true);
        seed(platform, contentType, "OVERVIEW_CHART", "follower_loss", "脱粉量", "人", 30, false);
        seed(platform, contentType, "OVERVIEW_CHART", "fan_view_ratio", "粉丝播放占比", "%", 40, true);
        seed(platform, contentType, "OVERVIEW_CHART", "hourly_chart_values", "小时图表数据", null, 50, false);
        seed(platform, contentType, "OVERVIEW_CHART", "daily_chart_values", "每日图表数据", null, 60, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "traffic_uplift_rate", "播放量较往期上涨", "%", 10, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "view_count", "播放量", "次", 20, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "cover_click_rate", "封面点击率", "%", 30, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "copy_expand_rate", "文案展开率", "%", 40, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "skip_rate", "划走率", "%", 50, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "avg_image_view_count", "平均浏览图片数", "张", 60, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "copy_finish_rate", "文案完读率", "%", 70, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "comment_enter_rate", "评论进入率", "%", 80, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "comment_rate", "评论率", "%", 90, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "share_rate", "分享率", "%", 100, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "completion_rate", "完播率", "%", 110, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "five_second_completion_rate", "5s完播率", "%", 120, true);
    }

    private void seed(String platform, String contentType, String group, String key, String label, String unit, int order, boolean required) {
        jdbc.update("""
                insert into data_operation_metric_definition
                    (platform_code, content_type, metric_group, metric_key, metric_label, metric_unit, display_order, required_flag, enabled)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1)
                on duplicate key update metric_label = values(metric_label), metric_unit = values(metric_unit), display_order = values(display_order), required_flag = values(required_flag), enabled = 1
                """, platform, contentType, group, key, label, unit, order, required ? 1 : 0);
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbc.queryForObject("""
                    select count(*)
                    from information_schema.columns
                    where table_schema = database() and table_name = ? and column_name = ?
                    """, Integer.class, table, column);
            if (count != null && count == 0) jdbc.execute(ddl);
        } catch (RuntimeException ignore) {
            // 兼容已有库、低权限库和重复执行场景。
        }
    }

    private String queryString(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) return null;
        Object value = rows.get(0).values().stream().findFirst().orElse(null);
        return value == null ? null : String.valueOf(value);
    }

    private Long queryLong(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) return null;
        Object value = rows.get(0).values().stream().findFirst().orElse(null);
        return numberToLong(value);
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.replaceAll("[\\s:_：/\\-+>√]", "").toLowerCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String stringValue(Object value) {
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

    private record MetricDefinition(String metricKey, String metricLabel, String metricUnit) {}
}

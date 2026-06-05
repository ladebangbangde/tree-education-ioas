package com.treeeducation.ioas.dataops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DataOperationMetricStoreService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private static final Map<String, List<String>> LABELS = Map.of(
            "DOUYIN_OVERVIEW", List.of("播放量", "点赞量", "评论量", "分享量", "收藏量", "完播率", "划走率"),
            "DOUYIN_OVERVIEW_CHART", List.of("趋势曲线数值", "涨粉量", "粉丝播放占比"),
            "DOUYIN_FLOW_ANALYSIS", List.of("流量上涨", "内容吸引力", "评论率", "分享率", "完播率", "5s完播率")
    );

    public DataOperationMetricStoreService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensureTable() {
        jdbc.execute("""
                create table if not exists data_operation_metric_value (
                  id bigint primary key auto_increment,
                  package_id bigint null,
                  platform_topic_id bigint null,
                  content_id bigint null,
                  asset_id bigint not null,
                  asset_group varchar(64) not null,
                  metric_section varchar(64) not null,
                  metric_key varchar(96) not null,
                  metric_label varchar(96) not null,
                  metric_value text null,
                  value_type varchar(32) not null default 'string',
                  source varchar(64) null,
                  status varchar(24) not null default 'pending',
                  display_order int not null default 0,
                  created_at datetime(6) not null default current_timestamp(6),
                  updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
                  unique key uk_data_metric_asset_label (asset_id, metric_label),
                  key idx_data_metric_topic (platform_topic_id),
                  key idx_data_metric_content (content_id),
                  key idx_data_metric_group (asset_group)
                )
                """);
    }

    @Transactional
    public void replaceAssetMetrics(Map<String, Object> asset, Map<String, Object> extractedPayload) {
        Long assetId = numberToLong(asset.get("id"));
        if (assetId == null) return;
        jdbc.update("delete from data_operation_metric_value where asset_id = ?", assetId);

        String group = stringValue(extractedPayload.get("assetGroup"));
        if (group == null || group.isBlank()) group = resolveAssetGroup(asset);
        String section = sectionName(group);
        Long packageId = numberToLong(asset.get("package_id"));
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        Long contentId = numberToLong(asset.get("content_id"));
        Map<String, Object> metrics = mapValue(extractedPayload.get("metrics"));

        List<String> labels = LABELS.getOrDefault(group, LABELS.get("DOUYIN_OVERVIEW"));
        int order = 1;
        for (String label : labels) {
            Object metric = metrics.get(label);
            Object value = metricValue(metric);
            String source = metricSource(metric);
            String valueType = valueType(value);
            String status = value == null || String.valueOf(value).isBlank() ? "pending" : "success";
            jdbc.update("""
                    insert into data_operation_metric_value
                    (package_id, platform_topic_id, content_id, asset_id, asset_group, metric_section, metric_key, metric_label, metric_value, value_type, source, status, display_order)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, packageId, topicId, contentId, assetId, group, section, metricKey(label), label,
                    value == null ? null : valueToString(value), valueType, source, status, order++);
        }
    }

    public List<Map<String, Object>> topicMetricValues(Long topicId) {
        if (topicId == null) return List.of();
        return jdbc.queryForList("""
                select id, package_id, platform_topic_id, content_id, asset_id, asset_group, metric_section,
                       metric_key, metric_label, metric_value, value_type, source, status, display_order,
                       created_at, updated_at
                from data_operation_metric_value
                where platform_topic_id = ?
                order by case asset_group
                    when 'DOUYIN_OVERVIEW' then 1
                    when 'DOUYIN_OVERVIEW_CHART' then 2
                    when 'DOUYIN_FLOW_ANALYSIS' then 3
                    else 9 end,
                    display_order asc, id asc
                """, topicId);
    }

    private Object metricValue(Object metric) {
        if (metric instanceof Map<?, ?> map) return map.get("value");
        return metric;
    }

    private String metricSource(Object metric) {
        if (metric instanceof Map<?, ?> map && map.get("source") != null) return String.valueOf(map.get("source"));
        return "ocr";
    }

    private String valueType(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) return "json";
        if (value == null) return "string";
        String text = String.valueOf(value);
        if (text.endsWith("%")) return "percent";
        if (text.matches("[+\\-]?[0-9][0-9,]*(\\.[0-9]+)?")) return "number";
        return "string";
    }

    private String valueToString(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException ex) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private String resolveAssetGroup(Map<String, Object> asset) {
        String objectKey = stringValue(asset.get("object_key"));
        String marker = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
        if (marker.contains("douyin_flow_analysis")) return "DOUYIN_FLOW_ANALYSIS";
        if (marker.contains("douyin_overview_chart")) return "DOUYIN_OVERVIEW_CHART";
        return "DOUYIN_OVERVIEW";
    }

    private String sectionName(String group) {
        return switch (group) {
            case "DOUYIN_OVERVIEW_CHART" -> "总览页趋势/粉丝趋势/小时每日图表";
            case "DOUYIN_FLOW_ANALYSIS" -> "流量分析";
            default -> "总览指标";
        };
    }

    private String metricKey(String label) {
        return switch (label) {
            case "播放量" -> "view_count";
            case "点赞量" -> "like_count";
            case "评论量" -> "comment_count";
            case "分享量" -> "share_count";
            case "收藏量" -> "favorite_count";
            case "完播率" -> "completion_rate";
            case "划走率" -> "swipe_away_rate";
            case "趋势曲线数值" -> "trend_series";
            case "涨粉量" -> "follower_gain";
            case "粉丝播放占比" -> "follower_play_ratio";
            case "流量上涨" -> "traffic_uplift";
            case "内容吸引力" -> "content_attraction";
            case "评论率" -> "comment_rate";
            case "分享率" -> "share_rate";
            case "5s完播率" -> "five_second_completion_rate";
            default -> label;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

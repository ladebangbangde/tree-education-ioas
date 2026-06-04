package com.treeeducation.ioas.recognition;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataOperationMetricExtractor {
    private static final List<String> OVERVIEW_KEYS = List.of("播放量", "点赞量", "评论量", "分享量", "收藏量", "完播率", "划走率");
    private static final List<String> CHART_KEYS = List.of("涨粉量", "粉丝增量", "新增粉丝", "粉丝趋势", "小时数据", "每日数据", "趋势曲线");
    private static final List<String> FLOW_KEYS = List.of("流量上涨", "内容吸引力", "评论率", "分享率", "完播率", "5s完播率", "5S完播率");

    public Map<String, Object> extract(String assetGroup, ImageRecognitionDtos.Response response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String group = normalizeGroup(assetGroup);
        payload.put("assetGroup", group);
        payload.put("schema", schemaName(group));
        payload.put("metrics", extractMetrics(group, response));
        payload.put("rawText", rawText(response));
        payload.put("rawResult", response == null ? Map.of() : response.result());
        return payload;
    }

    private Map<String, Object> extractMetrics(String group, ImageRecognitionDtos.Response response) {
        LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
        List<String> keys = expectedKeys(group);
        Map<String, Object> result = response == null || response.result() == null ? Map.of() : response.result();
        String text = rawText(response);
        for (String key : keys) {
            String value = firstNonBlank(valueFromMap(result, key), valueFromText(text, key));
            if (value != null) metrics.put(key, metricValue(value, "ocr"));
        }
        Map<String, Object> nested = mapValue(result.get("metrics"));
        for (Map.Entry<String, Object> entry : nested.entrySet()) {
            String key = normalizeMetricName(entry.getKey());
            if (key.isBlank()) continue;
            metrics.putIfAbsent(key, metricValue(String.valueOf(entry.getValue()), "ocr.metrics"));
        }
        return metrics;
    }

    private List<String> expectedKeys(String group) {
        return switch (group) {
            case "DOUYIN_OVERVIEW_CHART" -> CHART_KEYS;
            case "DOUYIN_FLOW_ANALYSIS" -> FLOW_KEYS;
            default -> OVERVIEW_KEYS;
        };
    }

    private String schemaName(String group) {
        return switch (group) {
            case "DOUYIN_OVERVIEW_CHART" -> "总览图表数据";
            case "DOUYIN_FLOW_ANALYSIS" -> "流量分析";
            default -> "总览指标";
        };
    }

    private Map<String, Object> metricValue(String value, String source) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("value", value == null ? "" : value.trim());
        row.put("source", source);
        return row;
    }

    private String valueFromMap(Map<String, Object> result, String key) {
        if (result == null || result.isEmpty()) return null;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (sameKey(entry.getKey(), key) && entry.getValue() != null) return String.valueOf(entry.getValue());
        }
        Map<String, Object> nested = mapValue(result.get("metrics"));
        for (Map.Entry<String, Object> entry : nested.entrySet()) {
            if (sameKey(entry.getKey(), key) && entry.getValue() != null) return String.valueOf(entry.getValue());
        }
        return null;
    }

    private String valueFromText(String text, String key) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.replace('：', ':').replace("\r", "\n");
        Pattern inline = Pattern.compile(Pattern.quote(key) + "\\s*[:：]?\\s*([+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:%|万|w|W|k|K)?|[+\\-]?[0-9]+(?:\\.[0-9]+)?%?)");
        Matcher matcher = inline.matcher(normalized);
        if (matcher.find()) return matcher.group(1).replaceAll("\\s+", "");
        String[] lines = normalized.split("\\n+");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(key)) {
                String current = extractFirstNumber(lines[i].replace(key, ""));
                if (current != null) return current;
                if (i + 1 < lines.length) {
                    String next = extractFirstNumber(lines[i + 1]);
                    if (next != null) return next;
                }
            }
        }
        return null;
    }

    private String extractFirstNumber(String value) {
        if (value == null) return null;
        Matcher matcher = Pattern.compile("[+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:%|万|w|W|k|K)?").matcher(value);
        return matcher.find() ? matcher.group().replaceAll("\\s+", "") : null;
    }

    private String rawText(ImageRecognitionDtos.Response response) {
        if (response == null) return "";
        if (response.rawText() != null && !response.rawText().isBlank()) return response.rawText();
        Object rawText = response.result() == null ? null : response.result().get("rawText");
        return rawText == null ? "" : String.valueOf(rawText);
    }

    private String normalizeGroup(String value) {
        String group = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("DOUYIN_OVERVIEW_CHART".equals(group)) return group;
        if ("DOUYIN_FLOW_ANALYSIS".equals(group)) return group;
        return "DOUYIN_OVERVIEW";
    }

    private boolean sameKey(String left, String right) {
        return normalizeMetricName(left).equalsIgnoreCase(normalizeMetricName(right));
    }

    private String normalizeMetricName(String value) {
        return value == null ? "" : value.replaceAll("[\\s:_：/\\-]", "").trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) return value.trim();
        }
        return null;
    }
}

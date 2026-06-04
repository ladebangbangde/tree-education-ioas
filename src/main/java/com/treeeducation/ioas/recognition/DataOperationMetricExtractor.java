package com.treeeducation.ioas.recognition;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataOperationMetricExtractor {
    private static final Pattern METRIC_NUMBER = Pattern.compile("[+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:%|万|w|W|k|K)?");
    private static final Set<String> NOISE_TOKENS = Set.of("中国联通4G", "作品数据详情", "总览", "流量分析", "观众分析", "切换作品", "设置观测>", "新增累计", "每小时", "每天", "每小时每天", "DOUO", "DOU+", "粉丝", "流量", "作品状态正常");

    public Map<String, Object> extract(String assetGroup, ImageRecognitionDtos.Response response) {
        String group = normalizeGroup(assetGroup);
        List<String> lines = lines(rawText(response));
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetGroup", group);
        payload.put("schema", schemaName(group));
        payload.put("metrics", extractMetrics(group, response, lines));
        payload.put("rawText", rawText(response));
        payload.put("rawResult", response == null ? Map.of() : response.result());
        return payload;
    }

    private Map<String, Object> extractMetrics(String group, ImageRecognitionDtos.Response response, List<String> lines) {
        LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
        if ("DOUYIN_FLOW_ANALYSIS".equals(group)) {
            extractFlowMetrics(metrics, lines, response);
        } else if ("DOUYIN_OVERVIEW_CHART".equals(group)) {
            extractChartMetrics(metrics, lines, response);
        } else {
            extractOverviewMetrics(metrics, lines, response);
        }
        addGenericFallback(metrics, response);
        return metrics;
    }

    private void extractOverviewMetrics(LinkedHashMap<String, Object> metrics, List<String> lines, ImageRecognitionDtos.Response response) {
        addTable(metrics, lines, List.of("播放量", "点赞量", "评论量"), List.of("播放量", "点赞量", "评论量"), "ocr.table.overview.primary");
        addTable(metrics, lines, List.of("分享量", "收藏量", "划走率"), List.of("分享量", "收藏量", "划走率"), "ocr.table.overview.secondary");
        addTable(metrics, lines, List.of("文案展开率", "平均浏览图片数"), List.of("文案展开率", "平均浏览图片数"), "ocr.table.overview.copy");
        addInline(metrics, lines, "播放量较往期", "播放量较往期", "ocr.text");
        addInline(metrics, lines, "播放量较往期上涨", "播放量较往期上涨", "ocr.text");
        addFromResultMetric(metrics, response, "viewCount", "播放量");
        addFromResultMetric(metrics, response, "likeCount", "点赞量");
        addFromResultMetric(metrics, response, "commentCount", "评论量");
        addFromResultMetric(metrics, response, "favoriteCount", "收藏量");
        addFromResultMetric(metrics, response, "shareCount", "分享量");
        addFromResultMetric(metrics, response, "completionRate", "完播率");
    }

    private void extractChartMetrics(LinkedHashMap<String, Object> metrics, List<String> lines, ImageRecognitionDtos.Response response) {
        List<String> chartValues = leadingChartValues(lines);
        if (!chartValues.isEmpty()) metrics.put("趋势曲线数值", metricValue(chartValues, "ocr.chart.series"));
        addTable(metrics, lines, List.of("涨粉量", "脱粉量", "粉丝播放占比"), List.of("涨粉量", "脱粉量", "粉丝播放占比"), "ocr.table.chart.fans");
        addFromResultMetric(metrics, response, "followerGain", "涨粉量");
        addFromResultMetric(metrics, response, "followerCount", "粉丝数");
    }

    private void extractFlowMetrics(LinkedHashMap<String, Object> metrics, List<String> lines, ImageRecognitionDtos.Response response) {
        addInline(metrics, lines, "播放量", "播放量", "ocr.text.flow");
        addInline(metrics, lines, "播放量较往期上涨", "播放量较往期上涨", "ocr.text.flow");
        addInline(metrics, lines, "播放量较往期+", "播放量较往期", "ocr.text.flow");
        addTable(metrics, lines, List.of("封面点击率", "文案展开率", "划走率"), List.of("封面点击率", "文案展开率", "划走率"), "ocr.table.flow.attraction");
        addTable(metrics, lines, List.of("平均浏览图片数", "文案完读率", "评论进入率"), List.of("平均浏览图片数", "文案完读率", "评论进入率"), "ocr.table.flow.reading");
        addInline(metrics, lines, "评论率", "评论率", "ocr.text.flow");
        addInline(metrics, lines, "分享率", "分享率", "ocr.text.flow");
        addInline(metrics, lines, "完播率", "完播率", "ocr.text.flow");
        addInline(metrics, lines, "5s完播率", "5s完播率", "ocr.text.flow");
        addFromResultMetric(metrics, response, "viewCount", "播放量");
        addFromResultMetric(metrics, response, "completionRate", "完播率");
        addFromResultMetric(metrics, response, "interactionRate", "互动率");
    }

    private void addTable(LinkedHashMap<String, Object> metrics, List<String> lines, List<String> labels, List<String> outputKeys, String source) {
        int start = indexOfSequence(lines, labels);
        if (start < 0) return;
        List<String> values = valuesAfter(lines, start + labels.size(), labels.size());
        if (values.isEmpty()) return;
        if (labels.contains("分享量") && labels.contains("收藏量") && labels.contains("划走率") && values.size() == 2 && looksPercent(values.get(1))) {
            putMetric(metrics, "分享量", values.get(0), source);
            putMetric(metrics, "划走率", values.get(1), source);
            return;
        }
        for (int i = 0; i < outputKeys.size() && i < values.size(); i++) {
            putMetric(metrics, outputKeys.get(i), values.get(i), source);
        }
    }

    private void addInline(LinkedHashMap<String, Object> metrics, List<String> lines, String label, String outputKey, String source) {
        for (String line : lines) {
            String normalized = line.replace('：', ':');
            if (!normalized.contains(label)) continue;
            String rest = normalized.substring(normalized.indexOf(label) + label.length());
            String number = firstNumber(rest);
            if (number != null) {
                putMetric(metrics, outputKey, number, source);
                return;
            }
        }
    }

    private void addFromResultMetric(LinkedHashMap<String, Object> metrics, ImageRecognitionDtos.Response response, String sourceKey, String targetKey) {
        if (metrics.containsKey(targetKey)) return;
        Map<String, Object> result = response == null || response.result() == null ? Map.of() : response.result();
        Map<String, Object> nested = mapValue(result.get("metrics"));
        Object value = nested.get(sourceKey);
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) return;
        putMetric(metrics, targetKey, String.valueOf(value), "ocr.metrics." + sourceKey);
    }

    private void addGenericFallback(LinkedHashMap<String, Object> metrics, ImageRecognitionDtos.Response response) {
        Map<String, Object> result = response == null || response.result() == null ? Map.of() : response.result();
        Map<String, Object> nested = mapValue(result.get("metrics"));
        for (Map.Entry<String, Object> entry : nested.entrySet()) {
            Object value = entry.getValue();
            if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) continue;
            String key = englishMetricName(entry.getKey());
            if (key == null) continue;
            metrics.putIfAbsent(key, metricValue(String.valueOf(value), "ocr.metrics." + entry.getKey()));
        }
    }

    private List<String> leadingChartValues(List<String> lines) {
        int marker = indexOf(lines, "观众分析");
        if (marker < 0) marker = indexOf(lines, "流量分析");
        if (marker < 0) return List.of();
        List<String> values = new ArrayList<>();
        for (int i = marker + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (containsAny(line, List.of("05-", "06-", "粉丝", "DOU", "投放"))) break;
            if (isNumberToken(line)) values.add(line);
            if (values.size() >= 8) break;
        }
        return values;
    }

    private List<String> valuesAfter(List<String> lines, int start, int desired) {
        List<String> values = new ArrayList<>();
        for (int i = start; i < lines.size() && values.size() < desired; i++) {
            String line = lines.get(i);
            if (isLikelyLabel(line) && !isNumberToken(line)) {
                if (!values.isEmpty()) break;
                continue;
            }
            String number = firstNumber(line);
            if (number != null) values.add(number);
        }
        return values;
    }

    private int indexOfSequence(List<String> lines, List<String> labels) {
        for (int i = 0; i <= lines.size() - labels.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < labels.size(); j++) {
                if (!sameText(lines.get(i + j), labels.get(j))) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    private int indexOf(List<String> lines, String label) {
        for (int i = 0; i < lines.size(); i++) {
            if (sameText(lines.get(i), label)) return i;
        }
        return -1;
    }

    private boolean isLikelyLabel(String line) {
        if (line == null || line.isBlank()) return false;
        if (isNumberToken(line)) return false;
        if (NOISE_TOKENS.contains(line)) return true;
        return containsAny(line, List.of("播放量", "点赞量", "评论量", "分享量", "收藏量", "划走率", "封面点击率", "文案展开率", "平均浏览图片数", "文案完读率", "评论进入率", "涨粉量", "脱粉量", "粉丝播放占比"));
    }

    private boolean containsAny(String value, List<String> needles) {
        if (value == null) return false;
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private boolean isNumberToken(String value) {
        if (value == null) return false;
        String v = value.trim();
        return v.matches("[+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?(?:%|万|w|W|k|K)?");
    }

    private boolean looksPercent(String value) {
        return value != null && value.trim().contains("%");
    }

    private String firstNumber(String value) {
        if (value == null) return null;
        String normalized = value.replace('：', ':').replace("，", ",").replace("+", "+");
        Matcher matcher = METRIC_NUMBER.matcher(normalized);
        return matcher.find() ? matcher.group().replaceAll("\\s+", "") : null;
    }

    private void putMetric(LinkedHashMap<String, Object> metrics, String key, String value, String source) {
        if (key == null || value == null || value.isBlank()) return;
        metrics.put(key, metricValue(value, source));
    }

    private Map<String, Object> metricValue(Object value, String source) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("source", source);
        return row;
    }

    private String rawText(ImageRecognitionDtos.Response response) {
        if (response == null) return "";
        if (response.rawText() != null && !response.rawText().isBlank()) return response.rawText();
        Object rawText = response.result() == null ? null : response.result().get("rawText");
        if (rawText != null) return String.valueOf(rawText);
        Object rawPayloadText = response.rawPayload() == null ? null : response.rawPayload().get("rawText");
        return rawPayloadText == null ? "" : String.valueOf(rawPayloadText);
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.replace('：', ':').replace("\r", "\n").split("\n+"))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
    }

    private String normalizeGroup(String value) {
        String group = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("DOUYIN_OVERVIEW_CHART".equals(group)) return group;
        if ("DOUYIN_FLOW_ANALYSIS".equals(group)) return group;
        return "DOUYIN_OVERVIEW";
    }

    private String schemaName(String group) {
        return switch (group) {
            case "DOUYIN_OVERVIEW_CHART" -> "总览图表数据";
            case "DOUYIN_FLOW_ANALYSIS" -> "流量分析";
            default -> "总览指标";
        };
    }

    private boolean sameText(String left, String right) {
        return normalizeMetricName(left).equalsIgnoreCase(normalizeMetricName(right));
    }

    private String normalizeMetricName(String value) {
        return value == null ? "" : value.replaceAll("[\\s:_：/\\-+>√]", "").trim();
    }

    private String englishMetricName(String key) {
        return switch (key) {
            case "viewCount" -> "播放量";
            case "likeCount" -> "点赞量";
            case "commentCount" -> "评论量";
            case "favoriteCount" -> "收藏量";
            case "shareCount" -> "分享量";
            case "followerCount" -> "粉丝数";
            case "followerGain" -> "涨粉量";
            case "completionRate" -> "完播率";
            case "interactionRate" -> "互动率";
            case "averageWatchSeconds" -> "平均观看时长";
            case "profileVisitCount" -> "主页访问量";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }
}

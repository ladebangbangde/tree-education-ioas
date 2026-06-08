package com.treeeducation.ioas.recognition;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Primary
public class DataOperationMetricExtractorFavoritePatch extends DataOperationMetricExtractor {
    private static final Pattern NUMBER = Pattern.compile("[+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:%|万|w|W|k|K)?");

    @Override
    public Map<String, Object> extract(String assetGroup, ImageRecognitionDtos.Response response) {
        Map<String, Object> payload = super.extract(assetGroup, response);
        if (payload == null) return payload;
        Object metricsObj = payload.get("metrics");
        if (!(metricsObj instanceof Map<?, ?> rawMetrics)) return payload;

        LinkedHashMap<String, Object> patched = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMetrics.entrySet()) {
            patched.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> result = response == null || response.result() == null ? Map.of() : response.result();
        String rawText = rawText(response);
        patchKeyValueMetrics(patched, mapValue(result.get("keyValueMetrics")));
        patchVideoStats(patched, mapValue(result.get("videoStats")));
        patchGenericMetrics(patched, mapValue(result.get("metrics")));

        if (isOverviewPage(assetGroup, result)) {
            patchOverviewTablesFromRawText(patched, lines(rawText));
        }

        String favorite = extractFavoriteFromRawText(rawText);
        if (favorite != null && !favorite.isBlank()) {
            patched.put("收藏量", metricValue(favorite, "ocr.table.favorite.patch"));
        }

        payload.put("metrics", patched);
        return payload;
    }

    private void patchKeyValueMetrics(LinkedHashMap<String, Object> metrics, Map<String, Object> keyValueMetrics) {
        putIfPresent(metrics, "播放量", first(keyValueMetrics, "播放量"), "ocr.keyValueMetrics.播放量");
        putIfPresent(metrics, "点赞量", first(keyValueMetrics, "点赞量"), "ocr.keyValueMetrics.点赞量");
        putIfPresent(metrics, "评论量", first(keyValueMetrics, "评论量"), "ocr.keyValueMetrics.评论量");
        putIfPresent(metrics, "收藏量", first(keyValueMetrics, "收藏量"), "ocr.keyValueMetrics.收藏量");
        putIfPresent(metrics, "分享量", first(keyValueMetrics, "分享量"), "ocr.keyValueMetrics.分享量");
        putIfPresent(metrics, "完播率", first(keyValueMetrics, "完播率"), "ocr.keyValueMetrics.完播率");
        putIfPresent(metrics, "5s完播率", first(keyValueMetrics, "5s完播率", "5S完播率", "5秒完播率", "5 s完播率"), "ocr.keyValueMetrics.5s完播率");
        putIfPresent(metrics, "评论率", first(keyValueMetrics, "评论率"), "ocr.keyValueMetrics.评论率");
        putIfPresent(metrics, "分享率", first(keyValueMetrics, "分享率"), "ocr.keyValueMetrics.分享率");
        putIfPresent(metrics, "涨粉量", first(keyValueMetrics, "涨粉量"), "ocr.keyValueMetrics.涨粉量");
        putIfPresent(metrics, "封面点击率", first(keyValueMetrics, "封面点击率"), "ocr.keyValueMetrics.封面点击率");
        putIfPresent(metrics, "文案展开率", first(keyValueMetrics, "文案展开率"), "ocr.keyValueMetrics.文案展开率");
        putIfPresent(metrics, "文案完读率", first(keyValueMetrics, "文案完读率"), "ocr.keyValueMetrics.文案完读率");
        putIfPresent(metrics, "评论进入率", first(keyValueMetrics, "评论进入率"), "ocr.keyValueMetrics.评论进入率");
    }

    private void patchVideoStats(LinkedHashMap<String, Object> metrics, Map<String, Object> videoStats) {
        putIfPresent(metrics, "播放量", first(videoStats, "playCount", "viewCount"), "ocr.videoStats.playCount");
        putIfPresent(metrics, "点赞量", first(videoStats, "likeCount"), "ocr.videoStats.likeCount");
        putIfPresent(metrics, "评论量", first(videoStats, "commentCount"), "ocr.videoStats.commentCount");
        putIfPresent(metrics, "收藏量", first(videoStats, "favoriteCount"), "ocr.videoStats.favoriteCount");
        putIfPresent(metrics, "分享量", first(videoStats, "shareCount"), "ocr.videoStats.shareCount");
        putIfPresent(metrics, "完播率", first(videoStats, "completionRate"), "ocr.videoStats.completionRate");
        putIfPresent(metrics, "5s完播率", first(videoStats, "fiveSecondCompletionRate", "fiveSecondsCompletionRate", "fiveSecondFinishRate"), "ocr.videoStats.fiveSecondCompletionRate");
        putIfPresent(metrics, "涨粉量", first(videoStats, "followerGain"), "ocr.videoStats.followerGain");
        putIfPresent(metrics, "评论率", first(videoStats, "commentRate"), "ocr.videoStats.commentRate");
        putIfPresent(metrics, "分享率", first(videoStats, "shareRate"), "ocr.videoStats.shareRate");
    }

    private void patchGenericMetrics(LinkedHashMap<String, Object> metrics, Map<String, Object> generic) {
        putIfPresent(metrics, "播放量", first(generic, "viewCount", "playCount"), "ocr.metrics.viewCount");
        putIfPresent(metrics, "点赞量", first(generic, "likeCount"), "ocr.metrics.likeCount");
        putIfPresent(metrics, "评论量", first(generic, "commentCount"), "ocr.metrics.commentCount");
        putIfPresent(metrics, "收藏量", first(generic, "favoriteCount"), "ocr.metrics.favoriteCount");
        putIfPresent(metrics, "分享量", first(generic, "shareCount"), "ocr.metrics.shareCount");
        putIfPresent(metrics, "完播率", first(generic, "completionRate"), "ocr.metrics.completionRate");
        putIfPresent(metrics, "5s完播率", first(generic, "fiveSecondCompletionRate", "fiveSecondsCompletionRate"), "ocr.metrics.fiveSecondCompletionRate");
        putIfPresent(metrics, "涨粉量", first(generic, "followerGain"), "ocr.metrics.followerGain");
    }

    private void patchOverviewTablesFromRawText(LinkedHashMap<String, Object> metrics, List<String> lines) {
        List<String> primary = valuesAfterCluster(lines, List.of("播放量", "点赞量", "评论量"), 3);
        if (primary.size() >= 3) {
            List<String> countValues = primary.stream().filter(v -> !looksPercent(v)).limit(3).toList();
            if (countValues.size() >= 3) {
                List<String> sorted = countValues.stream()
                        .sorted(Comparator.comparingDouble(this::numericValue).reversed())
                        .toList();
                putIfPresent(metrics, "播放量", sorted.get(0), "ocr.raw.overview.primary");
                putIfPresent(metrics, "点赞量", sorted.get(1), "ocr.raw.overview.primary");
                putIfPresent(metrics, "评论量", sorted.get(2), "ocr.raw.overview.primary");
            }
        }

        List<String> secondary = valuesAfterCluster(lines, List.of("分享量", "收藏量", "弹幕量"), 3);
        if (secondary.size() < 3) secondary = valuesAfterCluster(lines, List.of("分享量", "收藏量", "划走率"), 3);
        if (secondary.size() >= 3) {
            putIfPresent(metrics, "分享量", secondary.get(0), "ocr.raw.overview.secondary");
            putIfPresent(metrics, "收藏量", secondary.get(1), "ocr.raw.overview.secondary");
        }

        List<String> completion = valuesAfterCluster(lines, List.of("完播率", "2s跳出率"), 2);
        if (completion.size() >= 1 && looksPercent(completion.get(0))) {
            putIfPresent(metrics, "完播率", completion.get(0), "ocr.raw.overview.completion");
        }
    }

    private List<String> valuesAfterCluster(List<String> lines, List<String> labels, int desired) {
        if (lines == null || lines.isEmpty()) return List.of();
        for (int i = 0; i < lines.size(); i++) {
            int lastLabelIndex = -1;
            boolean allFound = true;
            for (String label : labels) {
                int index = nextLabelIndex(lines, i, label, 8);
                if (index < 0) {
                    allFound = false;
                    break;
                }
                lastLabelIndex = Math.max(lastLabelIndex, index);
            }
            if (!allFound || lastLabelIndex < 0) continue;
            List<String> values = numbersAfter(lines, lastLabelIndex + 1, desired);
            if (values.size() >= desired) return values;
        }
        return List.of();
    }

    private boolean isOverviewPage(String assetGroup, Map<String, Object> result) {
        String group = assetGroup == null ? "" : assetGroup.toUpperCase();
        String pageType = String.valueOf(result.getOrDefault("pageType", "")).toUpperCase();
        return !group.contains("FLOW_ANALYSIS")
                && !group.contains("OVERVIEW_CHART")
                && !pageType.contains("FLOW_ANALYSIS")
                && !pageType.contains("CHART");
    }

    private Object first(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty()) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (isUsable(value)) return value;
        }
        return null;
    }

    private boolean isUsable(Object value) {
        if (value == null) return false;
        String text = String.valueOf(value).trim();
        return !text.isBlank() && !"null".equalsIgnoreCase(text);
    }

    private void putIfPresent(LinkedHashMap<String, Object> metrics, String label, Object value, String source) {
        if (!isUsable(value)) return;
        metrics.put(label, metricValue(value, source));
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private String extractFavoriteFromRawText(String text) {
        List<String> lines = lines(text);
        if (lines.isEmpty()) return null;
        String value = favoriteFromSequentialLines(lines);
        if (value != null) return value;
        return favoriteFromInlineText(String.join(" ", lines));
    }

    private String favoriteFromSequentialLines(List<String> lines) {
        List<String> secondary = valuesAfterCluster(lines, List.of("分享量", "收藏量", "弹幕量"), 3);
        if (secondary.size() < 3) secondary = valuesAfterCluster(lines, List.of("分享量", "收藏量", "划走率"), 3);
        if (secondary.size() >= 2) return secondary.get(1);
        for (int i = 0; i < lines.size(); i++) {
            if (!same(lines.get(i), "分享量")) continue;
            int favoriteLabel = nextLabelIndex(lines, i + 1, "收藏量", 5);
            if (favoriteLabel < 0) continue;
            int thirdLabel = nextAnyLabelIndex(lines, favoriteLabel + 1, List.of("弹幕量", "划走率"), 6);
            int valueStart = thirdLabel > 0 ? thirdLabel + 1 : favoriteLabel + 1;
            List<String> values = numbersAfter(lines, valueStart, 3);
            if (values.size() >= 2) return values.get(1);
            if (values.size() == 1 && thirdLabel < 0) return values.get(0);
        }
        return null;
    }

    private String favoriteFromInlineText(String text) {
        String normalized = text == null ? "" : text.replace('：', ':');
        int favoriteAt = normalized.indexOf("收藏量");
        if (favoriteAt < 0) return null;
        int nextMetricAt = minPositive(
                normalized.indexOf("弹幕量", favoriteAt + 3),
                normalized.indexOf("划走率", favoriteAt + 3),
                normalized.indexOf("完播率", favoriteAt + 3),
                normalized.indexOf("2s跳出率", favoriteAt + 3),
                normalized.indexOf("5s完播率", favoriteAt + 3)
        );
        String segment = nextMetricAt > favoriteAt ? normalized.substring(favoriteAt, nextMetricAt) : normalized.substring(favoriteAt);
        Matcher matcher = NUMBER.matcher(segment);
        return matcher.find() ? matcher.group().replaceAll("\\s+", "") : null;
    }

    private int nextLabelIndex(List<String> lines, int start, String label, int maxDistance) {
        int end = Math.min(lines.size(), start + maxDistance);
        for (int i = start; i < end; i++) if (same(lines.get(i), label)) return i;
        return -1;
    }

    private int nextAnyLabelIndex(List<String> lines, int start, List<String> labels, int maxDistance) {
        int end = Math.min(lines.size(), start + maxDistance);
        for (int i = start; i < end; i++) for (String label : labels) if (same(lines.get(i), label)) return i;
        return -1;
    }

    private List<String> numbersAfter(List<String> lines, int start, int maxCount) {
        List<String> result = new ArrayList<>();
        for (int i = start; i < lines.size() && result.size() < maxCount; i++) {
            String line = lines.get(i);
            if (isLabel(line) && !result.isEmpty()) break;
            Matcher matcher = NUMBER.matcher(line == null ? "" : line);
            if (matcher.find()) result.add(matcher.group().replaceAll("\\s+", ""));
        }
        return result;
    }

    private boolean isLabel(String line) {
        return same(line, "播放量") || same(line, "点赞量") || same(line, "评论量") || same(line, "分享量") || same(line, "收藏量") || same(line, "弹幕量") || same(line, "划走率") || same(line, "完播率") || same(line, "2s跳出率") || same(line, "5s完播率") || same(line, "5秒完播率");
    }

    private boolean same(String a, String b) {
        return clean(a).equals(clean(b));
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\s:_：/\\-+>√]", "").trim();
    }

    private boolean looksPercent(String value) {
        return value != null && value.contains("%");
    }

    private double numericValue(String value) {
        if (value == null || value.isBlank()) return 0;
        String cleaned = value.replace(",", "").replace("%", "").replace("万", "0000").replace("w", "0000").replace("W", "0000").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : text.replace('：', ':').replace("\r", "\n").split("\n+")) {
            String item = line.trim();
            if (!item.isBlank()) result.add(item);
        }
        return result;
    }

    private String rawText(ImageRecognitionDtos.Response response) {
        if (response == null) return "";
        if (response.rawText() != null && !response.rawText().isBlank()) return response.rawText();
        Object fromResult = response.result() == null ? null : response.result().get("rawText");
        if (fromResult != null) return String.valueOf(fromResult);
        Object fromPayload = response.rawPayload() == null ? null : response.rawPayload().get("rawText");
        return fromPayload == null ? "" : String.valueOf(fromPayload);
    }

    private Map<String, Object> metricValue(Object value, String source) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("source", source);
        return row;
    }

    private int minPositive(int... values) {
        int result = -1;
        for (int value : values) if (value >= 0 && (result < 0 || value < result)) result = value;
        return result;
    }
}

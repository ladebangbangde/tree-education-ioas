package com.treeeducation.ioas.recognition;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        String favorite = extractFavoriteFromRawText(rawText(response));
        if (favorite == null || favorite.isBlank()) return payload;

        LinkedHashMap<String, Object> patched = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMetrics.entrySet()) {
            patched.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        patched.put("收藏量", metricValue(favorite, "ocr.table.favorite.patch"));
        payload.put("metrics", patched);
        return payload;
    }

    private String extractFavoriteFromRawText(String text) {
        List<String> lines = lines(text);
        if (lines.isEmpty()) return null;
        String value = favoriteFromSequentialLines(lines);
        if (value != null) return value;
        return favoriteFromInlineText(String.join(" ", lines));
    }

    private String favoriteFromSequentialLines(List<String> lines) {
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
        return same(line, "播放量") || same(line, "点赞量") || same(line, "评论量") || same(line, "分享量") || same(line, "收藏量") || same(line, "弹幕量") || same(line, "划走率") || same(line, "完播率") || same(line, "5s完播率");
    }

    private boolean same(String a, String b) {
        return clean(a).equals(clean(b));
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\s:_：/\\-+>√]", "").trim();
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

package com.treeeducation.ioas.dataops;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataOperationMetricRawPayloadBackfill {
    private static final Pattern NUMBER = Pattern.compile("[+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:%|万|w|W|k|K)?");
    private final JdbcTemplate jdbc;

    public DataOperationMetricRawPayloadBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        backfill("follower_gain", "OVERVIEW_CHART");
        backfill("five_second_completion_rate", "FLOW_ANALYSIS");
        backfill("completion_rate", "FLOW_ANALYSIS");
    }

    private void backfill(String metricKey, String metricGroup) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select mv.id, mv.metric_key, a.data_payload_json, a.ocr_payload_json
                    from data_operation_metric_value mv
                    join data_operation_asset a on a.id = mv.asset_id
                    where mv.metric_key = ?
                      and mv.metric_group = ?
                      and (mv.metric_value is null or trim(mv.metric_value) = '' or upper(mv.recognition_status) = 'PENDING' or mv.metric_value = '0')
                    """, metricKey, metricGroup);
            for (Map<String, Object> row : rows) {
                String text = String.valueOf(row.get("data_payload_json")) + "\n" + String.valueOf(row.get("ocr_payload_json"));
                String value = valueFor(metricKey, lines(text));
                if (value == null || value.isBlank()) continue;
                jdbc.update("""
                        update data_operation_metric_value
                        set metric_value = ?, metric_numeric = ?, recognition_status = 'SUCCESS', source = 'OCR_BACKFILL', fail_reason = null,
                            recognized_at = coalesce(recognized_at, current_timestamp(6)), updated_at = current_timestamp(6)
                        where id = ?
                        """, value, numeric(value), row.get("id"));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String valueFor(String metricKey, List<String> lines) {
        if ("follower_gain".equals(metricKey)) {
            List<String> values = valuesAfter(lines, List.of("粉丝播放占比", "脱粉量", "涨粉量"), 3);
            if (values.size() >= 3) return strip(values.get(2));
            values = valuesAfter(lines, List.of("脱粉量", "涨粉量"), 2);
            if (values.size() >= 2) return strip(values.get(1));
            return null;
        }
        if ("five_second_completion_rate".equals(metricKey)) {
            List<String> values = valuesAfter(lines, List.of("5s完播率", "平均播放占比"), 2);
            if (!values.isEmpty() && values.get(0).contains("%")) return values.get(0);
            values = valuesAfter(lines, List.of("5秒完播率", "平均播放占比"), 2);
            if (!values.isEmpty() && values.get(0).contains("%")) return values.get(0);
            return null;
        }
        if ("completion_rate".equals(metricKey)) {
            List<String> values = valuesAfter(lines, List.of("完播率", "平均播放时长", "2s跳出率"), 3);
            if (!values.isEmpty() && values.get(0).contains("%")) return values.get(0);
        }
        return null;
    }

    private List<String> valuesAfter(List<String> lines, List<String> labels, int count) {
        for (int start = 0; start < lines.size(); start++) {
            int last = -1;
            boolean ok = true;
            for (String label : labels) {
                int index = findLabel(lines, start, label, 8);
                if (index < 0) { ok = false; break; }
                last = Math.max(last, index);
            }
            if (!ok) continue;
            List<String> values = new ArrayList<>();
            for (int i = last + 1; i < lines.size() && values.size() < count; i++) {
                Matcher matcher = NUMBER.matcher(lines.get(i));
                if (matcher.find()) values.add(matcher.group().replaceAll("\\s+", ""));
            }
            if (values.size() >= count) return values;
        }
        return List.of();
    }

    private int findLabel(List<String> lines, int start, String label, int range) {
        int end = Math.min(lines.size(), start + range);
        for (int i = Math.max(0, start); i < end; i++) {
            if (clean(lines.get(i)).equals(clean(label))) return i;
        }
        return -1;
    }

    private List<String> lines(String text) {
        List<String> result = new ArrayList<>();
        String normalized = text == null ? "" : text.replace("\\n", "\n").replace("\\r", "\n").replace("：", ":").replace(",", "");
        for (String line : normalized.split("\n+")) {
            String item = line.trim();
            if (!item.isBlank()) result.add(item);
        }
        return result;
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\s:_：/\\-+>√]", "").trim();
    }

    private String strip(String value) {
        return value == null ? null : value.replace("+", "").replace("%", "").replace("万", "").replace("w", "").replace("W", "").trim();
    }

    private Object numeric(String value) {
        if (value == null) return null;
        try { return new java.math.BigDecimal(value.replace("%", "").replace("+", "")); } catch (RuntimeException ex) { return null; }
    }
}

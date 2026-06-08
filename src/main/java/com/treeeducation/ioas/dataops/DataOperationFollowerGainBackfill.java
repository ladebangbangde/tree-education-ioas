package com.treeeducation.ioas.dataops;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataOperationFollowerGainBackfill {
    private static final Pattern GROWTH_AFTER_NEW_TOTAL = Pattern.compile("新增累计[\\s\\S]{0,80}?([+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern GROWTH_AFTER_LABEL = Pattern.compile("(?:涨粉量|涨粉|新增粉丝|转粉|净增粉丝)[^0-9+\\-]{0,30}([+\\-]?[0-9][0-9,]*(?:\\.[0-9]+)?)");

    private final JdbcTemplate jdbc;

    public DataOperationFollowerGainBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void backfill() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select mv.id as metric_value_id,
                           mv.asset_id,
                           a.data_payload_json,
                           a.ocr_payload_json
                    from data_operation_metric_value mv
                    join data_operation_asset a on a.id = mv.asset_id
                    where mv.metric_key = 'follower_gain'
                      and mv.metric_group = 'OVERVIEW_CHART'
                      and (mv.metric_value is null or trim(mv.metric_value) = '' or upper(mv.recognition_status) = 'PENDING')
                    """);
            for (Map<String, Object> row : rows) {
                Long id = numberToLong(row.get("metric_value_id"));
                String payload = stringValue(row.get("data_payload_json")) + "\n" + stringValue(row.get("ocr_payload_json"));
                String gain = extractFollowerGain(payload);
                if (id == null || gain == null || gain.isBlank()) continue;
                jdbc.update("""
                        update data_operation_metric_value
                        set metric_value = ?,
                            metric_numeric = ?,
                            recognition_status = 'SUCCESS',
                            source = 'OCR_BACKFILL',
                            fail_reason = null,
                            recognized_at = coalesce(recognized_at, current_timestamp(6)),
                            updated_at = current_timestamp(6)
                        where id = ?
                        """, gain, metricNumeric(gain), id);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String extractFollowerGain(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.replace("\\n", "\n").replace("\\r", "\n").replace("：", ":").replace(",", "");
        Matcher direct = GROWTH_AFTER_LABEL.matcher(normalized);
        if (direct.find()) return direct.group(1);
        Matcher total = GROWTH_AFTER_NEW_TOTAL.matcher(normalized);
        if (total.find()) return total.group(1);
        return null;
    }

    private BigDecimal metricNumeric(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").replace("+", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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
}

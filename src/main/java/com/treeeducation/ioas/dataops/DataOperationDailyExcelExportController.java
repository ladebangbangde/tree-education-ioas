package com.treeeducation.ioas.dataops;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationDailyExcelExportController {
    private final JdbcTemplate jdbc;

    public DataOperationDailyExcelExportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/reports/daily/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','OPERATOR')")
    public ResponseEntity<byte[]> exportDaily(@RequestParam(required = false) String date) throws Exception {
        LocalDate day = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        List<Map<String, Object>> baseRows = jdbc.queryForList("""
                select p.id as package_id,
                       p.topic_date,
                       p.display_name as package_name,
                       p.operator_names,
                       p.media_names,
                       p.anchor_names,
                       t.id as topic_id,
                       t.platform_code,
                       t.platform_name,
                       t.sub_topic_name,
                       t.ocr_account_name,
                       t.ocr_platform_user_id,
                       c.id as content_id,
                       c.content_type,
                       c.content_title,
                       c.content_date
                from data_operation_topic_package p
                join data_operation_platform_topic t on t.package_id = p.id
                left join data_operation_content c on c.platform_topic_id = t.id
                where p.topic_date = ?
                order by p.id asc, t.id asc, c.id asc
                """, Date.valueOf(day));

        List<Map<String, Object>> metrics = jdbc.queryForList("""
                select platform_topic_id, content_id, metric_group, metric_label, metric_value, metric_numeric, metric_unit, source
                from data_operation_metric_value
                where topic_package_id in (select id from data_operation_topic_package where topic_date = ?)
                order by field(metric_group, 'OVERVIEW', 'OVERVIEW_CHART', 'FLOW_ANALYSIS'), metric_label
                """, Date.valueOf(day));
        Map<String, Map<String, String>> metricMap = new LinkedHashMap<>();
        LinkedHashSet<String> metricHeaders = new LinkedHashSet<>();
        for (Map<String, Object> m : metrics) {
            String rowKey = rowKey(numberToLong(m.get("platform_topic_id")), numberToLong(m.get("content_id")));
            String group = stringValue(m.get("metric_group"));
            String label = stringValue(m.get("metric_label"));
            String header = pageLabel(group) + "_" + label;
            String value = stringValue(m.get("metric_value"));
            if (value == null) value = stringValue(m.get("metric_numeric"));
            String unit = stringValue(m.get("metric_unit"));
            if (value != null && unit != null && !value.endsWith(unit)) value = value + unit;
            if ("MANUAL".equalsIgnoreCase(stringValue(m.get("source")))) value = value == null ? "人工修正" : value + "（人工修正）";
            metricHeaders.add(header);
            metricMap.computeIfAbsent(rowKey, k -> new LinkedHashMap<>()).put(header, value == null ? "" : value);
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("当日运营数据");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            List<String> headers = new ArrayList<>(List.of("日期", "主题包", "运营人员", "媒体人员", "主播", "平台", "子主题", "账号名称", "账号ID", "内容类型", "内容标题", "内容日期"));
            headers.addAll(metricHeaders);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Map<String, Object> row : baseRows) {
                Row excelRow = sheet.createRow(r++);
                List<String> fixed = List.of(
                        stringValue(row.get("topic_date"), ""),
                        stringValue(row.get("package_name"), ""),
                        stringValue(row.get("operator_names"), ""),
                        stringValue(row.get("media_names"), ""),
                        stringValue(row.get("anchor_names"), ""),
                        stringValue(row.get("platform_name"), stringValue(row.get("platform_code"), "")),
                        stringValue(row.get("sub_topic_name"), ""),
                        stringValue(row.get("ocr_account_name"), ""),
                        stringValue(row.get("ocr_platform_user_id"), ""),
                        contentTypeLabel(stringValue(row.get("content_type"))),
                        stringValue(row.get("content_title"), ""),
                        stringValue(row.get("content_date"), "")
                );
                int c = 0;
                for (String value : fixed) excelRow.createCell(c++).setCellValue(value);
                Map<String, String> rowMetrics = metricMap.getOrDefault(rowKey(numberToLong(row.get("topic_id")), numberToLong(row.get("content_id"))), Map.of());
                for (String h : metricHeaders) excelRow.createCell(c++).setCellValue(rowMetrics.getOrDefault(h, ""));
            }
            for (int i = 0; i < Math.min(headers.size(), 40); i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            String filename = "运营数据-" + day + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    private String rowKey(Long topicId, Long contentId) {
        return String.valueOf(topicId == null ? 0 : topicId) + ":" + String.valueOf(contentId == null ? 0 : contentId);
    }

    private String pageLabel(String group) {
        if ("OVERVIEW".equals(group)) return "数据页1";
        if ("OVERVIEW_CHART".equals(group)) return "数据页2";
        if ("FLOW_ANALYSIS".equals(group)) return "数据页3";
        return group == null ? "数据页" : group;
    }

    private String contentTypeLabel(String type) {
        if ("VIDEO".equals(type)) return "视频";
        if ("IMAGE_TEXT".equals(type)) return "图文";
        return type == null ? "" : type;
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (RuntimeException ex) { return null; }
    }

    private String stringValue(Object value) { return stringValue(value, null); }

    private String stringValue(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? fallback : text;
    }
}

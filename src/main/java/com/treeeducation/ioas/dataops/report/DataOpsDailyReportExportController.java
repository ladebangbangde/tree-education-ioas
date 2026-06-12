package com.treeeducation.ioas.dataops.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/data-ops/reports-export")
public class DataOpsDailyReportExportController {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataOpsDailyReportExportController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/daily")
    public void exportDailyReport(@RequestBody DailyReportRequest request, HttpServletResponse response) throws IOException {
        LocalDate reportDate = request == null || request.date() == null ? LocalDate.now() : request.date();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select
                  p.topic_date as report_date,
                  p.display_name as package_name,
                  p.operator_names,
                  p.media_names,
                  t.platform_code,
                  t.ocr_account_name as account_name,
                  t.sub_topic_name,
                  c.content_title,
                  c.content_summary,
                  c.content_date,
                  c.data_payload_json,
                  c.recognition_status,
                  c.created_at
                from data_operation_content c
                left join data_operation_platform_topic t on c.platform_topic_id = t.id
                left join data_operation_topic_package p on c.package_id = p.id
                where (p.topic_date = ? or c.content_date = ?)
                order by c.id desc
                """, Date.valueOf(reportDate), Date.valueOf(reportDate));

        String fileName = "数据操作日报_" + reportDate + ".xlsx";
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeSummarySheet(workbook, reportDate, rows);
            writeDetailSheet(workbook, rows);
            writeRawSheet(workbook, rows);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }

    private void writeSummarySheet(XSSFWorkbook workbook, LocalDate reportDate, List<Map<String, Object>> rows) {
        XSSFSheet sheet = workbook.createSheet("汇总看板");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "内容数", "抖音数", "小红书数", "总播放量", "总点赞", "总评论", "总收藏", "总分享"};
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
        }
        long totalViews = 0L;
        long totalLikes = 0L;
        long totalComments = 0L;
        long totalFavorites = 0L;
        long totalShares = 0L;
        long douyinCount = 0L;
        long xhsCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> payload = parseMap(row.get("data_payload_json"));
            totalViews += findLong(payload, "views", "playCount", "viewCount", "播放量");
            totalLikes += findLong(payload, "likes", "likeCount", "点赞");
            totalComments += findLong(payload, "comments", "commentCount", "评论");
            totalFavorites += findLong(payload, "favorites", "favoriteCount", "收藏");
            totalShares += findLong(payload, "shares", "shareCount", "转发");
            String platform = text(row.get("platform_code")).toUpperCase(Locale.ROOT);
            if ("DOUYIN".equals(platform)) douyinCount++;
            if ("XIAOHONGSHU".equals(platform)) xhsCount++;
        }
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(String.valueOf(reportDate));
        row.createCell(1).setCellValue(rows.size());
        row.createCell(2).setCellValue(douyinCount);
        row.createCell(3).setCellValue(xhsCount);
        row.createCell(4).setCellValue(totalViews);
        row.createCell(5).setCellValue(totalLikes);
        row.createCell(6).setCellValue(totalComments);
        row.createCell(7).setCellValue(totalFavorites);
        row.createCell(8).setCellValue(totalShares);
    }

    private void writeDetailSheet(XSSFWorkbook workbook, List<Map<String, Object>> rows) {
        XSSFSheet sheet = workbook.createSheet("内容明细");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "主题包", "平台", "账号", "子主题", "标题", "内容说明", "运营人员", "媒体人员", "播放量", "点赞", "评论", "收藏", "分享", "状态", "创建时间"};
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
        }
        int rowIndex = 1;
        for (Map<String, Object> item : rows) {
            Map<String, Object> payload = parseMap(item.get("data_payload_json"));
            Row row = sheet.createRow(rowIndex++);
            int c = 0;
            row.createCell(c++).setCellValue(blankTo(text(item.get("report_date")), text(item.get("content_date"))));
            row.createCell(c++).setCellValue(text(item.get("package_name")));
            row.createCell(c++).setCellValue(text(item.get("platform_code")));
            row.createCell(c++).setCellValue(text(item.get("account_name")));
            row.createCell(c++).setCellValue(text(item.get("sub_topic_name")));
            row.createCell(c++).setCellValue(text(item.get("content_title")));
            row.createCell(c++).setCellValue(text(item.get("content_summary")));
            row.createCell(c++).setCellValue(text(item.get("operator_names")));
            row.createCell(c++).setCellValue(text(item.get("media_names")));
            row.createCell(c++).setCellValue(findLong(payload, "views", "playCount", "viewCount", "播放量"));
            row.createCell(c++).setCellValue(findLong(payload, "likes", "likeCount", "点赞"));
            row.createCell(c++).setCellValue(findLong(payload, "comments", "commentCount", "评论"));
            row.createCell(c++).setCellValue(findLong(payload, "favorites", "favoriteCount", "收藏"));
            row.createCell(c++).setCellValue(findLong(payload, "shares", "shareCount", "转发"));
            row.createCell(c++).setCellValue(text(item.get("recognition_status")));
            row.createCell(c).setCellValue(text(item.get("created_at")));
        }
    }

    private void writeRawSheet(XSSFWorkbook workbook, List<Map<String, Object>> rows) {
        XSSFSheet sheet = workbook.createSheet("原始数据");
        Row header = sheet.createRow(0);
        String[] titles = {"平台", "账号", "标题", "payload_json"};
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
        }
        int rowIndex = 1;
        for (Map<String, Object> item : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(text(item.get("platform_code")));
            row.createCell(1).setCellValue(text(item.get("account_name")));
            row.createCell(2).setCellValue(text(item.get("content_title")));
            row.createCell(3).setCellValue(text(item.get("data_payload_json")));
        }
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private long findLong(Object root, String... keys) {
        Object value = deepFind(root, Set.of(keys));
        return toLong(value);
    }

    private Object deepFind(Object current, Set<String> keys) {
        if (current == null) return null;
        if (current instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (keys.contains(normalizeKey(String.valueOf(entry.getKey())))) {
                    return entry.getValue();
                }
            }
            for (Object value : map.values()) {
                Object found = deepFind(value, keys);
                if (found != null) return found;
            }
        }
        if (current instanceof Collection<?> collection) {
            for (Object item : collection) {
                Object found = deepFind(item, keys);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]", "").toLowerCase(Locale.ROOT);
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return Math.round(number.doubleValue());
        String text = String.valueOf(value).replaceAll("[^0-9.\\-]", "").trim();
        if (text.isEmpty() || "-".equals(text) || ".".equals(text)) return 0L;
        try {
            return Math.round(Double.parseDouble(text));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String blankTo(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
                return value;
            }
        }
        return "";
    }

    public record DailyReportRequest(LocalDate date) {}
}

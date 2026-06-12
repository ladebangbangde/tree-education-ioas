package com.treeeducation.ioas.dataops.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops/reports")
public class DataOpsDailyReportController {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private static final Set<String> CONFIRMED_STATUSES = Set.of("confirmed", "completed", "imported", "approved", "reviewed", "done", "success", "passed");

    public DataOpsDailyReportController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/daily")
    public void exportDailyReport(@RequestBody DailyReportRequest request, HttpServletResponse response) throws IOException {
        LocalDate reportDate = request.date() != null ? request.date() : LocalDate.now();
        ReportContext context = detectContext();
        List<Map<String, Object>> rows = context.structuredReady()
                ? loadStructuredRows(context, reportDate)
                : loadHeuristicRows(context, reportDate);
        List<ReportRow> reportRows = rows.stream().map(row -> toReportRow(row, reportDate)).toList();
        String fileName = "数据操作日报_" + reportDate + ".xlsx";

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeSummarySheet(workbook, reportDate, reportRows);
            writeDetailSheet(workbook, reportRows);
            writeRawSheet(workbook, rows);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }

    private void writeSummarySheet(XSSFWorkbook workbook, LocalDate reportDate, List<ReportRow> rows) {
        XSSFSheet sheet = workbook.createSheet("汇总看板");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "平台", "内容数量", "总播放量", "总点赞", "总评论", "总收藏", "总转发", "平均互动率"};
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
        }
        Map<String, List<ReportRow>> grouped = rows.stream().collect(Collectors.groupingBy(r -> blankTo(r.platform, "UNKNOWN"), LinkedHashMap::new, Collectors.toList()));
        if (grouped.isEmpty()) grouped = Map.of("ALL", List.of());
        int rowIndex = 1;
        for (Map.Entry<String, List<ReportRow>> entry : grouped.entrySet()) {
            List<ReportRow> group = entry.getValue();
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(String.valueOf(reportDate));
            row.createCell(1).setCellValue(entry.getKey());
            row.createCell(2).setCellValue(group.size());
            row.createCell(3).setCellValue(group.stream().mapToLong(r -> r.page1Views).sum());
            row.createCell(4).setCellValue(group.stream().mapToLong(r -> r.page1Likes).sum());
            row.createCell(5).setCellValue(group.stream().mapToLong(r -> r.page1Comments).sum());
            row.createCell(6).setCellValue(group.stream().mapToLong(r -> r.page1Favorites).sum());
            row.createCell(7).setCellValue(group.stream().mapToLong(r -> r.page1Shares).sum());
            row.createCell(8).setCellValue(group.isEmpty() ? 0D : group.stream().mapToDouble(r -> r.page3EngagementRate).average().orElse(0D));
        }
    }

    private void writeDetailSheet(XSSFWorkbook workbook, List<ReportRow> rows) {
        XSSFSheet sheet = workbook.createSheet("内容明细");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "主题包", "平台", "平台账号", "内容标题", "子主题", "内容类型", "发布时间", "运营人员", "媒体人员", "播放量", "点赞", "评论", "收藏", "转发", "曝光", "主页访问", "涨粉", "完播率", "互动率", "状态", "入库时间"};
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
        }
        int rowIndex = 1;
        for (ReportRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            int c = 0;
            row.createCell(c++).setCellValue(item.date);
            row.createCell(c++).setCellValue(item.packageName);
            row.createCell(c++).setCellValue(item.platform);
            row.createCell(c++).setCellValue(item.account);
            row.createCell(c++).setCellValue(item.title);
            row.createCell(c++).setCellValue(item.subTopic);
            row.createCell(c++).setCellValue(item.contentType);
            row.createCell(c++).setCellValue(item.publishedAt);
            row.createCell(c++).setCellValue(item.operatorName);
            row.createCell(c++).setCellValue(item.mediaName);
            row.createCell(c++).setCellValue(item.page1Views);
            row.createCell(c++).setCellValue(item.page1Likes);
            row.createCell(c++).setCellValue(item.page1Comments);
            row.createCell(c++).setCellValue(item.page1Favorites);
            row.createCell(c++).setCellValue(item.page1Shares);
            row.createCell(c++).setCellValue(item.page2Exposure);
            row.createCell(c++).setCellValue(item.page2ProfileViews);
            row.createCell(c++).setCellValue(item.page2Followers);
            row.createCell(c++).setCellValue(item.page3CompletionRate);
            row.createCell(c++).setCellValue(item.page3EngagementRate);
            row.createCell(c++).setCellValue(item.status);
            row.createCell(c).setCellValue(item.createdAt);
        }
    }

    private void writeRawSheet(XSSFWorkbook workbook, List<Map<String, Object>> rows) {
        XSSFSheet sheet = workbook.createSheet("原始数据");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("标题");
        header.createCell(1).setCellValue("平台");
        header.createCell(2).setCellValue("状态");
        header.createCell(3).setCellValue("内容数据JSON");
        header.createCell(4).setCellValue("OCR数据JSON");
        int rowIndex = 1;
        for (Map<String, Object> rowMap : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(blankTo(text(rowMap.get("content_title")), text(rowMap.get("topic_title"))));
            row.createCell(1).setCellValue(blankTo(text(rowMap.get("content_platform")), text(rowMap.get("topic_platform")), text(rowMap.get("platform_code"))));
            row.createCell(2).setCellValue(blankTo(text(rowMap.get("content_status")), text(rowMap.get("status"))));
            row.createCell(3).setCellValue(blankTo(text(rowMap.get("content_payload")), text(rowMap.get("data_payload_json"))));
            row.createCell(4).setCellValue(blankTo(text(rowMap.get("topic_payload")), text(rowMap.get("ocr_payload_json"))));
        }
    }

    private ReportContext detectContext() {
        List<String> tables = jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", String.class);
        List<TableMeta> metas = new ArrayList<>();
        for (String table : tables) {
            List<String> columns = jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?", String.class, table);
            metas.add(new TableMeta(table, columns, scoreTable(table, columns)));
        }
        TableMeta contentTable = metas.stream().filter(t -> "data_ops_contents".equalsIgnoreCase(t.tableName)).findFirst().orElse(null);
        TableMeta topicTable = metas.stream().filter(t -> "data_ops_platform_topics".equalsIgnoreCase(t.tableName)).findFirst().orElse(null);
        TableMeta packageTable = metas.stream().filter(t -> "data_ops_packages".equalsIgnoreCase(t.tableName)).findFirst().orElse(null);
        TableMeta mainTable = contentTable != null ? contentTable : metas.stream().max(Comparator.comparingInt(t -> t.score)).orElse(null);
        return new ReportContext(mainTable, contentTable, topicTable, packageTable);
    }

    private int scoreTable(String tableName, List<String> columns) {
        int score = 0;
        String lower = tableName.toLowerCase(Locale.ROOT);
        if (lower.contains("data")) score += 2;
        if (lower.contains("ops")) score += 2;
        if (lower.contains("content")) score += 6;
        if (firstExisting(columns, "content_title", "title", "name") != null) score += 6;
        if (firstExisting(columns, "platform_code", "platform") != null) score += 6;
        if (firstExisting(columns, "data_payload_json", "payload_json", "ocr_payload_json") != null) score += 8;
        if (firstExisting(columns, "created_at", "created_time", "published_at", "publish_time") != null) score += 4;
        return score;
    }

    private List<Map<String, Object>> loadStructuredRows(ReportContext ctx, LocalDate reportDate) {
        TableMeta c = ctx.contentTable;
        TableMeta t = ctx.topicTable;
        TableMeta p = ctx.packageTable;
        List<String> parts = new ArrayList<>();
        parts.add(select(c, "c", "content_title", "content_title", "content_name", "name", "title"));
        parts.add(select(c, "c", "content_platform", "platform_code", "platform"));
        parts.add(select(c, "c", "content_type_value", "content_type", "type"));
        parts.add(select(c, "c", "content_status", "status", "recognition_status"));
        parts.add(select(c, "c", "content_payload", "data_payload_json", "payload_json"));
        parts.add(select(c, "c", "content_created_at", "created_at", "created_time", "updated_at"));
        parts.add(select(c, "c", "content_published_at", "content_date", "published_at", "publish_time", "created_at"));
        parts.add(select(t, "t", "topic_platform", "platform_code", "platform_name"));
        parts.add(select(t, "t", "topic_account", "ocr_account_name", "account_name", "platform_account_name"));
        parts.add(select(t, "t", "topic_title", "ocr_content_title", "ocr_title"));
        parts.add(select(t, "t", "topic_sub_name", "sub_topic_name", "sub_topic"));
        parts.add(select(t, "t", "topic_payload", "ocr_payload_json"));
        parts.add(select(t, "t", "topic_content_type", "content_type"));
        parts.add(select(p, "p", "package_name", "display_name", "package_name", "name", "title"));
        parts.add(select(p, "p", "topic_date", "topic_date"));
        parts.add(select(p, "p", "operator_names", "operator_names"));
        parts.add(select(p, "p", "media_names", "media_names"));

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", parts)).append(" FROM `").append(c.tableName).append("` c");
        if (t != null && has(c, "platform_topic_id") && has(t, "id")) {
            sql.append(" LEFT JOIN `").append(t.tableName).append("` t ON c.`").append(firstExisting(c.columns, "platform_topic_id")).append("` = t.`id`");
        }
        if (p != null && has(c, "package_id") && has(p, "id")) {
            sql.append(" LEFT JOIN `").append(p.tableName).append("` p ON c.`").append(firstExisting(c.columns, "package_id")).append("` = p.`id`");
        } else if (p != null && t != null && has(t, "package_id") && has(p, "id")) {
            sql.append(" LEFT JOIN `").append(p.tableName).append("` p ON t.`").append(firstExisting(t.columns, "package_id")).append("` = p.`id`");
        }

        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (p != null && has(p, "topic_date")) {
            where.add("DATE(p.`" + firstExisting(p.columns, "topic_date") + "`) = ?");
            args.add(reportDate);
        } else {
            String contentDateColumn = firstExisting(c.columns, "content_date", "published_at", "publish_time", "created_at", "created_time");
            if (contentDateColumn != null) {
                where.add("DATE(c.`" + contentDateColumn + "`) = ?");
                args.add(reportDate);
            }
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        sql.append(" ORDER BY ").append(has(c, "created_at") ? "c.`" + firstExisting(c.columns, "created_at") + "` DESC" : "1 DESC");
        try {
            return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> loadHeuristicRows(ReportContext ctx, LocalDate reportDate) {
        if (ctx.mainTable == null) return List.of();
        String timeColumn = firstExisting(ctx.mainTable.columns, "created_at", "created_time", "recognized_at", "published_at", "publish_time");
        String sql = "SELECT * FROM `" + ctx.mainTable.tableName + "`" + (timeColumn == null ? "" : " WHERE DATE(`" + timeColumn + "`) = ?");
        try {
            return timeColumn == null ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, reportDate);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ReportRow toReportRow(Map<String, Object> row, LocalDate fallbackDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(parseMap(row.get("topic_payload")));
        payload.putAll(parseMap(row.get("content_payload")));
        payload.putAll(parseMap(row.get("data_payload_json")));
        ReportRow result = new ReportRow();
        result.date = blankTo(text(row.get("topic_date")), String.valueOf(fallbackDate));
        result.packageName = text(row.get("package_name"));
        result.platform = blankTo(text(row.get("content_platform")), text(row.get("topic_platform")), text(row.get("platform_code")), "UNKNOWN");
        result.account = blankTo(text(row.get("topic_account")), text(row.get("account_name")), text(row.get("ocr_account_name")));
        result.title = blankTo(text(row.get("content_title")), text(row.get("topic_title")), text(row.get("title")));
        result.subTopic = blankTo(text(row.get("topic_sub_name")), text(row.get("sub_topic_name")));
        result.contentType = blankTo(text(row.get("content_type_value")), text(row.get("topic_content_type")), text(row.get("content_type")));
        result.publishedAt = blankTo(text(row.get("content_published_at")), text(row.get("content_created_at")), text(row.get("created_at")), text(row.get("topic_date")));
        result.operatorName = text(row.get("operator_names"));
        result.mediaName = text(row.get("media_names"));
        result.page1Views = findLong(payload, "page1Views", "views", "playCount", "viewCount", "播放量");
        result.page1Likes = findLong(payload, "page1Likes", "likes", "likeCount", "点赞");
        result.page1Comments = findLong(payload, "page1Comments", "comments", "commentCount", "评论");
        result.page1Favorites = findLong(payload, "page1Favorites", "favorites", "favoriteCount", "收藏");
        result.page1Shares = findLong(payload, "page1Shares", "shares", "shareCount", "转发");
        result.page2Exposure = findLong(payload, "page2Exposure", "exposure", "impressions", "曝光");
        result.page2ProfileViews = findLong(payload, "page2ProfileViews", "profileViews", "homeVisit", "主页访问");
        result.page2Followers = findLong(payload, "page2Followers", "newFans", "followers", "涨粉");
        result.page3CompletionRate = findDouble(payload, "page3CompletionRate", "completionRate", "完播率");
        result.page3EngagementRate = findDouble(payload, "page3EngagementRate", "engagementRate", "互动率");
        result.status = blankTo(text(row.get("content_status")), text(row.get("status")));
        result.createdAt = blankTo(text(row.get("content_created_at")), text(row.get("created_at")));
        return result;
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
        Object value = deepFind(root, keys);
        return toLong(value);
    }

    private double findDouble(Object root, String... keys) {
        Object value = deepFind(root, keys);
        return toDouble(value);
    }

    private Object deepFind(Object root, String... keys) {
        if (root == null) return null;
        Set<String> normalizedKeys = java.util.Arrays.stream(keys).filter(Objects::nonNull).map(this::normalizeKey).collect(Collectors.toSet());
        return deepFindRecursive(root, normalizedKeys);
    }

    private Object deepFindRecursive(Object current, Set<String> normalizedKeys) {
        if (current == null) return null;
        if (current instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (normalizedKeys.contains(normalizeKey(String.valueOf(entry.getKey())))) {
                    return entry.getValue();
                }
            }
            for (Object value : map.values()) {
                Object found = deepFindRecursive(value, normalizedKeys);
                if (found != null) return found;
            }
        }
        if (current instanceof Collection<?> collection) {
            for (Object item : collection) {
                Object found = deepFindRecursive(item, normalizedKeys);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]", "").toLowerCase(Locale.ROOT);
    }

    private String select(TableMeta meta, String alias, String asName, String... candidates) {
        if (meta == null) return "'' AS `" + asName + "`";
        String column = firstExisting(meta.columns, candidates);
        return column == null ? "'' AS `" + asName + "`" : alias + ".`" + column + "` AS `" + asName + "`";
    }

    private boolean has(TableMeta meta, String... names) {
        return meta != null && firstExisting(meta.columns, names) != null;
    }

    private String firstExisting(Collection<String> available, String... names) {
        Map<String, String> normalized = new HashMap<>();
        for (String key : available) normalized.put(normalizeKey(key), key);
        for (String name : names) {
            String hit = normalized.get(normalizeKey(name));
            if (hit != null) return hit;
        }
        return null;
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

    private double toDouble(Object value) {
        if (value == null) return 0D;
        if (value instanceof Number number) return number.doubleValue();
        String text = String.valueOf(value).replaceAll("[^0-9.\\-]", "").trim();
        if (text.isEmpty() || "-".equals(text) || ".".equals(text)) return 0D;
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return 0D;
        }
    }

    public record DailyReportRequest(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {}

    private static class TableMeta {
        final String tableName;
        final List<String> columns;
        final int score;

        private TableMeta(String tableName, List<String> columns, int score) {
            this.tableName = tableName;
            this.columns = columns;
            this.score = score;
        }
    }

    private static class ReportContext {
        final TableMeta mainTable;
        final TableMeta contentTable;
        final TableMeta topicTable;
        final TableMeta packageTable;

        private ReportContext(TableMeta mainTable, TableMeta contentTable, TableMeta topicTable, TableMeta packageTable) {
            this.mainTable = mainTable;
            this.contentTable = contentTable;
            this.topicTable = topicTable;
            this.packageTable = packageTable;
        }

        private boolean structuredReady() {
            return contentTable != null;
        }
    }

    private static class ReportRow {
        String date = "";
        String packageName = "";
        String platform = "";
        String account = "";
        String title = "";
        String subTopic = "";
        String contentType = "";
        String publishedAt = "";
        String operatorName = "";
        String mediaName = "";
        long page1Views;
        long page1Likes;
        long page1Comments;
        long page1Favorites;
        long page1Shares;
        long page2Exposure;
        long page2ProfileViews;
        long page2Followers;
        double page3CompletionRate;
        double page3EngagementRate;
        String status = "";
        String createdAt = "";
    }
}

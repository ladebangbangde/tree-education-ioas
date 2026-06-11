package com.treeeducation.ioas.dataops.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops/reports")
public class DataOpsExcelReportController {
    private static final Set<String> CONFIRMED_STATUSES = Set.of("confirmed", "completed", "imported", "approved", "reviewed", "done", "success", "passed");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DataOpsExcelReportController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/export-preview")
    public Map<String, Object> preview(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                       @RequestParam(defaultValue = "ALL") String platform,
                                       @RequestParam(required = false) Long topicPackageId,
                                       @RequestParam(defaultValue = "true") boolean onlyConfirmed) {
        LocalDate reportDate = date != null ? date : LocalDate.now();
        ReportContext ctx = detectContext();
        List<ReportRow> rows = loadRows(ctx, reportDate, platform, topicPackageId, onlyConfirmed);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", String.valueOf(reportDate));
        data.put("platform", platform);
        data.put("topicPackageId", topicPackageId);
        data.put("onlyConfirmed", onlyConfirmed);
        data.put("tableName", ctx.mainTable == null ? "" : ctx.mainTable.tableName);
        data.put("totalContentCount", rows.size());
        data.put("confirmedCount", rows.stream().filter(ReportRow::confirmed).count());
        data.put("unconfirmedCount", rows.stream().filter(row -> !row.confirmed()).count());
        data.put("manualCorrectedCount", rows.stream().filter(ReportRow::corrected).count());
        data.put("platformStats", rows.stream().collect(Collectors.groupingBy(row -> safePlatform(row.platform()), LinkedHashMap::new, Collectors.counting())).entrySet().stream().map(e -> Map.of("platform", e.getKey(), "contentCount", e.getValue())).toList());
        return ok(data);
    }

    @GetMapping("/export-logs")
    public Map<String, Object> logs() {
        try {
            return ok(jdbc.queryForList("SELECT id, report_date, platform, file_name, total_content_count, confirmed_count, unconfirmed_count, manual_corrected_count, exported_by_name, exported_at FROM data_operation_report_export_log ORDER BY exported_at DESC LIMIT 50"));
        } catch (Exception ex) {
            return ok(List.of());
        }
    }

    @PostMapping("/export-excel")
    public void export(@RequestBody ExportRequest request, HttpServletResponse response) throws IOException {
        LocalDate reportDate = request.date() != null ? request.date() : LocalDate.now();
        String platform = request.platform() == null || request.platform().isBlank() ? "ALL" : request.platform();
        boolean onlyConfirmed = request.onlyConfirmed() == null || request.onlyConfirmed();
        ReportContext ctx = detectContext();
        List<ReportRow> rows = loadRows(ctx, reportDate, platform, request.topicPackageId(), onlyConfirmed);
        String fileName = "数据操作日报_" + reportDate + ".xlsx";

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeSummarySheet(workbook, reportDate, rows);
            writeDetailSheet(workbook, rows);
            writeAbnormalSheet(workbook, rows);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }

        saveLog(reportDate, platform, rows, fileName);
    }

    private void writeSummarySheet(XSSFWorkbook workbook, LocalDate reportDate, List<ReportRow> rows) {
        XSSFSheet sheet = workbook.createSheet("汇总看板");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "平台", "内容数量", "总播放量", "总点赞", "总评论", "总收藏", "总转发", "平均互动率"};
        for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);
        Map<String, List<ReportRow>> grouped = rows.stream().collect(Collectors.groupingBy(row -> safePlatform(row.platform()), LinkedHashMap::new, Collectors.toList()));
        if (grouped.isEmpty()) grouped = Map.of("ALL", rows);
        int rowIndex = 1;
        for (Map.Entry<String, List<ReportRow>> entry : grouped.entrySet()) {
            List<ReportRow> groupRows = entry.getValue();
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(String.valueOf(reportDate));
            row.createCell(1).setCellValue(entry.getKey());
            row.createCell(2).setCellValue(groupRows.size());
            row.createCell(3).setCellValue(groupRows.stream().mapToLong(ReportRow::page1Views).sum());
            row.createCell(4).setCellValue(groupRows.stream().mapToLong(ReportRow::page1Likes).sum());
            row.createCell(5).setCellValue(groupRows.stream().mapToLong(ReportRow::page1Comments).sum());
            row.createCell(6).setCellValue(groupRows.stream().mapToLong(ReportRow::page1Favorites).sum());
            row.createCell(7).setCellValue(groupRows.stream().mapToLong(ReportRow::page1Shares).sum());
            row.createCell(8).setCellValue(groupRows.isEmpty() ? 0D : groupRows.stream().mapToDouble(ReportRow::page3EngagementRate).average().orElse(0D));
        }
    }

    private void writeDetailSheet(XSSFWorkbook workbook, List<ReportRow> rows) {
        XSSFSheet sheet = workbook.createSheet("内容明细");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "主题包", "平台", "平台账号", "内容标题", "子主题", "内容类型", "发布时间", "运营人员", "媒体人员", "数据页1播放量", "数据页1点赞", "数据页1评论", "数据页1收藏", "数据页1转发", "数据页2曝光", "数据页2主页访问", "数据页2涨粉", "数据页3完播率", "数据页3互动率", "OCR置信度", "是否人工修正", "校验人", "入库时间"};
        for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);
        int rowIndex = 1;
        for (ReportRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            int c = 0;
            row.createCell(c++).setCellValue(item.date());
            row.createCell(c++).setCellValue(item.packageName());
            row.createCell(c++).setCellValue(item.platform());
            row.createCell(c++).setCellValue(item.account());
            row.createCell(c++).setCellValue(item.title());
            row.createCell(c++).setCellValue(item.subTopic());
            row.createCell(c++).setCellValue(item.contentType());
            row.createCell(c++).setCellValue(item.publishedAt());
            row.createCell(c++).setCellValue(item.operatorName());
            row.createCell(c++).setCellValue(item.mediaName());
            row.createCell(c++).setCellValue(item.page1Views());
            row.createCell(c++).setCellValue(item.page1Likes());
            row.createCell(c++).setCellValue(item.page1Comments());
            row.createCell(c++).setCellValue(item.page1Favorites());
            row.createCell(c++).setCellValue(item.page1Shares());
            row.createCell(c++).setCellValue(item.page2Exposure());
            row.createCell(c++).setCellValue(item.page2ProfileViews());
            row.createCell(c++).setCellValue(item.page2Followers());
            row.createCell(c++).setCellValue(item.page3CompletionRate());
            row.createCell(c++).setCellValue(item.page3EngagementRate());
            row.createCell(c++).setCellValue(item.ocrConfidence());
            row.createCell(c++).setCellValue(item.corrected() ? "是" : "否");
            row.createCell(c++).setCellValue(item.reviewer());
            row.createCell(c).setCellValue(item.createdAt());
        }
    }

    private void writeAbnormalSheet(XSSFWorkbook workbook, List<ReportRow> rows) {
        XSSFSheet sheet = workbook.createSheet("异常数据");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("内容标题");
        header.createCell(1).setCellValue("平台");
        header.createCell(2).setCellValue("异常原因");
        int rowIndex = 1;
        for (ReportRow item : rows) {
            String reason = abnormalReason(item);
            if (reason.isBlank()) continue;
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.title());
            row.createCell(1).setCellValue(item.platform());
            row.createCell(2).setCellValue(reason);
        }
    }

    private String abnormalReason(ReportRow row) {
        List<String> reasons = new ArrayList<>();
        if (row.ocrConfidence() > 0 && row.ocrConfidence() < 0.7D) reasons.add("OCR低置信度");
        if (row.corrected()) reasons.add("人工修正");
        if (row.page1Views() == 0 && row.page1Likes() > 0) reasons.add("播放量为0但存在点赞");
        return String.join("；", reasons);
    }

    private void saveLog(LocalDate reportDate, String platform, List<ReportRow> rows, String fileName) {
        try {
            jdbc.update("INSERT INTO data_operation_report_export_log(report_date, platform, file_name, total_content_count, confirmed_count, unconfirmed_count, manual_corrected_count, exported_by_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", reportDate, platform, fileName, rows.size(), rows.stream().filter(ReportRow::confirmed).count(), rows.stream().filter(row -> !row.confirmed()).count(), rows.stream().filter(ReportRow::corrected).count(), "system");
        } catch (Exception ignored) {
        }
    }

    private ReportContext detectContext() {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", String.class);
        List<TableMeta> metas = new ArrayList<>();
        for (String table : tables) {
            List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?", String.class, table);
            metas.add(new TableMeta(table, columns, scoreTable(table, columns)));
        }
        metas.sort(Comparator.comparingInt(TableMeta::score).reversed());
        TableMeta mainTable = metas.stream().filter(meta -> meta.score() > 0).findFirst().orElse(null);
        TableMeta packageTable = metas.stream().filter(meta -> meta.tableName().toLowerCase(Locale.ROOT).contains("package")).filter(meta -> firstExisting(meta.columns(), "id") != null).filter(meta -> firstExisting(meta.columns(), "display_name", "package_name", "name", "title") != null).findFirst().orElse(null);
        return new ReportContext(mainTable, loadIdNameMap(packageTable));
    }

    private int scoreTable(String tableName, List<String> columns) {
        int score = 0;
        String lowerName = tableName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("data")) score += 3;
        if (lowerName.contains("ops")) score += 3;
        if (lowerName.contains("content")) score += 6;
        if (lowerName.contains("report")) score += 2;
        if (lowerName.contains("recogn")) score += 3;
        if (lowerName.contains("review")) score += 2;
        if (lowerName.contains("result")) score += 2;
        if (lowerName.contains("record")) score += 2;
        if (firstExisting(columns, "title", "content_title", "content_name", "post_title", "note_title", "name") != null) score += 8;
        if (firstExisting(columns, "platform", "platform_code", "source_platform", "channel_platform") != null) score += 8;
        if (firstExisting(columns, "status", "review_status", "import_status", "data_status") != null) score += 6;
        if (firstExisting(columns, "created_at", "created_time", "recognized_at", "imported_at", "published_at", "publish_time") != null) score += 6;
        if (firstExisting(columns, "payload", "payload_json", "data_payload_json", "metrics_json", "report_json", "result_json", "ocr_result_json", "extra_json", "data_json") != null) score += 6;
        if (firstExisting(columns, "ocr_confidence", "confidence", "ocr_score") != null) score += 4;
        if (firstExisting(columns, "manually_corrected", "manual_corrected", "corrected") != null) score += 4;
        if (firstExisting(columns, "package_id", "topic_package_id", "content_package_id") != null) score += 4;
        return score;
    }

    private Map<Long, String> loadIdNameMap(TableMeta table) {
        if (table == null) return Map.of();
        String nameColumn = firstExisting(table.columns(), "display_name", "package_name", "name", "title");
        if (nameColumn == null) return Map.of();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, `" + nameColumn + "` AS label FROM `" + table.tableName() + "`");
            Map<Long, String> result = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long id = toLongObject(row.get("id"));
                if (id != null) result.put(id, stringValue(row.get("label")));
            }
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<ReportRow> loadRows(ReportContext ctx, LocalDate reportDate, String platform, Long topicPackageId, boolean onlyConfirmed) {
        if (ctx.mainTable == null) return List.of();
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String timeColumn = firstExisting(ctx.mainTable.columns(), "created_at", "created_time", "recognized_at", "imported_at", "published_at", "publish_time", "reviewed_at");
        String platformColumn = firstExisting(ctx.mainTable.columns(), "platform", "platform_code", "source_platform", "channel_platform");
        String statusColumn = firstExisting(ctx.mainTable.columns(), "status", "review_status", "import_status", "data_status");
        String confirmedColumn = firstExisting(ctx.mainTable.columns(), "confirmed", "is_confirmed", "reviewed", "is_reviewed");
        String packageIdColumn = firstExisting(ctx.mainTable.columns(), "package_id", "topic_package_id", "content_package_id");
        if (timeColumn != null) {
            conditions.add("DATE(`" + timeColumn + "`) = ?");
            params.add(reportDate);
        }
        if (platformColumn != null && platform != null && !platform.isBlank() && !"ALL".equalsIgnoreCase(platform)) {
            conditions.add("`" + platformColumn + "` = ?");
            params.add(platform);
        }
        if (packageIdColumn != null && topicPackageId != null) {
            conditions.add("`" + packageIdColumn + "` = ?");
            params.add(topicPackageId);
        }
        if (onlyConfirmed) {
            if (statusColumn != null) {
                String marks = CONFIRMED_STATUSES.stream().map(v -> "?").collect(Collectors.joining(","));
                conditions.add("LOWER(CAST(`" + statusColumn + "` AS CHAR)) IN (" + marks + ")");
                params.addAll(CONFIRMED_STATUSES);
            } else if (confirmedColumn != null) {
                conditions.add("CAST(`" + confirmedColumn + "` AS CHAR) IN ('1','true','TRUE','Y','y')");
            }
        }
        String sql = "SELECT * FROM `" + ctx.mainTable.tableName() + "`" + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions));
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, params.toArray());
        } catch (Exception ex) {
            return List.of();
        }
        return rows.stream().map(row -> mapRow(ctx, row, platformColumn, statusColumn, confirmedColumn, packageIdColumn, reportDate)).toList();
    }

    private ReportRow mapRow(ReportContext ctx, Map<String, Object> row, String platformColumn, String statusColumn, String confirmedColumn, String packageIdColumn, LocalDate reportDate) {
        String packageName = firstValue(row, "package_name", "package_title", "package_display_name", "topic_package_name");
        if (packageName.isBlank() && packageIdColumn != null) {
            Long id = toLongObject(row.get(packageIdColumn));
            if (id != null) packageName = ctx.packageNameMap.getOrDefault(id, "");
        }
        String createdAt = formatObject(row.getOrDefault(firstExisting(row.keySet(), "created_at", "created_time", "recognized_at", "imported_at", "updated_at"), ""));
        String publishedAt = formatObject(row.getOrDefault(firstExisting(row.keySet(), "published_at", "publish_time", "post_time", "content_publish_time", "created_at"), ""));
        String platform = platformColumn == null ? firstValue(row, "platform", "platform_code", "source_platform", "channel_platform") : stringValue(row.get(platformColumn));
        boolean confirmed = (statusColumn == null && confirmedColumn == null) || isConfirmed(row, statusColumn, confirmedColumn);
        Map<String, Object> payload = mergedPayload(row);
        return new ReportRow(
                String.valueOf(reportDate),
                packageName,
                safePlatform(platform),
                firstValue(row, "account_name", "platform_account_name", "platform_account", "account", "nickname", "author_name"),
                firstValue(row, "title", "content_title", "content_name", "post_title", "note_title", "name"),
                firstValue(row, "sub_topic_name", "sub_topic", "topic_name", "theme_name"),
                firstValue(row, "content_type", "material_type", "type"),
                publishedAt,
                firstValue(row, "operator_name", "operator_names", "owner_name"),
                firstValue(row, "media_name", "media_names", "creator_name", "uploader_name"),
                findLong(payload, "page1Views", "views", "playCount", "viewCount", "播放量"),
                findLong(payload, "page1Likes", "likes", "likeCount", "点赞"),
                findLong(payload, "page1Comments", "comments", "commentCount", "评论"),
                findLong(payload, "page1Favorites", "favorites", "favoriteCount", "收藏"),
                findLong(payload, "page1Shares", "shares", "shareCount", "转发"),
                findLong(payload, "page2Exposure", "exposure", "impressions", "曝光"),
                findLong(payload, "page2ProfileViews", "profileViews", "homeVisit", "主页访问"),
                findLong(payload, "page2Followers", "newFans", "followers", "涨粉"),
                findDouble(payload, "page3CompletionRate", "completionRate", "完播率"),
                findDouble(payload, "page3EngagementRate", "engagementRate", "互动率"),
                firstDouble(row, "ocr_confidence", "confidence", "ocr_score"),
                isTruthy(row, "manually_corrected", "manual_corrected", "corrected", "has_manual_fix"),
                firstValue(row, "reviewed_by_name", "reviewer_name", "checker_name", "confirmed_by_name"),
                createdAt,
                confirmed
        );
    }

    private boolean isConfirmed(Map<String, Object> row, String statusColumn, String confirmedColumn) {
        if (statusColumn != null) {
            String status = stringValue(row.get(statusColumn)).trim().toLowerCase(Locale.ROOT);
            return CONFIRMED_STATUSES.contains(status);
        }
        return isTruthy(row, confirmedColumn);
    }

    private Map<String, Object> mergedPayload(Map<String, Object> row) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String key : List.of("payload", "payload_json", "data_payload_json", "metrics_json", "report_json", "result_json", "ocr_result_json", "extra_json", "data_json")) {
            if (row.containsKey(key)) merged.putAll(parseMap(row.get(key)));
        }
        for (String key : row.keySet()) merged.putIfAbsent(key, row.get(key));
        return merged;
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return Map.of();
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private long findLong(Map<String, Object> data, String... keys) {
        return Math.round(findDouble(data, keys));
    }

    private double findDouble(Map<String, Object> data, String... keys) {
        Object value = findValueDeep(data, normalizeKeys(keys));
        return toDouble(value);
    }

    private Object findValueDeep(Object node, Set<String> normalizedKeys) {
        if (node == null) return null;
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (normalizedKeys.contains(normalizeKey(String.valueOf(entry.getKey())))) return entry.getValue();
            }
            for (Object value : map.values()) {
                Object found = findValueDeep(value, normalizedKeys);
                if (found != null) return found;
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                Object found = findValueDeep(item, normalizedKeys);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Set<String> normalizeKeys(String... keys) {
        return Arrays.stream(keys).map(this::normalizeKey).collect(Collectors.toSet());
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.replaceAll("[_\\-\\s]", "").toLowerCase(Locale.ROOT);
    }

    private String firstValue(Map<String, Object> row, String... keys) {
        String key = firstExisting(row.keySet(), keys);
        return key == null ? "" : stringValue(row.get(key));
    }

    private Double firstDouble(Map<String, Object> row, String... keys) {
        String key = firstExisting(row.keySet(), keys);
        return key == null ? 0D : toDouble(row.get(key));
    }

    private boolean isTruthy(Map<String, Object> row, String... keys) {
        String key = firstExisting(row.keySet(), keys);
        if (key == null) return false;
        return isTruthy(row.get(key));
    }

    private boolean isTruthy(Object value) {
        String text = stringValue(value).trim().toLowerCase(Locale.ROOT);
        return "1".equals(text) || "true".equals(text) || "y".equals(text) || "yes".equals(text);
    }

    private String firstExisting(Collection<String> available, String... candidates) {
        Map<String, String> normalized = new HashMap<>();
        for (String key : available) normalized.put(normalizeKey(key), key);
        for (String candidate : candidates) {
            String found = normalized.get(normalizeKey(candidate));
            if (found != null) return found;
        }
        return null;
    }

    private double toDouble(Object value) {
        if (value == null) return 0D;
        if (value instanceof Number number) return number.doubleValue();
        String text = String.valueOf(value).replace("%", "").replace(",", "").trim();
        if (text.isBlank()) return 0D;
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return 0D;
        }
    }

    private Long toLongObject(Object value) {
        if (value == null) return null;
        return Math.round(toDouble(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safePlatform(String platform) {
        return platform == null || platform.isBlank() ? "UNKNOWN" : platform;
    }

    private String formatObject(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "ok");
        result.put("data", data);
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return result;
    }

    public record ExportRequest(LocalDate date, String platform, Long topicPackageId, Boolean onlyConfirmed) {}
    private record TableMeta(String tableName, List<String> columns, int score) {}
    private record ReportContext(TableMeta mainTable, Map<Long, String> packageNameMap) {}
    private record ReportRow(String date, String packageName, String platform, String account, String title, String subTopic, String contentType, String publishedAt, String operatorName, String mediaName, long page1Views, long page1Likes, long page1Comments, long page1Favorites, long page1Shares, long page2Exposure, long page2ProfileViews, long page2Followers, double page3CompletionRate, double page3EngagementRate, double ocrConfidence, boolean corrected, String reviewer, String createdAt, boolean confirmed) {}
}

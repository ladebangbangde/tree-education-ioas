package com.treeeducation.ioas.dataops.report;

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
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops/reports2")
public class DataOpsExcelReportV2Controller {
    private final JdbcTemplate jdbc;
    private final DataOpsExcelReportQueryService queryService;

    public DataOpsExcelReportV2Controller(JdbcTemplate jdbc, DataOpsExcelReportQueryService queryService) {
        this.jdbc = jdbc;
        this.queryService = queryService;
    }

    @GetMapping("/export-preview")
    public Map<String, Object> preview(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                       @RequestParam(defaultValue = "ALL") String platform,
                                       @RequestParam(required = false) Long topicPackageId,
                                       @RequestParam(defaultValue = "true") boolean onlyConfirmed) {
        LocalDate reportDate = date != null ? date : LocalDate.now();
        DataOpsExcelReportQueryService.QueryResult result = queryService.load(reportDate, platform, topicPackageId, onlyConfirmed);
        List<ReportRow> rows = result.rows().stream().map(row -> mapRow(queryService.toReportRow(row, reportDate))).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", String.valueOf(reportDate));
        data.put("platform", platform);
        data.put("topicPackageId", topicPackageId);
        data.put("onlyConfirmed", onlyConfirmed);
        data.put("tableName", result.tableName());
        data.put("sourceMode", result.sourceMode());
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

    @GetMapping("/export-logs/{id}/top5")
    public Map<String, Object> top5(@PathVariable Long id) {
        try {
            List<Map<String, Object>> logs = jdbc.queryForList("SELECT id, report_date, platform, file_name FROM data_operation_report_export_log WHERE id = ? LIMIT 1", id);
            if (logs.isEmpty()) return ok(Map.of("logId", id, "rows", List.of()));
            Map<String, Object> log = logs.get(0);
            LocalDate reportDate = toLocalDate(log.get("report_date"));
            String platform = text(log.get("platform"));
            DataOpsExcelReportQueryService.QueryResult result = queryService.load(reportDate, platform, null, true);
            List<Map<String, Object>> topRows = result.rows().stream().limit(5).map(row -> queryService.toReportRow(row, reportDate)).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("logId", id);
            data.put("reportDate", String.valueOf(reportDate));
            data.put("platform", platform);
            data.put("fileName", text(log.get("file_name")));
            data.put("tableName", result.tableName());
            data.put("sourceMode", result.sourceMode());
            data.put("rows", topRows);
            return ok(data);
        } catch (Exception ex) {
            return ok(Map.of("logId", id, "rows", List.of(), "error", ex.getMessage()));
        }
    }

    @PostMapping("/export-excel")
    public void export(@RequestBody ExportRequest request, HttpServletResponse response) throws IOException {
        LocalDate reportDate = request.date() != null ? request.date() : LocalDate.now();
        String platform = request.platform() == null || request.platform().isBlank() ? "ALL" : request.platform();
        boolean onlyConfirmed = request.onlyConfirmed() == null || request.onlyConfirmed();
        DataOpsExcelReportQueryService.QueryResult result = queryService.load(reportDate, platform, request.topicPackageId(), onlyConfirmed);
        List<ReportRow> rows = result.rows().stream().map(row -> mapRow(queryService.toReportRow(row, reportDate))).toList();
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

    private ReportRow mapRow(Map<String, Object> row) {
        return new ReportRow(value(row, "date"), value(row, "packageName"), safePlatform(value(row, "platform")), value(row, "account"), value(row, "title"), value(row, "subTopic"), value(row, "contentType"), value(row, "publishedAt"), value(row, "operatorName"), value(row, "mediaName"), toLong(row.get("page1Views")), toLong(row.get("page1Likes")), toLong(row.get("page1Comments")), toLong(row.get("page1Favorites")), toLong(row.get("page1Shares")), toLong(row.get("page2Exposure")), toLong(row.get("page2ProfileViews")), toLong(row.get("page2Followers")), toDouble(row.get("page3CompletionRate")), toDouble(row.get("page3EngagementRate")), toDouble(row.get("ocrConfidence")), truthy(row.get("corrected")), value(row, "reviewer"), value(row, "createdAt"), truthy(row.get("confirmed")));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "ok");
        result.put("data", data);
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return result;
    }

    private String safePlatform(String platform) { return platform == null || platform.isBlank() ? "UNKNOWN" : platform; }
    private String value(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? "" : String.valueOf(value); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private long toLong(Object value) { if (value == null) return 0L; if (value instanceof Number number) return Math.round(number.doubleValue()); try { return Math.round(Double.parseDouble(String.valueOf(value))); } catch (Exception ex) { return 0L; } }
    private double toDouble(Object value) { if (value == null) return 0D; if (value instanceof Number number) return number.doubleValue(); try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ex) { return 0D; } }
    private boolean truthy(Object value) { if (value == null) return false; String text = String.valueOf(value).trim().toLowerCase(); return "1".equals(text) || "true".equals(text) || "y".equals(text) || "yes".equals(text); }

    public record ExportRequest(LocalDate date, String platform, Long topicPackageId, Boolean onlyConfirmed) {}
    private record ReportRow(String date, String packageName, String platform, String account, String title, String subTopic, String contentType, String publishedAt, String operatorName, String mediaName, long page1Views, long page1Likes, long page1Comments, long page1Favorites, long page1Shares, long page2Exposure, long page2ProfileViews, long page2Followers, double page3CompletionRate, double page3EngagementRate, double ocrConfidence, boolean corrected, String reviewer, String createdAt, boolean confirmed) {}
}

package com.treeeducation.ioas.dataops.report;

import com.treeeducation.ioas.dataops.DataOperationMetricService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/v1/data-ops/reports-export")
public class DataOpsDailyReportExportController {
    private final JdbcTemplate jdbcTemplate;
    private final DataOperationMetricService metricService;

    public DataOpsDailyReportExportController(JdbcTemplate jdbcTemplate, DataOperationMetricService metricService) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricService = metricService;
    }

    @PostMapping("/daily")
    public void exportDailyReport(@RequestBody DailyReportRequest request, HttpServletResponse response) throws IOException {
        LocalDate reportDate = request == null || request.date() == null ? LocalDate.now() : request.date();
        String requestedContentType = normalizeContentType(request == null ? null : request.contentType());

        if (!requestedContentType.isBlank()) {
            List<ReportRow> rows = loadReportRows(reportDate, requestedContentType);
            String typeLabel = contentTypeLabel(requestedContentType);
            String fileName = "数据操作日报_" + reportDate + "_" + typeLabel + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            response.getOutputStream().write(buildWorkbookBytes(reportDate, rows, typeLabel));
            return;
        }

        String zipFileName = "数据操作日报_" + reportDate + "_图文与视频.zip";
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(zipFileName, StandardCharsets.UTF_8));
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writeWorkbookEntry(zip, "数据操作日报_" + reportDate + "_图文.xlsx", reportDate, loadReportRows(reportDate, "IMAGE_TEXT"), "图文");
            writeWorkbookEntry(zip, "数据操作日报_" + reportDate + "_视频.xlsx", reportDate, loadReportRows(reportDate, "VIDEO"), "视频");
        }
    }

    private void writeWorkbookEntry(ZipOutputStream zip, String fileName, LocalDate reportDate, List<ReportRow> rows, String typeLabel) throws IOException {
        zip.putNextEntry(new ZipEntry(fileName));
        zip.write(buildWorkbookBytes(reportDate, rows, typeLabel));
        zip.closeEntry();
    }

    private byte[] buildWorkbookBytes(LocalDate reportDate, List<ReportRow> rows, String typeLabel) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSummarySheet(workbook, reportDate, rows, typeLabel);
            writeDetailSheet(workbook, rows, typeLabel);
            writeMetricSheet(workbook, rows, typeLabel);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private List<ReportRow> loadReportRows(LocalDate reportDate, String contentType) {
        List<Map<String, Object>> contents = jdbcTemplate.queryForList("""
                select
                  p.id as package_id,
                  p.topic_date as report_date,
                  p.display_name as package_name,
                  p.operator_names,
                  p.media_names,
                  t.id as topic_id,
                  t.platform_code,
                  t.platform_name,
                  t.ocr_account_name as account_name,
                  t.ocr_platform_user_id as platform_user_id,
                  t.sub_topic_name,
                  c.id as content_id,
                  c.content_type,
                  c.content_title,
                  c.content_summary,
                  c.content_date,
                  c.recognition_status,
                  c.created_at
                from data_operation_content c
                join data_operation_platform_topic t on t.id = c.platform_topic_id
                join data_operation_topic_package p on p.id = c.package_id
                where p.topic_date = ?
                  and c.content_type = ?
                order by p.id desc, t.id desc, c.id desc
                """, Date.valueOf(reportDate), contentType);
        return contents.stream().map(this::buildReportRow).toList();
    }

    private ReportRow buildReportRow(Map<String, Object> content) {
        Long topicId = numberToLong(content.get("topic_id"));
        Long contentId = numberToLong(content.get("content_id"));
        List<Map<String, Object>> topicMetrics = topicId == null ? List.of() : metricService.listTopicMetrics(topicId);
        List<Map<String, Object>> metricRows = topicMetrics.stream()
                .filter(metric -> contentId != null && contentId.equals(numberToLong(metric.get("contentId"))))
                .toList();
        Map<String, Map<String, Object>> metricsByLabel = new LinkedHashMap<>();
        for (Map<String, Object> metricRow : metricRows) {
            metricsByLabel.putIfAbsent(normalizeLabel(text(metricRow.get("metricLabel"))), metricRow);
        }

        ReportRow row = new ReportRow();
        row.date = firstNonBlank(text(content.get("report_date")), text(content.get("content_date")));
        row.packageName = text(content.get("package_name"));
        row.platformCode = firstNonBlank(text(content.get("platform_code")), "DOUYIN");
        row.platformName = firstNonBlank(text(content.get("platform_name")), platformLabel(row.platformCode));
        row.accountName = firstNonBlank(findFirstMetricText(metricRows, "accountName"), text(content.get("account_name")));
        row.platformUserId = firstNonBlank(findFirstMetricText(metricRows, "platformUserId"), text(content.get("platform_user_id")));
        row.subTopicName = text(content.get("sub_topic_name"));
        row.contentId = contentId;
        row.contentType = firstNonBlank(text(content.get("content_type")), "IMAGE_TEXT");
        row.contentTypeLabel = contentTypeLabel(row.contentType);
        row.contentTitle = firstNonBlank(findFirstMetricText(metricRows, "videoTitle"), text(content.get("content_title")));
        row.contentSummary = text(content.get("content_summary"));
        row.operatorNames = text(content.get("operator_names"));
        row.mediaNames = text(content.get("media_names"));
        row.recognitionStatus = text(content.get("recognition_status"));
        row.createdAt = text(content.get("created_at"));
        row.metricRows = metricRows;

        row.views = metricText(metricsByLabel, "播放量", "浏览量");
        row.likes = metricText(metricsByLabel, "点赞量", "点赞");
        row.comments = metricText(metricsByLabel, "评论量", "评论");
        row.favorites = metricText(metricsByLabel, "收藏量", "收藏");
        row.shares = metricText(metricsByLabel, "分享量", "转发量", "转发", "分享");
        row.newFollowers = metricText(metricsByLabel, "涨粉量", "新增粉丝数", "新增粉丝");
        row.coverClickRate = metricText(metricsByLabel, "封面点击率");
        row.copyExpandRate = metricText(metricsByLabel, "文案展开率");
        row.copyReadRate = metricText(metricsByLabel, "文案完读率");
        row.commentEnterRate = metricText(metricsByLabel, "评论进入率");
        row.completionRate = metricText(metricsByLabel, "完播率");
        row.fiveSecondCompletionRate = metricText(metricsByLabel, "5s完播率");

        row.viewsLong = toLong(row.views);
        row.likesLong = toLong(row.likes);
        row.commentsLong = toLong(row.comments);
        row.favoritesLong = toLong(row.favorites);
        row.sharesLong = toLong(row.shares);
        row.newFollowersLong = toLong(row.newFollowers);
        row.coverClickRateDouble = toDouble(row.coverClickRate);
        row.copyExpandRateDouble = toDouble(row.copyExpandRate);
        row.copyReadRateDouble = toDouble(row.copyReadRate);
        row.commentEnterRateDouble = toDouble(row.commentEnterRate);
        row.completionRateDouble = toDouble(row.completionRate);
        row.fiveSecondCompletionRateDouble = toDouble(row.fiveSecondCompletionRate);
        return row;
    }

    private void writeSummarySheet(XSSFWorkbook workbook, LocalDate reportDate, List<ReportRow> rows, String typeLabel) {
        XSSFSheet sheet = workbook.createSheet(typeLabel + "主题账号汇总");
        Row header = sheet.createRow(0);
        String[] titles = {
                "日期", "主题包", "平台子主题", "平台", "账号名称", "平台账号ID", "运营人员", "媒体人员",
                "总播放量", "总点赞量", "总评论量", "总收藏量", "总分享量", "总涨粉量",
                "平均封面点击率", "平均文案展开率", "平均文案完读率", "平均评论进入率", "平均完播率", "平均5s完播率"
        };
        for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);

        Map<String, List<ReportRow>> groups = new LinkedHashMap<>();
        for (ReportRow item : rows) {
            String key = String.join("|",
                    item.date,
                    item.packageName,
                    item.subTopicName,
                    item.platformCode,
                    item.accountName,
                    item.platformUserId,
                    item.operatorNames,
                    item.mediaNames
            );
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        if (groups.isEmpty()) {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(String.valueOf(reportDate));
            row.createCell(1).setCellValue("当日无已确认" + typeLabel + "数据");
            return;
        }

        int rowIndex = 1;
        for (List<ReportRow> groupRows : groups.values()) {
            ReportRow first = groupRows.get(0);
            Row row = sheet.createRow(rowIndex++);
            int c = 0;
            row.createCell(c++).setCellValue(first.date);
            row.createCell(c++).setCellValue(first.packageName);
            row.createCell(c++).setCellValue(first.subTopicName);
            row.createCell(c++).setCellValue(first.platformName);
            row.createCell(c++).setCellValue(first.accountName);
            row.createCell(c++).setCellValue(first.platformUserId);
            row.createCell(c++).setCellValue(first.operatorNames);
            row.createCell(c++).setCellValue(first.mediaNames);
            row.createCell(c++).setCellValue(groupRows.stream().mapToLong(item -> item.viewsLong).sum());
            row.createCell(c++).setCellValue(groupRows.stream().mapToLong(item -> item.likesLong).sum());
            row.createCell(c++).setCellValue(groupRows.stream().mapToLong(item -> item.commentsLong).sum());
            row.createCell(c++).setCellValue(groupRows.stream().mapToLong(item -> item.favoritesLong).sum());
            row.createCell(c++).setCellValue(groupRows.stream().mapToLong(item -> item.sharesLong).sum());
            row.createCell(c++).setCellValue(groupRows.stream().mapToLong(item -> item.newFollowersLong).sum());
            row.createCell(c++).setCellValue(averagePercent(groupRows, item -> item.coverClickRateDouble));
            row.createCell(c++).setCellValue(averagePercent(groupRows, item -> item.copyExpandRateDouble));
            row.createCell(c++).setCellValue(averagePercent(groupRows, item -> item.copyReadRateDouble));
            row.createCell(c++).setCellValue(averagePercent(groupRows, item -> item.commentEnterRateDouble));
            row.createCell(c++).setCellValue(averagePercent(groupRows, item -> item.completionRateDouble));
            row.createCell(c).setCellValue(averagePercent(groupRows, item -> item.fiveSecondCompletionRateDouble));
        }
    }

    private void writeDetailSheet(XSSFWorkbook workbook, List<ReportRow> rows, String typeLabel) {
        XSSFSheet sheet = workbook.createSheet(typeLabel + "作品数据明细");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "主题包", "平台子主题", "平台", "账号名称", "平台账号ID", "运营人员", "媒体人员", "作品ID", "作品类型", "作品标题", "内容说明", "播放量", "点赞量", "评论量", "收藏量", "分享量", "涨粉量", "封面点击率", "文案展开率", "文案完读率", "评论进入率", "完播率", "5s完播率", "状态", "创建时间"};
        for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);
        int rowIndex = 1;
        for (ReportRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            int c = 0;
            row.createCell(c++).setCellValue(item.date);
            row.createCell(c++).setCellValue(item.packageName);
            row.createCell(c++).setCellValue(item.subTopicName);
            row.createCell(c++).setCellValue(item.platformName);
            row.createCell(c++).setCellValue(item.accountName);
            row.createCell(c++).setCellValue(item.platformUserId);
            row.createCell(c++).setCellValue(item.operatorNames);
            row.createCell(c++).setCellValue(item.mediaNames);
            row.createCell(c++).setCellValue(item.contentId == null ? "" : String.valueOf(item.contentId));
            row.createCell(c++).setCellValue(item.contentTypeLabel);
            row.createCell(c++).setCellValue(item.contentTitle);
            row.createCell(c++).setCellValue(item.contentSummary);
            row.createCell(c++).setCellValue(item.views);
            row.createCell(c++).setCellValue(item.likes);
            row.createCell(c++).setCellValue(item.comments);
            row.createCell(c++).setCellValue(item.favorites);
            row.createCell(c++).setCellValue(item.shares);
            row.createCell(c++).setCellValue(item.newFollowers);
            row.createCell(c++).setCellValue(item.coverClickRate);
            row.createCell(c++).setCellValue(item.copyExpandRate);
            row.createCell(c++).setCellValue(item.copyReadRate);
            row.createCell(c++).setCellValue(item.commentEnterRate);
            row.createCell(c++).setCellValue(item.completionRate);
            row.createCell(c++).setCellValue(item.fiveSecondCompletionRate);
            row.createCell(c++).setCellValue(item.recognitionStatus);
            row.createCell(c).setCellValue(item.createdAt);
        }
    }

    private void writeMetricSheet(XSSFWorkbook workbook, List<ReportRow> rows, String typeLabel) {
        XSSFSheet sheet = workbook.createSheet(typeLabel + "OCR指标明细");
        Row header = sheet.createRow(0);
        String[] titles = {"日期", "主题包", "平台子主题", "平台", "账号名称", "平台账号ID", "运营人员", "媒体人员", "作品ID", "作品类型", "作品标题", "数据页", "来源图片", "数据标签", "识别值", "单位", "状态", "识别时间", "来源"};
        for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);
        int rowIndex = 1;
        for (ReportRow item : rows) {
            for (Map<String, Object> metric : item.metricRows) {
                Row row = sheet.createRow(rowIndex++);
                int c = 0;
                row.createCell(c++).setCellValue(item.date);
                row.createCell(c++).setCellValue(item.packageName);
                row.createCell(c++).setCellValue(item.subTopicName);
                row.createCell(c++).setCellValue(item.platformName);
                row.createCell(c++).setCellValue(firstNonBlank(text(metric.get("accountName")), item.accountName));
                row.createCell(c++).setCellValue(firstNonBlank(text(metric.get("platformUserId")), item.platformUserId));
                row.createCell(c++).setCellValue(item.operatorNames);
                row.createCell(c++).setCellValue(item.mediaNames);
                row.createCell(c++).setCellValue(item.contentId == null ? "" : String.valueOf(item.contentId));
                row.createCell(c++).setCellValue(item.contentTypeLabel);
                row.createCell(c++).setCellValue(firstNonBlank(text(metric.get("videoTitle")), text(metric.get("contentTitle")), item.contentTitle));
                row.createCell(c++).setCellValue(metricGroupLabel(text(metric.get("metricGroup"))));
                row.createCell(c++).setCellValue(assetLabel(metric.get("assetId")));
                row.createCell(c++).setCellValue(text(metric.get("metricLabel")));
                row.createCell(c++).setCellValue(metricValueText(metric));
                row.createCell(c++).setCellValue(text(metric.get("metricUnit")));
                row.createCell(c++).setCellValue(text(metric.get("recognitionStatus")));
                row.createCell(c++).setCellValue(text(metric.get("recognizedAt")));
                row.createCell(c).setCellValue(text(metric.get("source")));
            }
        }
    }

    private String findFirstMetricText(List<Map<String, Object>> metricRows, String key) {
        for (Map<String, Object> metricRow : metricRows) {
            String value = text(metricRow.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String metricText(Map<String, Map<String, Object>> metricsByLabel, String... labels) {
        Map<String, Object> metric = findMetric(metricsByLabel, labels);
        return metric == null ? "" : metricValueText(metric);
    }

    private Map<String, Object> findMetric(Map<String, Map<String, Object>> metricsByLabel, String... labels) {
        for (String label : labels) {
            Map<String, Object> metric = metricsByLabel.get(normalizeLabel(label));
            if (metric != null) return metric;
        }
        return null;
    }

    private String metricValueText(Map<String, Object> metric) {
        String value = text(metric.get("metricValue"));
        if (!value.isBlank()) return value;
        Object numeric = metric.get("metricNumeric");
        return numeric == null ? "" : stripTrailingZeros(String.valueOf(numeric));
    }

    private double averagePercent(List<ReportRow> rows, PercentExtractor extractor) {
        double sum = 0D;
        int count = 0;
        for (ReportRow row : rows) {
            double value = extractor.extract(row);
            if (!Double.isNaN(value)) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0D : sum / count;
    }

    private String metricGroupLabel(String group) {
        if ("OVERVIEW".equalsIgnoreCase(group)) return "数据页1 · 总览指标";
        if ("OVERVIEW_CHART".equalsIgnoreCase(group)) return "数据页2 · 趋势/粉丝图表";
        if ("FLOW_ANALYSIS".equalsIgnoreCase(group)) return "数据页3 · 流量分析";
        return group;
    }

    private String assetLabel(Object assetId) {
        Long value = numberToLong(assetId);
        return value == null ? "" : "来源图片 #" + value;
    }

    private String platformLabel(String code) {
        if ("XIAOHONGSHU".equalsIgnoreCase(code)) return "小红书";
        if ("WECHAT_CHANNEL".equalsIgnoreCase(code)) return "视频号";
        return "抖音";
    }

    private String contentTypeLabel(String contentType) {
        return "VIDEO".equalsIgnoreCase(contentType) ? "视频" : "图文";
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        String value = contentType.trim().toUpperCase(Locale.ROOT);
        if (Objects.equals(value, "IMAGE_TEXT") || Objects.equals(value, "VIDEO")) return value;
        return "";
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]", "").toLowerCase(Locale.ROOT);
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ex) { return null; }
    }

    private long toLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        String cleaned = value.replaceAll("[^0-9.\\-]", "").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned) || ".".equals(cleaned)) return 0L;
        try { return Math.round(Double.parseDouble(cleaned)); } catch (Exception ex) { return 0L; }
    }

    private double toDouble(String value) {
        if (value == null || value.isBlank()) return Double.NaN;
        String cleaned = value.replaceAll("[^0-9.\\-]", "").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned) || ".".equals(cleaned)) return Double.NaN;
        try { return Double.parseDouble(cleaned); } catch (Exception ex) { return Double.NaN; }
    }

    private String stripTrailingZeros(String value) {
        if (value == null) return "";
        if (!value.contains(".")) return value;
        return value.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) return value;
        }
        return "";
    }

    private static class ReportRow {
        String date = "";
        String packageName = "";
        String platformCode = "";
        String platformName = "";
        String accountName = "";
        String platformUserId = "";
        String subTopicName = "";
        Long contentId;
        String contentType = "";
        String contentTypeLabel = "";
        String contentTitle = "";
        String contentSummary = "";
        String operatorNames = "";
        String mediaNames = "";
        String recognitionStatus = "";
        String createdAt = "";
        String views = "";
        String likes = "";
        String comments = "";
        String favorites = "";
        String shares = "";
        String newFollowers = "";
        String coverClickRate = "";
        String copyExpandRate = "";
        String copyReadRate = "";
        String commentEnterRate = "";
        String completionRate = "";
        String fiveSecondCompletionRate = "";
        long viewsLong;
        long likesLong;
        long commentsLong;
        long favoritesLong;
        long sharesLong;
        long newFollowersLong;
        double coverClickRateDouble = Double.NaN;
        double copyExpandRateDouble = Double.NaN;
        double copyReadRateDouble = Double.NaN;
        double commentEnterRateDouble = Double.NaN;
        double completionRateDouble = Double.NaN;
        double fiveSecondCompletionRateDouble = Double.NaN;
        List<Map<String, Object>> metricRows = new ArrayList<>();
    }

    @FunctionalInterface
    private interface PercentExtractor {
        double extract(ReportRow row);
    }

    public record DailyReportRequest(LocalDate date, String contentType) {}
}

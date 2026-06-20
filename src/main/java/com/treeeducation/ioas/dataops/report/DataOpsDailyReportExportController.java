package com.treeeducation.ioas.dataops.report;

import com.treeeducation.ioas.dataops.DataOperationMetricService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/reports-export")
public class DataOpsDailyReportExportController {
    private static final List<PlatformSheet> PLATFORM_SHEETS = List.of(
            new PlatformSheet("DOUYIN", "抖音"),
            new PlatformSheet("XIAOHONGSHU", "小红书"),
            new PlatformSheet("WECHAT_CHANNEL", "视频号")
    );

    private static final String[] DAILY_HEADERS = {
            "运营",
            "账号",
            "类型",
            "标题",
            "播放量",
            "点赞量",
            "评论量",
            "收藏量",
            "整体完播率",
            "5S完播率",
            "文案展开率",
            "评论进入率",
            "单帖涨粉量",
            "负责美工",
            "负责口播",
            "发布时间",
            "备注"
    };

    private final JdbcTemplate jdbcTemplate;
    private final DataOperationMetricService metricService;

    public DataOpsDailyReportExportController(JdbcTemplate jdbcTemplate, DataOperationMetricService metricService) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricService = metricService;
    }

    @PostMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','ADMINISTRATIVE')")
    public void exportDailyReport(@RequestBody DailyReportRequest request, HttpServletResponse response) throws IOException {
        LocalDate reportDate = request == null || request.date() == null ? LocalDate.now() : request.date();
        List<ReportRow> rows = loadReportRows(reportDate);
        rows.sort(Comparator
                .comparingInt((ReportRow row) -> platformOrder(row.platformCode))
                .thenComparing(row -> row.operatorNames)
                .thenComparing(row -> row.accountName)
                .thenComparing(row -> row.platformUserId)
                .thenComparing(row -> row.contentId == null ? 0L : row.contentId));

        String fileName = "数据操作日报_" + reportDate + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeDailyWorkbook(workbook, reportDate, rows);
            workbook.write(response.getOutputStream());
        }
    }

    private List<ReportRow> loadReportRows(LocalDate reportDate) {
        List<Map<String, Object>> contents = jdbcTemplate.queryForList("""
                select
                  p.id as package_id,
                  p.topic_date as report_date,
                  p.display_name as package_name,
                  p.operator_names,
                  p.media_names,
                  p.anchor_names,
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
                order by p.id desc, t.id desc, c.id desc
                """, Date.valueOf(reportDate));
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
        row.platformCode = normalizePlatformCode(firstNonBlank(text(content.get("platform_code")), "DOUYIN"));
        row.platformName = platformLabel(row.platformCode);
        row.accountName = firstNonBlank(findFirstMetricText(metricRows, "accountName"), text(content.get("account_name")));
        row.platformUserId = firstNonBlank(findFirstMetricText(metricRows, "platformUserId"), text(content.get("platform_user_id")));
        row.subTopicName = text(content.get("sub_topic_name"));
        row.contentId = contentId;
        row.contentType = firstNonBlank(text(content.get("content_type")), "IMAGE_TEXT");
        row.contentTypeLabel = contentTypeLabel(row.contentType);
        row.contentTitle = firstNonBlank(findFirstMetricText(metricRows, "videoTitle"), findFirstMetricText(metricRows, "contentTitle"), text(content.get("content_title")));
        row.contentSummary = text(content.get("content_summary"));
        row.operatorNames = text(content.get("operator_names"));
        row.mediaNames = text(content.get("media_names"));
        row.anchorNames = text(content.get("anchor_names"));
        row.recognitionStatus = text(content.get("recognition_status"));
        row.createdAt = text(content.get("created_at"));
        row.metricRows = metricRows;

        row.views = metricText(metricsByLabel, "播放量", "浏览量", "播放", "观看量", "阅读量");
        row.likes = metricText(metricsByLabel, "点赞量", "点赞");
        row.comments = metricText(metricsByLabel, "评论量", "评论");
        row.favorites = metricText(metricsByLabel, "收藏量", "收藏");
        row.newFollowers = metricText(metricsByLabel, "单帖涨粉量", "涨粉量", "新增粉丝数", "新增粉丝");
        row.copyExpandRate = metricText(metricsByLabel, "文案展开率");
        row.commentEnterRate = metricText(metricsByLabel, "评论进入率");
        row.completionRate = metricText(metricsByLabel, "整体完播率", "完播率");
        row.fiveSecondCompletionRate = metricText(metricsByLabel, "5S完播率", "5s完播率");
        return row;
    }

    private void writeDailyWorkbook(XSSFWorkbook workbook, LocalDate reportDate, List<ReportRow> rows) {
        Map<String, List<ReportRow>> rowsByPlatform = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            rowsByPlatform.computeIfAbsent(normalizePlatformCode(row.platformCode), ignored -> new ArrayList<>()).add(row);
        }
        for (PlatformSheet platform : PLATFORM_SHEETS) {
            writePlatformSheet(workbook, String.valueOf(reportDate), platform.code(), platform.name(), rowsByPlatform.getOrDefault(platform.code(), List.of()));
        }
    }

    private void writePlatformSheet(XSSFWorkbook workbook, String reportDate, String platformCode, String platformName, List<ReportRow> rows) {
        XSSFSheet sheet = workbook.createSheet(sheetName(platformCode, platformName));
        sheet.createFreezePane(0, 2);
        setColumnWidths(sheet);

        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle bodyStyle = bodyStyle(workbook);
        CellStyle centerStyle = centerStyle(workbook);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(26F);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sheetName(platformCode, platformName));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, DAILY_HEADERS.length - 1));
        for (int i = 1; i < DAILY_HEADERS.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellStyle(titleStyle);
        }

        Row header = sheet.createRow(1);
        header.setHeightInPoints(22F);
        for (int i = 0; i < DAILY_HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(DAILY_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        if (rows.isEmpty()) {
            Row row = sheet.createRow(2);
            Cell cell = row.createCell(0);
            cell.setCellValue(reportDate + " 当日无已确认数据");
            cell.setCellStyle(bodyStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, DAILY_HEADERS.length - 1));
            return;
        }

        int rowIndex = 2;
        int groupStart = rowIndex;
        String currentOperator = rows.get(0).operatorNames;
        int currentCount = 0;

        for (ReportRow item : rows) {
            if (!sameText(currentOperator, item.operatorNames)) {
                mergeOperatorAndRemark(sheet, groupStart, rowIndex - 1, currentCount, centerStyle);
                groupStart = rowIndex;
                currentOperator = item.operatorNames;
                currentCount = 0;
            }
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(24F);
            writeDailyRow(row, item, bodyStyle, centerStyle);
            currentCount++;
        }
        mergeOperatorAndRemark(sheet, groupStart, rowIndex - 1, currentCount, centerStyle);
    }

    private void writeDailyRow(Row row, ReportRow item, CellStyle bodyStyle, CellStyle centerStyle) {
        int c = 0;
        setCell(row, c++, item.operatorNames, centerStyle);
        setCell(row, c++, accountDisplay(item), bodyStyle);
        setCell(row, c++, item.contentTypeLabel, centerStyle);
        setCell(row, c++, titleDisplay(item), bodyStyle);
        setCell(row, c++, countDisplay(item.views), centerStyle);
        setCell(row, c++, countDisplay(item.likes), centerStyle);
        setCell(row, c++, countDisplay(item.comments), centerStyle);
        setCell(row, c++, countDisplay(item.favorites), centerStyle);
        setCell(row, c++, videoRateDisplay(item.completionRate, item), centerStyle);
        setCell(row, c++, videoRateDisplay(item.fiveSecondCompletionRate, item), centerStyle);
        setCell(row, c++, imageTextRateDisplay(item.copyExpandRate, item), centerStyle);
        setCell(row, c++, imageTextRateDisplay(item.commentEnterRate, item), centerStyle);
        setCell(row, c++, countDisplay(item.newFollowers), centerStyle);
        setCell(row, c++, firstNonBlank(item.mediaNames, "/"), centerStyle);
        setCell(row, c++, firstNonBlank(item.anchorNames, "/"), centerStyle);
        setCell(row, c++, firstNonBlank(item.date, "/"), centerStyle);
        setCell(row, c, "", centerStyle);
    }

    private void mergeOperatorAndRemark(XSSFSheet sheet, int startRow, int endRow, int count, CellStyle centerStyle) {
        if (startRow > endRow) return;
        Row start = sheet.getRow(startRow);
        if (start == null) return;
        Cell remark = start.getCell(DAILY_HEADERS.length - 1);
        if (remark == null) remark = start.createCell(DAILY_HEADERS.length - 1);
        remark.setCellValue(count + "条");
        remark.setCellStyle(centerStyle);

        if (endRow > startRow) {
            sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, DAILY_HEADERS.length - 1, DAILY_HEADERS.length - 1));
        }
    }

    private void setCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(firstNonBlank(value, "/"));
        cell.setCellStyle(style);
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

    private String accountDisplay(ReportRow item) {
        if (!item.accountName.isBlank() && !item.platformUserId.isBlank()) return item.accountName + "（" + item.platformUserId + "）";
        return firstNonBlank(item.accountName, item.platformUserId, "/");
    }

    private String titleDisplay(ReportRow item) {
        return firstNonBlank(item.contentTitle, item.contentSummary, "/");
    }

    private String countDisplay(String value) {
        return firstNonBlank(value, "0");
    }

    private String videoRateDisplay(String value, ReportRow item) {
        if (!"VIDEO".equalsIgnoreCase(item.contentType)) return "/";
        return firstNonBlank(value, "/");
    }

    private String imageTextRateDisplay(String value, ReportRow item) {
        if ("VIDEO".equalsIgnoreCase(item.contentType)) return "/";
        return firstNonBlank(value, "/");
    }

    private String normalizePlatformCode(String code) {
        String normalized = firstNonBlank(code, "DOUYIN").trim().toUpperCase(Locale.ROOT);
        if ("XHS".equals(normalized) || "REDNOTE".equals(normalized)) return "XIAOHONGSHU";
        if ("WECHAT".equals(normalized) || "WECHAT_VIDEO".equals(normalized) || "WECHAT_CHANNELS".equals(normalized)) return "WECHAT_CHANNEL";
        if ("DOUYIN".equals(normalized) || "TIKTOK_CHINA".equals(normalized)) return "DOUYIN";
        return normalized;
    }

    private int platformOrder(String code) {
        String normalized = normalizePlatformCode(code);
        for (int i = 0; i < PLATFORM_SHEETS.size(); i++) {
            if (PLATFORM_SHEETS.get(i).code().equals(normalized)) return i;
        }
        return PLATFORM_SHEETS.size();
    }

    private String platformLabel(String code) {
        String normalized = normalizePlatformCode(code);
        if ("XIAOHONGSHU".equals(normalized)) return "小红书";
        if ("WECHAT_CHANNEL".equals(normalized)) return "视频号";
        return "抖音";
    }

    private String sheetName(String platformCode, String platformName) {
        String name = firstNonBlank(platformName, platformLabel(platformCode), "日报");
        name = name.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
        if (name.isBlank()) name = "日报";
        return name.length() > 31 ? name.substring(0, 31) : name;
    }

    private String contentTypeLabel(String contentType) {
        return "VIDEO".equalsIgnoreCase(contentType) ? "视频" : "图文";
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]", "").toLowerCase(Locale.ROOT);
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ex) { return null; }
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

    private boolean sameText(String left, String right) {
        return firstNonBlank(left).equals(firstNonBlank(right));
    }

    private void setColumnWidths(XSSFSheet sheet) {
        int[] widths = {10, 30, 8, 46, 10, 10, 10, 10, 13, 13, 13, 13, 12, 14, 14, 14, 8};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private CellStyle titleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        applyThinBorder(style);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        applyThinBorder(style);
        return style;
    }

    private CellStyle bodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle centerStyle(XSSFWorkbook workbook) {
        CellStyle style = bodyStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private record PlatformSheet(String code, String name) {}

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
        String anchorNames = "";
        String recognitionStatus = "";
        String createdAt = "";
        String views = "";
        String likes = "";
        String comments = "";
        String favorites = "";
        String newFollowers = "";
        String copyExpandRate = "";
        String commentEnterRate = "";
        String completionRate = "";
        String fiveSecondCompletionRate = "";
        List<Map<String, Object>> metricRows = new ArrayList<>();
    }

    public record DailyReportRequest(LocalDate date, String contentType) {}
}

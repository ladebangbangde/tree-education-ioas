package com.treeeducation.ioas.dataops;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/reports-export")
public class DataOperationReportExportController {
    private static final MediaType XLSX_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final List<PlatformSheet> PLATFORMS = List.of(
            new PlatformSheet("DOUYIN", "抖音 ♪", "抖音"),
            new PlatformSheet("XIAOHONGSHU", "小红书", "小红书"),
            new PlatformSheet("WECHAT_CHANNEL", "视频号", "视频号")
    );
    private static final String[] HEADERS = {
            "运营", "账号", "类型", "标题", "播放量", "点赞量", "评论量", "收藏量",
            "整体完播率", "5S完播率", "文案展开率", "评论进入率", "单帖涨粉量",
            "负责美工", "负责口播", "发布时间", "备注"
    };

    private final JdbcTemplate jdbc;

    public DataOperationReportExportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','ADMINISTRATIVE')")
    public ResponseEntity<byte[]> exportDailyReport(@RequestBody(required = false) DailyReportExportRequest request) throws Exception {
        ensureReportColumns();
        LocalDate date = request == null || request.date() == null ? LocalDate.now() : request.date();
        byte[] body = buildWorkbook(date);
        String filename = "数据操作日报_" + date + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentLength(body.length)
                .body(body);
    }

    private byte[] buildWorkbook(LocalDate date) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = createStyles(workbook);
            for (PlatformSheet platform : PLATFORMS) {
                createPlatformSheet(workbook, styles, platform, date);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createPlatformSheet(Workbook workbook, Styles styles, PlatformSheet platform, LocalDate date) {
        Sheet sheet = workbook.createSheet(platform.sheetName());
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 2);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(platform.title());
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(styles.header());
        }

        List<Map<String, Object>> rows = queryRows(platform.code(), date);
        int rowIndex = 2;
        int groupStart = rowIndex;
        String currentOperator = null;

        for (Map<String, Object> row : rows) {
            String operator = text(row.get("operator_names"));
            if (currentOperator != null && !currentOperator.equals(operator)) {
                mergeOperatorGroup(sheet, groupStart, rowIndex - 1, styles.body());
                groupStart = rowIndex;
            }
            currentOperator = operator;
            writeReportRow(sheet, rowIndex++, styles.body(), row, date);
        }
        if (currentOperator != null) {
            mergeOperatorGroup(sheet, groupStart, rowIndex - 1, styles.body());
        }
        if (rows.isEmpty()) {
            Row empty = sheet.createRow(2);
            Cell cell = empty.createCell(0);
            cell.setCellValue("当日暂无" + platform.sheetName() + "数据");
            cell.setCellStyle(styles.body());
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, HEADERS.length - 1));
        }

        int[] widths = {12, 30, 10, 48, 11, 10, 10, 10, 14, 12, 12, 12, 12, 14, 16, 14, 10};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
    }

    private void writeReportRow(Sheet sheet, int rowIndex, CellStyle style, Map<String, Object> row, LocalDate date) {
        Map<String, String> metrics = queryMetrics(number(row.get("content_id")));
        String accountName = firstText(row.get("ocr_account_name"), "未识别账号");
        String accountId = firstText(row.get("ocr_platform_user_id"), "");
        String account = accountId.isBlank() ? accountName : accountName + "（" + accountId + "）";
        String title = firstText(row.get("content_title"), row.get("ocr_content_title"), row.get("sub_topic_name"), "未命名内容");
        String type = "VIDEO".equalsIgnoreCase(text(row.get("content_type"))) ? "视频" : "图文";
        String publishDate = firstText(row.get("content_date"), date.toString());

        Object[] values = {
                firstText(row.get("operator_names"), "无"), account, type, title,
                metric(metrics, "view_count", "viewCount", "play_count", "playCount", "read_count", "readCount"),
                metric(metrics, "like_count", "likeCount"),
                metric(metrics, "comment_count", "commentCount"),
                metric(metrics, "favorite_count", "favoriteCount", "collect_count", "collectCount"),
                metric(metrics, "completion_rate", "completionRate", "finish_rate", "finishRate"),
                metric(metrics, "five_second_completion_rate", "fiveSecondCompletionRate", "five_sec_completion_rate", "fiveSecCompletionRate"),
                metric(metrics, "copy_expand_rate", "copyExpandRate"),
                metric(metrics, "comment_enter_rate", "commentEnterRate"),
                metric(metrics, "follower_gain", "followerGain"),
                firstText(row.get("media_names"), "无"), firstText(row.get("anchor_names"), "无"), publishDate, ""
        };
        Row excelRow = sheet.createRow(rowIndex);
        excelRow.setHeightInPoints(26);
        for (int i = 0; i < values.length; i++) {
            Cell cell = excelRow.createCell(i);
            cell.setCellValue(String.valueOf(values[i]));
            cell.setCellStyle(style);
        }
    }

    private void mergeOperatorGroup(Sheet sheet, int start, int end, CellStyle style) {
        if (start > end) return;
        int count = end - start + 1;
        Row row = sheet.getRow(start);
        if (row != null) {
            Cell remark = row.getCell(16);
            if (remark == null) remark = row.createCell(16);
            remark.setCellValue(count + "条");
            remark.setCellStyle(style);
        }
        if (start == end) return;
        sheet.addMergedRegion(new CellRangeAddress(start, end, 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(start, end, 16, 16));
    }

    private List<Map<String, Object>> queryRows(String platformCode, LocalDate date) {
        return jdbc.queryForList("""
                select p.operator_names,
                       p.media_names,
                       p.anchor_names,
                       pt.ocr_account_name,
                       pt.ocr_platform_user_id,
                       pt.ocr_content_title,
                       pt.sub_topic_name,
                       c.id as content_id,
                       c.content_type,
                       c.content_title,
                       c.content_date
                from data_operation_content c
                join data_operation_topic_package p on p.id = c.package_id
                left join data_operation_platform_topic pt on pt.id = c.platform_topic_id
                where c.content_date = ? and c.platform_code = ?
                order by coalesce(p.operator_names, ''), coalesce(pt.ocr_account_name, ''), c.id
                """, Date.valueOf(date), platformCode);
    }

    private Map<String, String> queryMetrics(Long contentId) {
        if (contentId == null) return Map.of();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select *
                    from data_operation_metric_value
                    where content_id = ?
                    order by coalesce(asset_id, 0), id
                    """, contentId);
            Map<String, String> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String key = text(row.get("metric_key"));
                String value = firstText(row.get("metric_value"), row.get("metric_numeric"));
                if (!key.isBlank() && !value.isBlank()) result.put(key, value);
            }
            return result;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Styles createStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        titleFont.setFontHeightInPoints((short) 16);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 11);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(header);

        CellStyle body = workbook.createCellStyle();
        body.setAlignment(HorizontalAlignment.CENTER);
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        body.setWrapText(true);
        applyBorder(body);
        return new Styles(title, header, body);
    }

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String metric(Map<String, String> metrics, String... keys) {
        for (String key : keys) {
            String value = metrics.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "/";
    }

    private void ensureReportColumns() {
        ensureColumn("data_operation_topic_package", "anchor_user_ids", "alter table data_operation_topic_package add column anchor_user_ids varchar(500) null after media_names");
        ensureColumn("data_operation_topic_package", "anchor_names", "alter table data_operation_topic_package add column anchor_names varchar(500) null after anchor_user_ids");
        ensureColumn("data_operation_content", "content_type", "alter table data_operation_content add column content_type varchar(32) null after platform_code");
        ensureColumn("data_operation_platform_topic", "content_type", "alter table data_operation_platform_topic add column content_type varchar(32) null after platform_name");
        ensureColumn("data_operation_platform_topic", "ocr_account_name", "alter table data_operation_platform_topic add column ocr_account_name varchar(255) null");
        ensureColumn("data_operation_platform_topic", "ocr_platform_user_id", "alter table data_operation_platform_topic add column ocr_platform_user_id varchar(255) null");
        ensureColumn("data_operation_platform_topic", "ocr_content_title", "alter table data_operation_platform_topic add column ocr_content_title varchar(500) null");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbc.queryForObject("""
                    select count(*)
                    from information_schema.columns
                    where table_schema = database() and table_name = ? and column_name = ?
                    """, Integer.class, table, column);
            if (count != null && count == 0) jdbc.execute(ddl);
        } catch (RuntimeException ignored) {}
    }

    private String firstText(Object first, Object second) {
        return firstText(first, second, "");
    }

    private String firstText(Object first, Object second, Object fallback) {
        String a = text(first);
        if (!a.isBlank()) return a;
        String b = text(second);
        if (!b.isBlank()) return b;
        return text(fallback);
    }

    private String firstText(Object first, Object second, Object third, Object fallback) {
        String a = text(first);
        if (!a.isBlank()) return a;
        String b = text(second);
        if (!b.isBlank()) return b;
        String c = text(third);
        if (!c.isBlank()) return c;
        return text(fallback);
    }

    private String text(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).trim();
        if (text.equalsIgnoreCase("null")) return "";
        return text;
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }

    public record DailyReportExportRequest(LocalDate date) {}
    private record PlatformSheet(String code, String title, String sheetName) {}
    private record Styles(CellStyle title, CellStyle header, CellStyle body) {}
}

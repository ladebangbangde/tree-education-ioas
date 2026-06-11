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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/reports")
public class DataOpsExcelReportController {
    private final JdbcTemplate jdbc;

    public DataOpsExcelReportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/export-preview")
    public Map<String, Object> preview(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                       @RequestParam(defaultValue = "ALL") String platform,
                                       @RequestParam(required = false) Long topicPackageId,
                                       @RequestParam(defaultValue = "true") boolean onlyConfirmed) {
        LocalDate reportDate = date != null ? date : LocalDate.now();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", String.valueOf(reportDate));
        data.put("platform", platform);
        data.put("topicPackageId", topicPackageId);
        data.put("onlyConfirmed", onlyConfirmed);
        data.put("totalContentCount", 0);
        data.put("confirmedCount", 0);
        data.put("unconfirmedCount", 0);
        data.put("manualCorrectedCount", 0);
        data.put("platformStats", List.of());
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
        String fileName = "数据操作日报_" + reportDate + ".xlsx";
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet summary = workbook.createSheet("汇总看板");
            Row h1 = summary.createRow(0);
            String[] s1 = {"日期", "平台", "内容数量", "总播放量", "总点赞", "总评论", "总收藏", "总转发", "平均互动率"};
            for (int i = 0; i < s1.length; i++) h1.createCell(i).setCellValue(s1[i]);
            Row r1 = summary.createRow(1);
            r1.createCell(0).setCellValue(String.valueOf(reportDate));
            r1.createCell(1).setCellValue(request.platform() == null ? "ALL" : request.platform());
            for (int i = 2; i < s1.length; i++) r1.createCell(i).setCellValue(0);

            XSSFSheet detail = workbook.createSheet("内容明细");
            Row h2 = detail.createRow(0);
            String[] s2 = {"日期", "主题包", "平台", "平台账号", "内容标题", "子主题", "内容类型", "发布时间", "运营人员", "媒体人员", "数据页1播放量", "数据页1点赞", "数据页1评论", "数据页1收藏", "数据页1转发", "数据页2曝光", "数据页2主页访问", "数据页2涨粉", "数据页3完播率", "数据页3互动率", "OCR置信度", "是否人工修正", "校验人", "入库时间"};
            for (int i = 0; i < s2.length; i++) h2.createCell(i).setCellValue(s2[i]);

            XSSFSheet abnormal = workbook.createSheet("异常数据");
            Row h3 = abnormal.createRow(0);
            h3.createCell(0).setCellValue("内容标题");
            h3.createCell(1).setCellValue("平台");
            h3.createCell(2).setCellValue("异常原因");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
        try {
            jdbc.update("INSERT INTO data_operation_report_export_log(report_date, platform, file_name, total_content_count, confirmed_count, unconfirmed_count, manual_corrected_count, exported_by_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", reportDate, request.platform() == null ? "ALL" : request.platform(), fileName, 0, 0, 0, 0, "system");
        } catch (Exception ignored) {
        }
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
}

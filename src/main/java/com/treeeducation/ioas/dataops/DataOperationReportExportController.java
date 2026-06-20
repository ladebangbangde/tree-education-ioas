package com.treeeducation.ioas.dataops;

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

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/data-ops/reports-export")
public class DataOperationReportExportController {
    private final JdbcTemplate jdbc;

    public DataOperationReportExportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','ADMINISTRATIVE')")
    public ResponseEntity<byte[]> exportDailyReport(@RequestBody(required = false) DailyReportExportRequest request) {
        LocalDate date = request == null || request.date() == null ? LocalDate.now() : request.date();
        String csv = buildCsv(date);
        String filename = "data-operation-daily-report-" + date + ".csv";
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(body.length)
                .body(body);
    }

    private String buildCsv(LocalDate date) {
        StringBuilder sb = new StringBuilder();
        sb.append("metric,value\n");
        sb.append("date,").append(date).append('\n');
        sb.append("packageCount,").append(count("select count(*) from data_operation_topic_package where topic_date = ?", date)).append('\n');
        sb.append("contentCount,").append(count("select count(*) from data_operation_content where content_date = ?", date)).append('\n');
        sb.append("screenshotCount,").append(count("select count(*) from data_operation_asset where asset_type = 'DATA_SCREENSHOT' and date(created_at) = ?", date)).append('\n');
        sb.append("douyinCount,").append(count("select count(*) from data_operation_content where content_date = ? and platform_code = 'DOUYIN'", date)).append('\n');
        sb.append("xiaohongshuCount,").append(count("select count(*) from data_operation_content where content_date = ? and platform_code = 'XIAOHONGSHU'", date)).append('\n');
        sb.append("failedCount,").append(count("select count(*) from data_operation_asset where upload_status = 'failed' and date(created_at) = ?", date)).append('\n');
        return sb.toString();
    }

    private Integer count(String sql, LocalDate date) {
        Integer value = jdbc.queryForObject(sql, Integer.class, Date.valueOf(date));
        return value == null ? 0 : value;
    }

    public record DailyReportExportRequest(LocalDate date) {}
}

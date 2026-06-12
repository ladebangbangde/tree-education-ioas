package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.recognition.ImageRecognitionDtos;
import com.treeeducation.ioas.recognition.ImageRecognitionService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationWorkflowController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ImageRecognitionService imageRecognitionService;
    private final DataOperationMetricService metricService;
    private final DataOperationVideoHierarchyService hierarchyService;
    private final DataOperationAssetStorageService storageService;

    public DataOperationWorkflowController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, ImageRecognitionService imageRecognitionService, DataOperationMetricService metricService, DataOperationVideoHierarchyService hierarchyService, DataOperationAssetStorageService storageService) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.imageRecognitionService = imageRecognitionService;
        this.metricService = metricService;
        this.hierarchyService = hierarchyService;
        this.storageService = storageService;
    }

    @PostConstruct
    public void initSchema() {
        ensureColumn("data_operation_platform_topic", "content_type", "alter table data_operation_platform_topic add column content_type varchar(32) null");
        ensureColumn("data_operation_platform_topic", "account_confirmed_flag", "alter table data_operation_platform_topic add column account_confirmed_flag tinyint(1) not null default 0");
        ensureColumn("data_operation_platform_topic", "confirmed_account_id", "alter table data_operation_platform_topic add column confirmed_account_id bigint null");
        ensureColumn("data_operation_content", "content_type", "alter table data_operation_content add column content_type varchar(32) null");
    }

    @GetMapping("/platform-topics/{topicId}/metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<Map<String, Object>> topicMetrics(@PathVariable Long topicId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("status", metricService.computeTopicStatus(topicId));
        result.put("rows", metricService.listTopicMetrics(topicId));
        return ApiResponse.ok(result);
    }

    @GetMapping("/platform-topics/{topicId}/recognition-status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<Map<String, Object>> topicRecognitionStatus(@PathVariable Long topicId) {
        return ApiResponse.ok(metricService.computeTopicStatus(topicId));
    }

    @PostMapping("/platform-topics/{topicId}/account/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> confirmAccount(@PathVariable Long topicId, @RequestBody ConfirmAccountRequest request) {
        if (request == null || blank(request.accountName()) || blank(request.platformUserId())) throw BusinessException.badRequest("请确认账号名称和平台账号ID");
        Map<String, Object> topic = one("select * from data_operation_platform_topic where id = ?", topicId);
        Long accountId = hierarchyService.upsertAccountFromCover(topicId, str(topic.get("platform_code")), request.accountName().trim(), request.platformUserId().trim());
        jdbc.update("update data_operation_platform_topic set ocr_account_name = ?, ocr_platform_user_id = ?, account_confirmed_flag = 1, confirmed_account_id = ?, updated_at = current_timestamp(6) where id = ?", request.accountName().trim(), request.platformUserId().trim(), accountId, topicId);
        return ApiResponse.ok(Map.of("topicId", topicId, "accountId", accountId, "accountName", request.accountName().trim(), "platformUserId", request.platformUserId().trim(), "status", "CONFIRMED"));
    }

    @PostMapping("/platform-topics/{topicId}/contents/confirm-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> confirmCurrentContent(@PathVariable Long topicId, @RequestBody ConfirmCurrentContentRequest request) {
        Map<String, Object> topic = one("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = num(topic.get("package_id"));
        String platformCode = str(topic.get("platform_code"));
        String contentType = "VIDEO".equalsIgnoreCase(request == null ? null : request.contentType()) ? "VIDEO" : "IMAGE_TEXT";
        String title = !blank(request == null ? null : request.contentTitle()) ? request.contentTitle().trim() : first(str(topic.get("ocr_content_title")), str(topic.get("ocr_title")), str(topic.get("sub_topic_name")), "未命名内容");
        LocalDate contentDate = request == null || request.contentDate() == null ? LocalDate.now() : request.contentDate();
        String summary = request == null ? null : request.contentSummary();
        List<Map<String, Object>> rows = jdbc.queryForList("select * from data_operation_content where platform_topic_id = ? and content_type = ? order by id desc", topicId, contentType);
        Long contentId;
        if (rows.isEmpty()) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            namedJdbc.update("insert into data_operation_content(package_id, platform_topic_id, platform_code, content_type, content_title, content_summary, content_date, recognition_status) values (:packageId,:topicId,:platformCode,:contentType,:title,:summary,:contentDate,'pending')", new MapSqlParameterSource().addValue("packageId", packageId).addValue("topicId", topicId).addValue("platformCode", platformCode).addValue("contentType", contentType).addValue("title", title).addValue("summary", summary).addValue("contentDate", Date.valueOf(contentDate)), keyHolder);
            contentId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        } else {
            contentId = num(rows.get(0).get("id"));
            jdbc.update("update data_operation_content set content_type = ?, content_title = ?, content_summary = ?, content_date = ?, updated_at = current_timestamp(6) where id = ?", contentType, title, summary, Date.valueOf(contentDate), contentId);
        }
        jdbc.update("update data_operation_platform_topic set content_type = ?, ocr_content_title = ?, updated_at = current_timestamp(6) where id = ?", contentType, title, topicId);
        return ApiResponse.ok(one("select * from data_operation_content where id = ?", contentId));
    }

    @PostMapping("/assets/{assetId}/recognize-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<ImageRecognitionDtos.Response> recognizeCurrent(@PathVariable Long assetId, @RequestParam(required = false) String platform, @RequestParam(required = false) String scene) {
        return ApiResponse.ok(imageRecognitionService.recognizeDataAsset(assetId, platform, scene));
    }

    @DeleteMapping("/assets/{assetId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> deleteAsset(@PathVariable Long assetId) {
        Map<String, Object> asset = one("select * from data_operation_asset where id = ?", assetId);
        Long contentId = num(asset.get("content_id"));
        storageService.delete(str(asset.get("bucket_name")), str(asset.get("object_key")));
        jdbc.update("delete from data_operation_metric_value where asset_id = ?", assetId);
        jdbc.update("delete from data_operation_asset where id = ?", assetId);
        if (contentId != null) {
            Integer count = jdbc.queryForObject("select count(*) from data_operation_asset where content_id = ? and asset_type = 'DATA_SCREENSHOT'", Integer.class, contentId);
            jdbc.update("update data_operation_content set screenshot_count = ?, updated_at = current_timestamp(6) where id = ?", count == null ? 0 : count, contentId);
        }
        return ApiResponse.ok(Map.of("deleted", true, "assetId", assetId));
    }

    @GetMapping("/assets/{assetId}/file")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public void assetFile(@PathVariable Long assetId, HttpServletResponse response) throws IOException {
        Map<String, Object> asset = one("select * from data_operation_asset where id = ?", assetId);
        byte[] bytes = storageService.readBytes(str(asset.get("bucket_name")), str(asset.get("object_key")));
        response.setContentType(first(str(asset.get("mime_type")), "application/octet-stream"));
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + URLEncoder.encode(first(str(asset.get("original_filename")), "asset.bin"), StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", Integer.class, table, column);
        if (count != null && count == 0) jdbc.execute(ddl);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String str(Object value) { return value == null ? null : String.valueOf(value); }
    private Long num(Object value) { if (value instanceof Number n) return n.longValue(); if (value == null) return null; return Long.parseLong(String.valueOf(value)); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return null; }

    public record ConfirmAccountRequest(String accountName, String platformUserId) {}
    public record ConfirmCurrentContentRequest(String contentTitle, String contentSummary, LocalDate contentDate, String contentType) {}
}

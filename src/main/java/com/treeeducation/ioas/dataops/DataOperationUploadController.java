package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationUploadController {
    private static final String BUCKET = "tree-education-data-operation";
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public DataOperationUploadController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @PostMapping("/platform-topics/{topicId}/cover")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> uploadCover(@PathVariable Long topicId,
                                                        @RequestPart("file") MultipartFile file,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("请上传视频封面");
        Map<String, Object> topic = one("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = number(topic.get("package_id"));
        Map<String, Object> pkg = one("select * from data_operation_topic_package where id = ?", packageId);
        String objectKey = buildObjectKey(pkg, topic, null, "cover", file.getOriginalFilename());
        Long taskId = createUploadTask("数据封面上传", "data_cover_upload", packageId, objectKey, file, principal);
        Long assetId = createAsset(packageId, topicId, null, "COVER", objectKey, file, taskId, principal);
        jdbc.update("update data_operation_platform_topic set cover_asset_id = ?, cover_image_url = ?, ocr_status = 'pending', updated_at = current_timestamp(6) where id = ?",
                assetId, objectKey, topicId);
        Map<String, Object> asset = one("select * from data_operation_asset where id = ?", assetId);
        asset.put("minioPath", objectKey);
        asset.put("ocrStatus", "pending");
        return ApiResponse.ok(asset);
    }

    @PostMapping("/contents/{contentId}/screenshots")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<List<Map<String, Object>>> uploadScreenshots(@PathVariable Long contentId,
                                                                    @RequestPart("files") List<MultipartFile> files,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        if (files == null || files.isEmpty()) throw BusinessException.badRequest("请上传数据截图");
        Map<String, Object> content = one("select * from data_operation_content where id = ?", contentId);
        Long packageId = number(content.get("package_id"));
        Long topicId = number(content.get("platform_topic_id"));
        Map<String, Object> pkg = one("select * from data_operation_topic_package where id = ?", packageId);
        Map<String, Object> topic = one("select * from data_operation_platform_topic where id = ?", topicId);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            String objectKey = buildObjectKey(pkg, topic, content, "screenshots", file.getOriginalFilename());
            Long taskId = createUploadTask("数据截图上传", "data_screenshot_upload", packageId, objectKey, file, principal);
            createAsset(packageId, topicId, contentId, "DATA_SCREENSHOT", objectKey, file, taskId, principal);
        }
        jdbc.update("update data_operation_content set screenshot_count = (select count(*) from data_operation_asset where content_id = ? and asset_type = 'DATA_SCREENSHOT'), recognition_status = 'pending', updated_at = current_timestamp(6) where id = ?", contentId, contentId);
        return ApiResponse.ok(jdbc.queryForList("select * from data_operation_asset where content_id = ? order by id desc", contentId));
    }

    @PostMapping("/assets/{assetId}/retry")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> retryAsset(@PathVariable Long assetId) {
        jdbc.update("update data_operation_asset set upload_status = 'created', retry_count = retry_count + 1 where id = ?", assetId);
        return ApiResponse.ok(one("select * from data_operation_asset where id = ?", assetId));
    }

    private Long createUploadTask(String title, String type, Long packageId, String objectKey, MultipartFile file, UserPrincipal principal) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type)
                .addValue("title", title)
                .addValue("taskType", type)
                .addValue("roleType", "data")
                .addValue("relatedPackageId", packageId)
                .addValue("assigneeId", principal == null ? 0L : principal.id())
                .addValue("assigneeName", principal == null ? "system" : principal.displayName())
                .addValue("status", "success")
                .addValue("progress", 100)
                .addValue("bucket", BUCKET)
                .addValue("objectKey", objectKey)
                .addValue("publicUrl", objectKey)
                .addValue("fileName", file.getOriginalFilename())
                .addValue("fileSize", file.getSize())
                .addValue("uploadedBytes", file.getSize())
                .addValue("now", Instant.now());
        namedJdbc.update("""
                insert into ioas_task
                (type, title, task_type, role_type, related_package_id, assignee_id, assignee_name, status, progress,
                 upload_bucket_name, upload_object_key, upload_public_url, file_name, file_size, uploaded_bytes, created_at, updated_at, completed_at)
                values (:type, :title, :taskType, :roleType, :relatedPackageId, :assigneeId, :assigneeName, :status, :progress,
                        :bucket, :objectKey, :publicUrl, :fileName, :fileSize, :uploadedBytes, :now, :now, :now)
                """, params, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long createAsset(Long packageId, Long topicId, Long contentId, String assetType, String objectKey, MultipartFile file, Long taskId, UserPrincipal principal) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("packageId", packageId)
                .addValue("topicId", topicId)
                .addValue("contentId", contentId)
                .addValue("assetType", assetType)
                .addValue("filename", file.getOriginalFilename())
                .addValue("bucket", BUCKET)
                .addValue("objectKey", objectKey)
                .addValue("publicUrl", objectKey)
                .addValue("mime", file.getContentType())
                .addValue("size", file.getSize())
                .addValue("taskId", taskId)
                .addValue("createdBy", principal == null ? 0L : principal.id());
        namedJdbc.update("""
                insert into data_operation_asset
                (package_id, platform_topic_id, content_id, asset_type, original_filename, bucket_name, object_key, public_url, mime_type, file_size, upload_status, retry_count, task_id, created_by)
                values (:packageId, :topicId, :contentId, :assetType, :filename, :bucket, :objectKey, :publicUrl, :mime, :size, 'success', 0, :taskId, :createdBy)
                """, params, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private String buildObjectKey(Map<String, Object> pkg, Map<String, Object> topic, Map<String, Object> content, String folder, String filename) {
        LocalDate date = LocalDate.parse(String.valueOf(pkg.get("topic_date")));
        String packageFolder = safe(String.valueOf(pkg.get("folder_name")));
        String coverTheme = safe(String.valueOf(topic.getOrDefault("ocr_title", "cover_pending")));
        String platform = safe(String.valueOf(topic.get("platform_name")));
        String contentName = content == null ? "cover" : safe(String.valueOf(content.get("content_title")));
        String safeName = safe(filename == null ? "file" : filename);
        return "data-operation/%d/%02d/%02d/%s/%s/%s/%s/%s/%s".formatted(
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(), packageFolder, coverTheme, platform, contentName, folder, System.currentTimeMillis() + "_" + safeName);
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("_+", "_");
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }
}

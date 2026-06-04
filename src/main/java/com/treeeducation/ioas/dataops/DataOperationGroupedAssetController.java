package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.task.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationGroupedAssetController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TaskService taskService;

    @Value("${app.upload.base-dir:/app/uploads}")
    private String uploadBaseDir;
    @Value("${app.upload.public-prefix:/uploads}")
    private String uploadPublicPrefix;
    @Value("${app.upload.bucket:data-operation}")
    private String uploadBucket;

    public DataOperationGroupedAssetController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, TaskService taskService) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.taskService = taskService;
    }

    @PostMapping(value = "/contents/{contentId}/screenshots", params = "assetGroup")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> uploadGroupedScreenshots(@PathVariable Long contentId,
                                                                     @RequestParam("files") List<MultipartFile> files,
                                                                     @RequestParam("assetGroup") String assetGroup,
                                                                     @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        if (files == null || files.isEmpty()) throw BusinessException.badRequest("请选择要上传的数据截图");
        String group = normalizeAssetGroup(assetGroup);
        Map<String, Object> content = queryOne("select * from data_operation_content where id = ?", contentId);
        Long packageId = ((Number) content.get("package_id")).longValue();
        Long topicId = ((Number) content.get("platform_topic_id")).longValue();
        Map<String, Object> pkg = queryOne("select * from data_operation_topic_package where id = ?", packageId);
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        List<Map<String, Object>> assets = new ArrayList<>();
        int index = 1;
        for (MultipartFile file : files) {
            String objectKey = buildObjectKey(pkg, topic, content, group.toLowerCase(Locale.ROOT), String.format("%03d_%s", index++, safeFileName(file.getOriginalFilename())));
            Map<String, Object> asset = storeAsset(file, packageId, topicId, contentId, "DATA_SCREENSHOT", objectKey, principal);
            asset.put("asset_group", group);
            asset.put("assetGroup", group);
            assets.add(asset);
            taskService.createDataScreenshotUploadTask(packageId, objectToString(content.get("content_title")), objectToString(asset.get("original_filename")),
                    numberToLong(asset.get("file_size")), objectToString(asset.get("bucket_name")), objectToString(asset.get("object_key")),
                    objectToString(asset.get("public_url")), currentUserId(principal), currentUserName(principal));
        }
        jdbc.update("update data_operation_content set screenshot_count = screenshot_count + ?, recognition_status = 'pending', updated_at = current_timestamp(6) where id = ?", assets.size(), contentId);
        Map<String, Object> response = queryOne("select * from data_operation_content where id = ?", contentId);
        response.put("assets", assets);
        return ApiResponse.ok(response);
    }

    private Map<String, Object> storeAsset(MultipartFile file, Long packageId, Long topicId, Long contentId, String assetType, String objectKey, UserPrincipal principal) throws IOException {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件不能为空");
        String originalFilename = safeFileName(file.getOriginalFilename());
        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        Path base = Path.of(uploadBaseDir).normalize().toAbsolutePath();
        Path target = base.resolve(objectKey).normalize().toAbsolutePath();
        if (!target.startsWith(base)) throw BusinessException.badRequest("非法文件路径");
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        String prefix = uploadPublicPrefix.endsWith("/") ? uploadPublicPrefix.substring(0, uploadPublicPrefix.length() - 1) : uploadPublicPrefix;
        String publicUrl = prefix + "/" + objectKey.replace((char) 92, '/');
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("packageId", packageId)
                .addValue("topicId", topicId)
                .addValue("contentId", contentId)
                .addValue("assetType", assetType)
                .addValue("originalFilename", originalFilename)
                .addValue("bucketName", uploadBucket)
                .addValue("objectKey", objectKey)
                .addValue("publicUrl", publicUrl)
                .addValue("mimeType", mimeType)
                .addValue("fileSize", file.getSize())
                .addValue("createdBy", currentUserId(principal));
        namedJdbc.update("""
                insert into data_operation_asset
                (package_id, platform_topic_id, content_id, asset_type, original_filename, bucket_name, object_key, public_url, mime_type, file_size, upload_status, created_by)
                values (:packageId, :topicId, :contentId, :assetType, :originalFilename, :bucketName, :objectKey, :publicUrl, :mimeType, :fileSize, 'success', :createdBy)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return queryOne("select * from data_operation_asset where id = ?", id);
    }

    private String normalizeAssetGroup(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("DOUYIN_OVERVIEW".equals(code)) return "DOUYIN_OVERVIEW";
        if ("DOUYIN_OVERVIEW_CHART".equals(code)) return "DOUYIN_OVERVIEW_CHART";
        if ("DOUYIN_FLOW_ANALYSIS".equals(code)) return "DOUYIN_FLOW_ANALYSIS";
        throw BusinessException.badRequest("截图类型只支持：总览、总览图表数据、流量分析");
    }

    private String buildObjectKey(Map<String, Object> pkg, Map<String, Object> topic, Map<String, Object> content, String folder, String filename) {
        LocalDate date = toLocalDate(pkg.get("topic_date"));
        String packageFolder = safeFolder(String.valueOf(pkg.get("folder_name")));
        String coverTopic = safeFolder(String.valueOf(Optional.ofNullable(topic.get("ocr_title")).orElse(topic.get("sub_topic_name"))));
        String platformName = safeFolder(String.valueOf(topic.get("platform_name")));
        String contentName = content == null ? "pending_content" : safeFolder(String.valueOf(content.get("content_title")));
        return String.join("/", "data-operation", String.valueOf(date.getYear()), String.format("%02d", date.getMonthValue()), String.format("%02d", date.getDayOfMonth()), packageFolder, coverTopic, platformName, contentName, folder, UUID.randomUUID() + "_" + safeFileName(filename));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDate localDate) return localDate;
        return LocalDate.parse(String.valueOf(value));
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String safeFolder(String value) { return cleanPathPart(value == null ? "未命名" : value); }
    private String safeFileName(String value) { return cleanPathPart(value == null || value.isBlank() ? "upload.bin" : value); }
    private String cleanPathPart(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return "未命名";
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.trim().toCharArray()) {
            if (Character.isWhitespace(c) || c == '/' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' || c == (char) 92) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();
        while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned.isBlank() ? "未命名" : cleaned.substring(0, Math.min(cleaned.length(), 80));
    }

    private String objectToString(Object value) { return value == null ? null : String.valueOf(value); }
    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }
    private Long currentUserId(UserPrincipal principal) { return principal == null ? 0L : principal.id(); }
    private String currentUserName(UserPrincipal principal) { return principal == null ? "system" : principal.userName(); }
}

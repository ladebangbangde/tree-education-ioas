package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.recognition.ImageRecognitionDtos;
import com.treeeducation.ioas.recognition.ImageRecognitionService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TaskService taskService;
    private final ImageRecognitionService imageRecognitionService;

    @Value("${app.upload.base-dir:/app/uploads}")
    private String uploadBaseDir;
    @Value("${app.upload.public-prefix:/uploads}")
    private String uploadPublicPrefix;
    @Value("${app.upload.bucket:data-operation}")
    private String uploadBucket;

    public DataOperationController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, TaskService taskService, ImageRecognitionService imageRecognitionService) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.taskService = taskService;
        this.imageRecognitionService = imageRecognitionService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<List<Map<String, Object>>> userOptions(@RequestParam(required = false) String role) {
        String roleCode = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("select id, username, display_name, department, role_code from sys_user where status = 'ACTIVE'");
        if (!roleCode.isBlank()) {
            if (!List.of("MEDIA", "OPERATOR", "DATA", "ADMINISTRATIVE", "CONSULTANT").contains(roleCode)) {
                throw BusinessException.badRequest("不支持的角色类型");
            }
            sql.append(" and role_code = :roleCode");
            params.addValue("roleCode", roleCode);
        }
        sql.append(" order by role_code asc, id desc");
        return ApiResponse.ok(namedJdbc.queryForList(sql.toString(), params));
    }

    @GetMapping("/packages")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<List<Map<String, Object>>> listPackages(@RequestParam(required = false) String date,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        StringBuilder sql = new StringBuilder("select * from data_operation_topic_package where 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (date != null && !date.isBlank()) {
            sql.append(" and topic_date = :date");
            params.addValue("date", Date.valueOf(LocalDate.parse(date)));
        }
        if (principal != null && !"SUPER_ADMIN".equalsIgnoreCase(principal.role())) {
            if ("DATA".equalsIgnoreCase(principal.role())) {
                sql.append(" and created_by = :uid");
                params.addValue("uid", principal.id());
            } else if ("MEDIA".equalsIgnoreCase(principal.role())) {
                sql.append(" and concat(',', media_user_ids, ',') like :uidLike");
                params.addValue("uidLike", "%" + principal.id() + "%");
            } else if ("OPERATOR".equalsIgnoreCase(principal.role())) {
                sql.append(" and concat(',', operator_user_ids, ',') like :uidLike");
                params.addValue("uidLike", "%" + principal.id() + "%");
            }
        }
        sql.append(" order by topic_date desc, id desc");
        return ApiResponse.ok(namedJdbc.queryForList(sql.toString(), params));
    }

    @PostMapping("/packages")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> createPackage(@RequestBody CreatePackageRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        if (request == null) throw BusinessException.badRequest("请求不能为空");
        LocalDate topicDate = request.topicDate() == null ? LocalDate.now() : request.topicDate();
        List<Long> operatorIds = cleanIds(request.operatorUserIds());
        List<Long> mediaIds = cleanIds(request.mediaUserIds());
        if (operatorIds.isEmpty()) throw BusinessException.badRequest("请选择运营人员");
        if (mediaIds.isEmpty()) throw BusinessException.badRequest("请选择媒体人员");

        String operatorNames = namesOf(operatorIds);
        String mediaNames = namesOf(mediaIds);
        String displayName = operatorNames + "+" + mediaNames + "+" + topicDate;
        String folderName = safeFolder(displayName);
        String packageNo = "DOP" + topicDate.toString().replace("-", "") + String.format("%04d", Math.abs(Objects.hash(displayName, System.nanoTime())) % 10000);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("packageNo", packageNo)
                .addValue("topicDate", Date.valueOf(topicDate))
                .addValue("displayName", displayName)
                .addValue("folderName", folderName)
                .addValue("operatorUserIds", joinIds(operatorIds))
                .addValue("operatorNames", operatorNames)
                .addValue("mediaUserIds", joinIds(mediaIds))
                .addValue("mediaNames", mediaNames)
                .addValue("createdBy", currentUserId(principal))
                .addValue("createdByName", currentUserName(principal));
        namedJdbc.update("""
                insert into data_operation_topic_package
                (package_no, topic_date, display_name, folder_name, operator_user_ids, operator_names, media_user_ids, media_names, created_by, created_by_name)
                values (:packageNo, :topicDate, :displayName, :folderName, :operatorUserIds, :operatorNames, :mediaUserIds, :mediaNames, :createdBy, :createdByName)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return ApiResponse.ok(detailPackage(id));
    }

    @GetMapping("/packages/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<Map<String, Object>> getPackage(@PathVariable Long id) {
        return ApiResponse.ok(detailPackage(id));
    }

    @PostMapping("/packages/{packageId}/platform-topics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> createPlatformTopic(@PathVariable Long packageId,
                                                               @RequestBody CreatePlatformTopicRequest request) {
        detailPackage(packageId);
        String platformCode = normalizePlatform(request.platformCode());
        String platformName = platformName(platformCode);
        String subTopicName = request.subTopicName() == null || request.subTopicName().isBlank() ? platformName + "子主题" : request.subTopicName().trim();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("packageId", packageId)
                .addValue("platformCode", platformCode)
                .addValue("platformName", platformName)
                .addValue("subTopicName", subTopicName);
        namedJdbc.update("""
                insert into data_operation_platform_topic
                (package_id, platform_code, platform_name, sub_topic_name)
                values (:packageId, :platformCode, :platformName, :subTopicName)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return ApiResponse.ok(queryOne("select * from data_operation_platform_topic where id = ?", id));
    }

    @GetMapping("/packages/{packageId}/platform-topics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<List<Map<String, Object>>> listPlatformTopics(@PathVariable Long packageId) {
        return ApiResponse.ok(jdbc.queryForList("select * from data_operation_platform_topic where package_id = ? order by id desc", packageId));
    }

    @PostMapping("/platform-topics/{topicId}/cover")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> uploadCover(@PathVariable Long topicId,
                                                        @RequestParam("file") MultipartFile file,
                                                        @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = ((Number) topic.get("package_id")).longValue();
        Map<String, Object> pkg = queryOne("select * from data_operation_topic_package where id = ?", packageId);
        String objectKey = buildObjectKey(pkg, topic, null, "cover", file.getOriginalFilename());
        Map<String, Object> asset = storeAsset(file, packageId, topicId, null, "COVER", objectKey, principal);
        jdbc.update("update data_operation_platform_topic set cover_asset_id = ?, cover_image_url = ?, ocr_status = 'pending', updated_at = current_timestamp(6) where id = ?",
                asset.get("id"), asset.get("public_url"), topicId);
        taskService.createDataCoverUploadTask(packageId, objectToString(topic.get("sub_topic_name")), objectToString(asset.get("original_filename")),
                numberToLong(asset.get("file_size")), objectToString(asset.get("bucket_name")), objectToString(asset.get("object_key")),
                objectToString(asset.get("public_url")), currentUserId(principal), currentUserName(principal));
        Map<String, Object> response = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        response.put("asset", asset);
        return ApiResponse.ok(response);
    }

    @PostMapping("/contents/{contentId}/screenshots")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','OPERATOR','MEDIA')")
    public ApiResponse<Map<String, Object>> uploadScreenshots(@PathVariable Long contentId,
                                                              @RequestParam("files") List<MultipartFile> files,
                                                              @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        if (files == null || files.isEmpty()) throw BusinessException.badRequest("请选择要上传的数据截图");
        Map<String, Object> content = queryOne("select * from data_operation_content where id = ?", contentId);
        Long packageId = ((Number) content.get("package_id")).longValue();
        Long topicId = ((Number) content.get("platform_topic_id")).longValue();
        Map<String, Object> pkg = queryOne("select * from data_operation_topic_package where id = ?", packageId);
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        List<Map<String, Object>> assets = new ArrayList<>();
        int index = 1;
        for (MultipartFile file : files) {
            String objectKey = buildObjectKey(pkg, topic, content, "screenshots", String.format("%03d_%s", index++, safeFileName(file.getOriginalFilename())));
            Map<String, Object> asset = storeAsset(file, packageId, topicId, contentId, "DATA_SCREENSHOT", objectKey, principal);
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

    @PostMapping("/platform-topics/{topicId}/contents/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> confirmContent(@PathVariable Long topicId,
                                                           @RequestBody ConfirmContentRequest request) {
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = ((Number) topic.get("package_id")).longValue();
        String platformCode = String.valueOf(topic.get("platform_code"));
        String title = request.contentTitle() == null || request.contentTitle().isBlank()
                ? String.valueOf(topic.get("sub_topic_name"))
                : request.contentTitle().trim();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("packageId", packageId)
                .addValue("topicId", topicId)
                .addValue("platformCode", platformCode)
                .addValue("title", title)
                .addValue("summary", request.contentSummary())
                .addValue("contentDate", Date.valueOf(request.contentDate() == null ? LocalDate.now() : request.contentDate()));
        namedJdbc.update("""
                insert into data_operation_content
                (package_id, platform_topic_id, platform_code, content_title, content_summary, content_date)
                values (:packageId, :topicId, :platformCode, :title, :summary, :contentDate)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return ApiResponse.ok(queryOne("select * from data_operation_content where id = ?", id));
    }

    @PostMapping("/assets/{assetId}/recognize")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<ImageRecognitionDtos.Response> recognizeAsset(@PathVariable Long assetId,
                                                                     @RequestParam(required = false) String platform,
                                                                     @RequestParam(required = false) String scene) {
        return ApiResponse.ok(imageRecognitionService.recognizeDataAsset(assetId, platform, scene));
    }

    @PostMapping("/platform-topics/{topicId}/generate-current-data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> generateCurrentTopicData(@PathVariable Long topicId) {
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = ((Number) topic.get("package_id")).longValue();
        String platformCode = normalizePlatform(objectToString(topic.get("platform_code")));
        int coverRecognized = 0;
        int screenshotsRecognized = 0;
        int skipped = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        Long coverAssetId = numberToLong(topic.get("cover_asset_id"));
        String coverStatus = objectToString(topic.get("ocr_status"));
        if (coverAssetId != null && !isRecognizedStatus(coverStatus)) {
            try {
                imageRecognitionService.recognizeDataAsset(coverAssetId, platformCode, "CONTENT_DETAIL");
                coverRecognized++;
            } catch (RuntimeException ex) {
                failures.add(Map.of("assetId", coverAssetId, "type", "COVER", "message", ex.getMessage()));
            }
        } else if (coverAssetId == null) {
            skipped++;
        }

        List<Map<String, Object>> screenshotAssets = jdbc.queryForList("""
                select * from data_operation_asset
                where platform_topic_id = ? and asset_type = 'DATA_SCREENSHOT'
                order by id asc
                """, topicId);
        for (Map<String, Object> asset : screenshotAssets) {
            Long assetId = numberToLong(asset.get("id"));
            if (assetId == null) {
                skipped++;
                continue;
            }
            if (isRecognizedStatus(objectToString(asset.get("upload_status")))) {
                skipped++;
                continue;
            }
            try {
                imageRecognitionService.recognizeDataAsset(assetId, platformCode, "CONTENT_DETAIL");
                screenshotsRecognized++;
            } catch (RuntimeException ex) {
                failures.add(Map.of("assetId", assetId, "type", "DATA_SCREENSHOT", "message", ex.getMessage()));
            }
        }

        if (screenshotsRecognized > 0) {
            jdbc.update("""
                    update data_operation_content
                    set recognition_status = 'success', updated_at = current_timestamp(6)
                    where platform_topic_id = ? and exists (
                        select 1 from data_operation_asset a
                        where a.content_id = data_operation_content.id
                          and a.asset_type = 'DATA_SCREENSHOT'
                          and a.upload_status = 'recognized'
                    )
                    """, topicId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("packageId", packageId);
        result.put("coverRecognized", coverRecognized);
        result.put("screenshotsRecognized", screenshotsRecognized);
        result.put("skipped", skipped);
        result.put("failed", failures.size());
        result.put("failures", failures);
        result.put("package", detailPackage(packageId));
        return ApiResponse.ok(result);
    }

    @PostMapping("/reports/daily")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> generateDailyReport(@RequestBody DailyReportRequest request,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        LocalDate date = request.date() == null ? LocalDate.now() : request.date();
        Integer packageCount = jdbc.queryForObject("select count(*) from data_operation_topic_package where topic_date = ?", Integer.class, Date.valueOf(date));
        Integer contentCount = jdbc.queryForObject("select count(*) from data_operation_content where content_date = ?", Integer.class, Date.valueOf(date));
        Integer screenshotCount = jdbc.queryForObject("select count(*) from data_operation_asset where asset_type = 'DATA_SCREENSHOT' and date(created_at) = ?", Integer.class, Date.valueOf(date));
        Integer douyinCount = jdbc.queryForObject("select count(*) from data_operation_content where content_date = ? and platform_code = 'DOUYIN'", Integer.class, Date.valueOf(date));
        Integer xiaohongshuCount = jdbc.queryForObject("select count(*) from data_operation_content where content_date = ? and platform_code = 'XIAOHONGSHU'", Integer.class, Date.valueOf(date));
        Integer failedCount = jdbc.queryForObject("select count(*) from data_operation_asset where upload_status = 'failed' and date(created_at) = ?", Integer.class, Date.valueOf(date));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("date", Date.valueOf(date))
                .addValue("packageCount", packageCount)
                .addValue("contentCount", contentCount)
                .addValue("screenshotCount", screenshotCount)
                .addValue("douyinCount", douyinCount)
                .addValue("xiaohongshuCount", xiaohongshuCount)
                .addValue("failedCount", failedCount)
                .addValue("createdBy", currentUserId(principal))
                .addValue("summaryJson", String.format("{\"packageCount\":%d,\"contentCount\":%d,\"screenshotCount\":%d}", packageCount, contentCount, screenshotCount));
        namedJdbc.update("""
                insert into data_operation_daily_report
                (report_date, package_count, content_count, screenshot_count, douyin_count, xiaohongshu_count, failed_count, report_status, summary_json, created_by)
                values (:date, :packageCount, :contentCount, :screenshotCount, :douyinCount, :xiaohongshuCount, :failedCount, 'created', :summaryJson, :createdBy)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        taskService.createDataDailyReportTask(date, currentUserId(principal), currentUserName(principal));
        return ApiResponse.ok(queryOne("select * from data_operation_daily_report where id = ?", id));
    }

    private Map<String, Object> detailPackage(Long id) {
        Map<String, Object> row = queryOne("select * from data_operation_topic_package where id = ?", id);
        row.put("platformTopics", jdbc.queryForList("select * from data_operation_platform_topic where package_id = ? order by id desc", id));
        row.put("contents", jdbc.queryForList("select * from data_operation_content where package_id = ? order by id desc", id));
        row.put("assets", jdbc.queryForList("select * from data_operation_asset where package_id = ? order by id desc", id));
        return row;
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

    private boolean isRecognizedStatus(String status) {
        return "success".equalsIgnoreCase(status) || "recognized".equalsIgnoreCase(status);
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String namesOf(List<Long> ids) {
        if (ids.isEmpty()) return "";
        List<String> names = namedJdbc.queryForList("select display_name from sys_user where id in (:ids) order by field(id, " + joinIds(ids) + ")", new MapSqlParameterSource("ids", ids), String.class);
        return names.isEmpty() ? joinIds(ids) : String.join("+", names);
    }

    private String normalizePlatform(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("DOUYIN".equals(code) || "抖音".equals(code)) return "DOUYIN";
        if ("XIAOHONGSHU".equals(code) || "XHS".equals(code) || "小红书".equals(code)) return "XIAOHONGSHU";
        throw BusinessException.badRequest("平台只支持抖音或小红书");
    }

    private String platformName(String code) {
        return "DOUYIN".equals(code) ? "抖音" : "小红书";
    }

    private String safeFolder(String value) {
        return cleanPathPart(value == null ? "未命名" : value);
    }

    private String safeFileName(String value) {
        return cleanPathPart(value == null || value.isBlank() ? "upload.bin" : value);
    }

    private String cleanPathPart(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return "未命名";
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.trim().toCharArray()) {
            if (isUnsafePathChar(c)) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();
        while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        if (cleaned.isBlank()) cleaned = "未命名";
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private boolean isUnsafePathChar(char c) {
        return Character.isWhitespace(c) || c == '/' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' || c == (char) 92;
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long currentUserId(UserPrincipal principal) {
        return principal == null ? 0L : principal.id();
    }

    private String currentUserName(UserPrincipal principal) {
        return principal == null ? "system" : principal.userName();
    }

    public record CreatePackageRequest(LocalDate topicDate, List<Long> operatorUserIds, List<Long> mediaUserIds) {}
    public record CreatePlatformTopicRequest(String platformCode, String subTopicName) {}
    public record ConfirmContentRequest(String contentTitle, String contentSummary, LocalDate contentDate) {}
    public record DailyReportRequest(LocalDate date) {}
}

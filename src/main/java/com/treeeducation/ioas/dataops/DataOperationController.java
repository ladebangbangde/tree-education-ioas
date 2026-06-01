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

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public DataOperationController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
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
                .addValue("createdBy", principal == null ? 0L : principal.id())
                .addValue("createdByName", principal == null ? "system" : principal.displayName());
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
                .addValue("createdBy", principal == null ? 0L : principal.id())
                .addValue("summaryJson", String.format("{\"packageCount\":%d,\"contentCount\":%d,\"screenshotCount\":%d}", packageCount, contentCount, screenshotCount));
        namedJdbc.update("""
                insert into data_operation_daily_report
                (report_date, package_count, content_count, screenshot_count, douyin_count, xiaohongshu_count, failed_count, report_status, summary_json, created_by)
                values (:date, :packageCount, :contentCount, :screenshotCount, :douyinCount, :xiaohongshuCount, :failedCount, 'created', :summaryJson, :createdBy)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return ApiResponse.ok(queryOne("select * from data_operation_daily_report where id = ?", id));
    }

    private Map<String, Object> detailPackage(Long id) {
        Map<String, Object> row = queryOne("select * from data_operation_topic_package where id = ?", id);
        row.put("platformTopics", jdbc.queryForList("select * from data_operation_platform_topic where package_id = ? order by id desc", id));
        row.put("contents", jdbc.queryForList("select * from data_operation_content where package_id = ? order by id desc", id));
        return row;
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
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
        List<String> names = namedJdbc.queryForList("select display_name from sys_user where id in (:ids) order by field(id, " + joinIds(ids) + ")",
                new MapSqlParameterSource("ids", ids), String.class);
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
        return value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("_+", "_");
    }

    public record CreatePackageRequest(LocalDate topicDate, List<Long> operatorUserIds, List<Long> mediaUserIds) {}
    public record CreatePlatformTopicRequest(String platformCode, String subTopicName) {}
    public record ConfirmContentRequest(String contentTitle, String contentSummary, LocalDate contentDate) {}
    public record DailyReportRequest(LocalDate date) {}
}

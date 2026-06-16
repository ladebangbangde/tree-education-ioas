package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/data-ops/platform-topics")
public class DataOperationContentConfirmController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public DataOperationContentConfirmController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @PostMapping("/{topicId}/contents/confirm-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<Map<String, Object>> confirmCurrentContent(@PathVariable Long topicId,
                                                                  @RequestBody ConfirmCurrentContentRequest request) {
        if (request == null) throw BusinessException.badRequest("请求不能为空");
        ensureColumns();
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        if (topic.isEmpty()) throw BusinessException.notFound("平台子主题不存在");
        Long packageId = numberToLong(topic.get("package_id"));
        if (packageId == null) throw BusinessException.badRequest("平台子主题缺少主题包归属");
        if (!isConfirmed(topic)) throw BusinessException.badRequest("请先确认账号和账号内容类型，再新增作品");
        String platformCode = stringValue(topic.get("platform_code"));
        String lockedType = normalizeContentType(stringValue(topic.get("content_type")));
        String requestedType = request.contentType() == null ? lockedType : normalizeContentType(request.contentType());
        if (!lockedType.equals(requestedType)) {
            throw BusinessException.badRequest("该账号已确认是" + contentTypeLabel(lockedType) + "账号，请先手动更改账号内容类型后再新增" + contentTypeLabel(requestedType));
        }
        String contentType = lockedType;
        String title = cleanTitle(request.contentTitle());
        if (title == null) title = cleanTitle(stringValue(topic.get("ocr_content_title")));
        if (title == null) title = cleanTitle(stringValue(topic.get("sub_topic_name")));
        if (title == null) throw BusinessException.badRequest("请确认内容标题");
        LocalDate contentDate = request.contentDate() == null ? LocalDate.now() : request.contentDate();

        Long id = request.contentId();
        if (id != null) {
            Map<String, Object> existing = queryOne("select * from data_operation_content where id = ? and platform_topic_id = ?", id, topicId);
            if (existing.isEmpty()) throw BusinessException.notFound("要编辑的内容不存在");
            String existingType = normalizeContentType(stringValue(existing.get("content_type")));
            if (!contentType.equals(existingType)) {
                throw BusinessException.badRequest("不能在当前账号类型下编辑另一种内容，请先切换账号内容类型");
            }
            jdbc.update("""
                    update data_operation_content
                    set platform_code = ?, content_title = ?, content_summary = ?, content_date = ?, content_type = ?, updated_at = current_timestamp(6)
                    where id = ?
                    """, platformCode, title, request.contentSummary(), Date.valueOf(contentDate), contentType, id);
        } else {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("packageId", packageId)
                    .addValue("topicId", topicId)
                    .addValue("platformCode", platformCode)
                    .addValue("contentType", contentType)
                    .addValue("title", title)
                    .addValue("summary", request.contentSummary())
                    .addValue("contentDate", Date.valueOf(contentDate));
            namedJdbc.update("""
                    insert into data_operation_content
                    (package_id, platform_topic_id, platform_code, content_type, content_title, content_summary, content_date)
                    values (:packageId, :topicId, :platformCode, :contentType, :title, :summary, :contentDate)
                    """, params, keyHolder);
            id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        }
        jdbc.update("""
                update data_operation_platform_topic
                set content_type = ?, ocr_content_title = ?, updated_at = current_timestamp(6)
                where id = ?
                """, contentType, title, topicId);
        synchronizeSingleContent(id, contentType);
        return ApiResponse.ok(queryOne("select * from data_operation_content where id = ?", id));
    }

    private void synchronizeSingleContent(Long contentId, String contentType) {
        jdbc.update("update data_operation_content set content_type = ?, updated_at = current_timestamp(6) where id = ?", contentType, contentId);
        jdbc.update("update data_operation_asset set content_type = ? where content_id = ? and asset_type = 'DATA_SCREENSHOT'", contentType, contentId);
        jdbc.update("update data_operation_metric_value set content_type = ?, updated_at = current_timestamp(6) where content_id = ?", contentType, contentId);
    }

    private void ensureColumns() {
        ensureColumn("data_operation_content", "content_type", "alter table data_operation_content add column content_type varchar(32) not null default 'IMAGE_TEXT' after platform_code");
        ensureColumn("data_operation_asset", "content_type", "alter table data_operation_asset add column content_type varchar(32) null after asset_type");
        ensureColumn("data_operation_platform_topic", "content_type", "alter table data_operation_platform_topic add column content_type varchar(32) null after platform_name");
        ensureColumn("data_operation_platform_topic", "account_confirmed_flag", "alter table data_operation_platform_topic add column account_confirmed_flag tinyint(1) not null default 0 after ocr_platform_user_id");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", Integer.class, table, column);
            if (count != null && count == 0) jdbc.execute(ddl);
        } catch (RuntimeException ignored) {}
    }

    private boolean isConfirmed(Map<String, Object> topic) {
        Object value = topic.get("account_confirmed_flag");
        if (value instanceof Number number) return number.intValue() == 1;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) return "IMAGE_TEXT";
        String type = value.trim().toUpperCase(Locale.ROOT);
        if ("VIDEO".equals(type)) return "VIDEO";
        if ("IMAGE_TEXT".equals(type) || "IMAGE".equals(type) || "TEXT".equals(type)) return "IMAGE_TEXT";
        throw BusinessException.badRequest("不支持的内容类型：" + value);
    }

    private String contentTypeLabel(String value) {
        return "VIDEO".equalsIgnoreCase(value) ? "视频" : "图文";
    }

    private String cleanTitle(String value) {
        if (value == null) return null;
        String title = value.trim().replaceAll("\\s+", " ");
        if (title.isBlank() || "null".equalsIgnoreCase(title)) return null;
        return title.length() > 255 ? title.substring(0, 255) : title;
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : new LinkedHashMap<>(rows.get(0));
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }

    public record ConfirmCurrentContentRequest(Long contentId,
                                               String contentTitle,
                                               String contentSummary,
                                               LocalDate contentDate,
                                               String contentType) {}
}

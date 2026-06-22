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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationAnchorController {
    private static final long NONE_OPERATOR_ID = -1L;
    private static final long NONE_MEDIA_ID = -2L;
    private static final long NONE_ANCHOR_ID = -3L;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public DataOperationAnchorController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @GetMapping("/anchor-users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<List<Map<String, Object>>> anchorUsers() {
        return ApiResponse.ok(jdbc.queryForList("""
                select id,
                       username,
                       coalesce(nullif(display_name_zh, ''), display_name, username) as display_name,
                       display_name_zh,
                       department,
                       role_code
                from sys_user
                where status = 'ACTIVE' and role_code in ('ANCHOR', 'SUPER_ADMIN')
                order by field(role_code, 'ANCHOR', 'SUPER_ADMIN'), id desc
                """));
    }

    @PostMapping("/packages-with-anchor")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> createPackageWithAnchor(@RequestBody CreatePackageWithAnchorRequest request,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        ensureColumns();
        if (request == null) throw BusinessException.badRequest("请求不能为空");
        LocalDate topicDate = request.topicDate() == null ? LocalDate.now() : request.topicDate();

        List<Long> rawOperatorIds = request.operatorUserIds() == null ? List.of() : request.operatorUserIds();
        List<Long> rawMediaIds = request.mediaUserIds() == null ? List.of() : request.mediaUserIds();
        List<Long> rawAnchorIds = request.anchorUserIds() == null ? List.of() : request.anchorUserIds();

        boolean noneOperator = hasNone(rawOperatorIds, NONE_OPERATOR_ID);
        boolean noneMedia = hasNone(rawMediaIds, NONE_MEDIA_ID);
        boolean noneAnchor = hasNone(rawAnchorIds, NONE_ANCHOR_ID);

        List<Long> operatorIds = cleanIds(rawOperatorIds, NONE_OPERATOR_ID);
        List<Long> mediaIds = cleanIds(rawMediaIds, NONE_MEDIA_ID);
        List<Long> anchorIds = cleanIds(rawAnchorIds, NONE_ANCHOR_ID);

        if (operatorIds.isEmpty() && !noneOperator) throw BusinessException.badRequest("请选择运营人员");
        if (mediaIds.isEmpty() && !noneMedia) throw BusinessException.badRequest("请选择媒体/美工人员");
        if (anchorIds.isEmpty() && !noneAnchor) throw BusinessException.badRequest("请选择负责口播人员");

        String operatorNames = noneOperator ? "无运营" : namesOf(operatorIds);
        String mediaNames = noneMedia ? "无美工" : namesOf(mediaIds);
        String anchorNames = noneAnchor ? "无主播" : namesOf(anchorIds);
        String displayName = operatorNames + "+" + mediaNames + "+" + anchorNames + "+" + topicDate;
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
                .addValue("anchorUserIds", joinIds(anchorIds))
                .addValue("anchorNames", anchorNames)
                .addValue("createdBy", principal == null ? 0L : principal.id())
                .addValue("createdByName", principal == null ? "system" : principal.userName());
        namedJdbc.update("""
                insert into data_operation_topic_package
                (package_no, topic_date, display_name, folder_name, operator_user_ids, operator_names, media_user_ids, media_names, anchor_user_ids, anchor_names, created_by, created_by_name)
                values (:packageNo, :topicDate, :displayName, :folderName, :operatorUserIds, :operatorNames, :mediaUserIds, :mediaNames, :anchorUserIds, :anchorNames, :createdBy, :createdByName)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return ApiResponse.ok(queryOne("select * from data_operation_topic_package where id = ?", id));
    }

    @PostMapping("/packages/{packageId}/anchors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> setPackageAnchors(@PathVariable Long packageId, @RequestBody AnchorAssignRequest request) {
        ensureColumns();
        List<Long> rawIds = request == null || request.anchorUserIds() == null ? List.of() : request.anchorUserIds();
        boolean noneAnchor = hasNone(rawIds, NONE_ANCHOR_ID);
        List<Long> ids = cleanIds(rawIds, NONE_ANCHOR_ID);
        String idText = joinIds(ids);
        String names = noneAnchor ? "无主播" : namesOf(ids);
        jdbc.update("""
                update data_operation_topic_package
                set anchor_user_ids = ?, anchor_names = ?, updated_at = current_timestamp(6)
                where id = ?
                """, idText, names, packageId);
        return ApiResponse.ok(queryOne("select * from data_operation_topic_package where id = ?", packageId));
    }

    private void ensureColumns() {
        ensureColumn("data_operation_topic_package", "anchor_user_ids", "alter table data_operation_topic_package add column anchor_user_ids varchar(500) null after media_names");
        ensureColumn("data_operation_topic_package", "anchor_names", "alter table data_operation_topic_package add column anchor_names varchar(500) null after anchor_user_ids");
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

    private boolean hasNone(List<Long> ids, long noneId) {
        return ids != null && ids.stream().filter(Objects::nonNull).anyMatch(id -> id == noneId);
    }

    private List<Long> cleanIds(List<Long> ids, long noneId) {
        if (ids == null || hasNone(ids, noneId)) return List.of();
        return ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String namesOf(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        List<String> names = namedJdbc.queryForList("""
                select coalesce(nullif(display_name_zh, ''), display_name, username)
                from sys_user
                where id in (:ids)
                order by field(id, %s)
                """.formatted(ids.stream().map(String::valueOf).collect(Collectors.joining(","))), params, String.class);
        return names.isEmpty() ? ids.stream().map(String::valueOf).collect(Collectors.joining("、")) : String.join("、", names);
    }

    private String safeFolder(String value) {
        String text = value == null || value.isBlank() ? "未命名" : value.trim();
        String cleaned = text.replaceAll("[\\s/:*?\"<>|\\\\]+", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public record AnchorAssignRequest(List<Long> anchorUserIds) {}
    public record CreatePackageWithAnchorRequest(LocalDate topicDate, List<Long> operatorUserIds, List<Long> mediaUserIds, List<Long> anchorUserIds) {}
}

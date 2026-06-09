package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationAnchorController {
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
                select id, username, display_name, department, role_code
                from sys_user
                where status = 'ACTIVE' and role_code = 'ANCHOR'
                order by id desc
                """));
    }

    @PostMapping("/packages/{packageId}/anchors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> setPackageAnchors(@PathVariable Long packageId, @RequestBody AnchorAssignRequest request) {
        ensureColumns();
        List<Long> ids = request == null || request.anchorUserIds() == null
                ? List.of()
                : request.anchorUserIds().stream().filter(x -> x != null).distinct().toList();
        String idText = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        String names = namesOf(ids);
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

    private String namesOf(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        List<String> names = namedJdbc.queryForList("""
                select display_name
                from sys_user
                where id in (:ids)
                order by field(id, %s)
                """.formatted(ids.stream().map(String::valueOf).collect(Collectors.joining(","))), params, String.class);
        return names.isEmpty() ? ids.stream().map(String::valueOf).collect(Collectors.joining("+")) : String.join("+", names);
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public record AnchorAssignRequest(List<Long> anchorUserIds) {}
}

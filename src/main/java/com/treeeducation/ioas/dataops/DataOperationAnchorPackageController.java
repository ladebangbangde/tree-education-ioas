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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationAnchorPackageController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public DataOperationAnchorPackageController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @PostMapping("/packages-with-anchor")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA')")
    public ApiResponse<Map<String, Object>> createPackageWithAnchor(@RequestBody CreatePackageWithAnchorRequest request,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        if (request == null) throw BusinessException.badRequest("请求不能为空");
        LocalDate topicDate = request.topicDate() == null ? LocalDate.now() : request.topicDate();
        List<Long> operatorIds = cleanIds(request.operatorUserIds());
        List<Long> mediaIds = cleanIds(request.mediaUserIds());
        List<Long> anchorIds = cleanIds(request.anchorUserIds());
        if (operatorIds.isEmpty()) throw BusinessException.badRequest("请选择运营人员");
        if (mediaIds.isEmpty()) throw BusinessException.badRequest("请选择媒体人员");
        if (anchorIds.isEmpty()) throw BusinessException.badRequest("请选择主播人员");

        String operatorNames = namesOf(operatorIds);
        String mediaNames = namesOf(mediaIds);
        String anchorNames = namesOf(anchorIds);
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
                (package_no, topic_date, display_name, folder_name,
                 operator_user_ids, operator_names, media_user_ids, media_names, anchor_user_ids, anchor_names,
                 created_by, created_by_name)
                values (:packageNo, :topicDate, :displayName, :folderName,
                        :operatorUserIds, :operatorNames, :mediaUserIds, :mediaNames, :anchorUserIds, :anchorNames,
                        :createdBy, :createdByName)
                """, params, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return ApiResponse.ok(detailPackage(id));
    }

    private Map<String, Object> detailPackage(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from data_operation_topic_package where id = ?", id);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        row.put("platformTopics", jdbc.queryForList("select * from data_operation_platform_topic where package_id = ? order by id desc", id));
        row.put("contents", jdbc.queryForList("select * from data_operation_content where package_id = ? order by id desc", id));
        row.put("assets", jdbc.queryForList("select * from data_operation_asset where package_id = ? order by id desc", id));
        return row;
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

    private String safeFolder(String value) {
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
        if (cleaned.isBlank()) cleaned = "未命名";
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    public record CreatePackageWithAnchorRequest(LocalDate topicDate, List<Long> operatorUserIds, List<Long> mediaUserIds, List<Long> anchorUserIds) {}
}

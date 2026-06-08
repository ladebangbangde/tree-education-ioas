package com.treeeducation.ioas.dataops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class DataOperationDataPageUploadCleanupFilter extends OncePerRequestFilter {
    private static final Pattern SCREENSHOT_PATH = Pattern.compile("^/api/v1/data-ops/contents/(\\d+)/screenshots$");
    private final JdbcTemplate jdbc;
    private final DataOperationAssetStorageService storageService;

    public DataOperationDataPageUploadCleanupFilter(JdbcTemplate jdbc, DataOperationAssetStorageService storageService) {
        this.jdbc = jdbc;
        this.storageService = storageService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Matcher matcher = SCREENSHOT_PATH.matcher(request.getRequestURI());
        String assetGroup = request.getParameter("assetGroup");
        Long contentId = matcher.matches() ? parseLong(matcher.group(1)) : null;
        filterChain.doFilter(request, response);
        if (contentId == null || assetGroup == null || assetGroup.isBlank() || response.getStatus() >= 400) return;
        cleanupDuplicates(contentId, assetGroup.trim().toUpperCase());
    }

    private void cleanupDuplicates(Long contentId, String assetGroup) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select id, bucket_name, object_key
                    from data_operation_asset
                    where content_id = ?
                      and asset_type = 'DATA_SCREENSHOT'
                      and (asset_group = ? or asset_group is null or asset_group = '')
                    order by id desc
                    """, contentId, assetGroup);
            if (rows.isEmpty()) return;
            Long keepId = numberToLong(rows.get(0).get("id"));
            if (keepId != null) {
                jdbc.update("update data_operation_asset set asset_group = ?, upload_status = 'success', updated_at = current_timestamp(6) where id = ?", assetGroup, keepId);
            }
            for (int i = 1; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                Long assetId = numberToLong(row.get("id"));
                if (assetId == null) continue;
                storageService.delete(stringValue(row.get("bucket_name")), stringValue(row.get("object_key")));
                safeUpdate("delete from data_operation_metric_value where asset_id = ?", assetId);
                safeUpdate("delete from data_operation_asset where id = ?", assetId);
            }
            Integer count = jdbc.queryForObject("select count(*) from data_operation_asset where content_id = ? and asset_type = 'DATA_SCREENSHOT'", Integer.class, contentId);
            jdbc.update("update data_operation_content set screenshot_count = ?, updated_at = current_timestamp(6) where id = ?", count == null ? 0 : count, contentId);
        } catch (RuntimeException ignored) {
        }
    }

    private void safeUpdate(String sql, Object... args) {
        try { jdbc.update(sql, args); } catch (RuntimeException ignored) {}
    }

    private Long parseLong(String value) {
        try { return Long.parseLong(value); } catch (RuntimeException ex) { return null; }
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }
}

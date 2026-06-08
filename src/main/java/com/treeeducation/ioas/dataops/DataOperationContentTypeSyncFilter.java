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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class DataOperationContentTypeSyncFilter extends OncePerRequestFilter {
    private static final Pattern CONFIRM_CONTENT_PATH = Pattern.compile("^/api/v1/data-ops/platform-topics/(\\d+)/contents/confirm$");
    private final JdbcTemplate jdbc;

    public DataOperationContentTypeSyncFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Matcher matcher = CONFIRM_CONTENT_PATH.matcher(request.getRequestURI());
        Long topicId = matcher.matches() ? parseLong(matcher.group(1)) : null;
        String contentType = normalizeContentType(request.getParameter("contentType"));
        filterChain.doFilter(request, response);
        if (topicId == null || response.getStatus() >= 400) return;
        syncLatestContentType(topicId, contentType);
    }

    private void syncLatestContentType(Long topicId, String contentType) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select id, content_summary, content_title
                    from data_operation_content
                    where platform_topic_id = ?
                    order by id desc
                    limit 1
                    """, topicId);
            if (rows.isEmpty()) return;
            Map<String, Object> row = rows.get(0);
            String finalType = contentType;
            if (finalType == null) finalType = inferContentType(stringValue(row.get("content_summary")), stringValue(row.get("content_title")));
            if (finalType == null) finalType = "IMAGE_TEXT";
            Long contentId = numberToLong(row.get("id"));
            if (contentId != null) {
                jdbc.update("update data_operation_content set content_type = ?, updated_at = current_timestamp(6) where id = ?", finalType, contentId);
                jdbc.update("update data_operation_platform_topic set content_type = ?, updated_at = current_timestamp(6) where id = ?", finalType, topicId);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) return null;
        String type = value.trim().toUpperCase(Locale.ROOT);
        if ("VIDEO".equals(type)) return "VIDEO";
        if ("IMAGE_TEXT".equals(type) || "IMAGE".equals(type) || "TEXT".equals(type)) return "IMAGE_TEXT";
        return null;
    }

    private String inferContentType(String... values) {
        for (String value : values) {
            if (value == null) continue;
            if (value.contains("视频")) return "VIDEO";
            if (value.contains("图文")) return "IMAGE_TEXT";
        }
        return null;
    }

    private Long parseLong(String value) { try { return Long.parseLong(value); } catch (RuntimeException ex) { return null; } }
    private Long numberToLong(Object value) { if (value instanceof Number number) return number.longValue(); if (value == null) return null; try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; } }
    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }
}

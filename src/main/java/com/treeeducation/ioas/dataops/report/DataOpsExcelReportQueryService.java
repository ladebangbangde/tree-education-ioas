package com.treeeducation.ioas.dataops.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataOpsExcelReportQueryService {
    private static final Set<String> CONFIRMED_STATUSES = Set.of("confirmed", "completed", "imported", "approved", "reviewed", "done", "success", "passed");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DataOpsExcelReportQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public QueryResult load(LocalDate reportDate, String platform, Long topicPackageId, boolean onlyConfirmed) {
        ReportContext ctx = detectContext();
        List<Map<String, Object>> rows = ctx.structuredReady()
                ? loadStructuredRows(ctx, reportDate, platform, topicPackageId, onlyConfirmed)
                : loadHeuristicRows(ctx, reportDate, platform, topicPackageId, onlyConfirmed);
        return new QueryResult(ctx.mainTable == null ? "" : ctx.mainTable.tableName, ctx.structuredReady() ? "STRUCTURED" : "HEURISTIC", rows);
    }

    private ReportContext detectContext() {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", String.class);
        List<TableMeta> metas = new ArrayList<>();
        for (String table : tables) {
            List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?", String.class, table);
            metas.add(new TableMeta(table, columns, scoreTable(table, columns)));
        }
        TableMeta contentTable = metas.stream().filter(t -> "data_ops_contents".equalsIgnoreCase(t.tableName)).findFirst().orElse(null);
        TableMeta topicTable = metas.stream().filter(t -> "data_ops_platform_topics".equalsIgnoreCase(t.tableName)).findFirst().orElse(null);
        TableMeta packageTable = metas.stream().filter(t -> "data_ops_packages".equalsIgnoreCase(t.tableName)).findFirst().orElse(null);
        metas.sort(Comparator.comparingInt(TableMeta::score).reversed());
        TableMeta mainTable = contentTable != null ? contentTable : metas.stream().filter(t -> t.score > 0).findFirst().orElse(null);
        return new ReportContext(mainTable, contentTable, topicTable, packageTable);
    }

    private int scoreTable(String tableName, List<String> columns) {
        int score = 0;
        String lower = tableName.toLowerCase(Locale.ROOT);
        if (lower.contains("data")) score += 3;
        if (lower.contains("ops")) score += 3;
        if (lower.contains("content")) score += 6;
        if (lower.contains("recogn")) score += 3;
        if (lower.contains("review")) score += 2;
        if (firstExisting(columns, "content_title", "title", "name") != null) score += 8;
        if (firstExisting(columns, "platform_code", "platform") != null) score += 8;
        if (firstExisting(columns, "status", "recognition_status") != null) score += 6;
        if (firstExisting(columns, "created_at", "created_time", "published_at", "publish_time") != null) score += 6;
        if (firstExisting(columns, "data_payload_json", "payload_json", "ocr_payload_json") != null) score += 6;
        return score;
    }

    private List<Map<String, Object>> loadStructuredRows(ReportContext ctx, LocalDate reportDate, String platform, Long topicPackageId, boolean onlyConfirmed) {
        TableMeta c = ctx.contentTable;
        TableMeta t = ctx.topicTable;
        TableMeta p = ctx.packageTable;
        List<String> parts = new ArrayList<>();
        parts.add(select(c, "c", "content_title", "content_title", "content_name", "name", "title"));
        parts.add(select(c, "c", "content_platform", "platform_code", "platform"));
        parts.add(select(c, "c", "content_type_value", "content_type", "type"));
        parts.add(select(c, "c", "content_status", "status", "recognition_status"));
        parts.add(select(c, "c", "content_payload", "data_payload_json", "payload_json"));
        parts.add(select(c, "c", "content_created_at", "created_at", "created_time", "updated_at"));
        parts.add(select(c, "c", "content_published_at", "content_date", "published_at", "publish_time", "created_at"));
        parts.add(select(c, "c", "content_corrected", "manually_corrected", "manual_corrected", "corrected"));
        parts.add(select(c, "c", "content_confidence", "ocr_confidence", "confidence", "ocr_score"));
        parts.add(select(c, "c", "reviewer_name", "reviewed_by_name", "reviewer_name", "checker_name"));
        parts.add(select(t, "t", "topic_platform", "platform_code", "platform_name"));
        parts.add(select(t, "t", "topic_account", "ocr_account_name", "account_name", "platform_account_name"));
        parts.add(select(t, "t", "topic_title", "ocr_content_title", "ocr_title"));
        parts.add(select(t, "t", "topic_sub_name", "sub_topic_name", "sub_topic"));
        parts.add(select(t, "t", "topic_payload", "ocr_payload_json"));
        parts.add(select(t, "t", "topic_content_type", "content_type"));
        parts.add(select(p, "p", "package_name", "display_name", "package_name", "name", "title"));
        parts.add(select(p, "p", "topic_date", "topic_date"));
        parts.add(select(p, "p", "operator_names", "operator_names"));
        parts.add(select(p, "p", "media_names", "media_names"));

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", parts)).append(" FROM `").append(c.tableName).append("` c");
        if (t != null && has(c, "platform_topic_id") && has(t, "id")) sql.append(" LEFT JOIN `").append(t.tableName).append("` t ON c.`").append(firstExisting(c.columns, "platform_topic_id")).append("` = t.`id`");
        else sql.append(" LEFT JOIN (SELECT 1) t ON 1=0");
        if (p != null && has(c, "package_id") && has(p, "id")) sql.append(" LEFT JOIN `").append(p.tableName).append("` p ON c.`").append(firstExisting(c.columns, "package_id")).append("` = p.`id`");
        else if (p != null && t != null && has(t, "package_id") && has(p, "id")) sql.append(" LEFT JOIN `").append(p.tableName).append("` p ON t.`").append(firstExisting(t.columns, "package_id")).append("` = p.`id`");
        else sql.append(" LEFT JOIN (SELECT 1) p ON 1=0");

        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (p != null && has(p, "topic_date")) {
            where.add("DATE(p.`" + firstExisting(p.columns, "topic_date") + "`) = ?");
            args.add(reportDate);
        } else if (has(c, "content_date", "published_at", "publish_time", "created_at", "created_time")) {
            String dc = firstExisting(c.columns, "content_date", "published_at", "publish_time", "created_at", "created_time");
            where.add("DATE(c.`" + dc + "`) = ?");
            args.add(reportDate);
        }
        if (platform != null && !platform.isBlank() && !"ALL".equalsIgnoreCase(platform)) {
            if (has(c, "platform_code", "platform")) {
                where.add("c.`" + firstExisting(c.columns, "platform_code", "platform") + "` = ?");
                args.add(platform);
            } else if (t != null && has(t, "platform_code")) {
                where.add("t.`" + firstExisting(t.columns, "platform_code") + "` = ?");
                args.add(platform);
            }
        }
        if (topicPackageId != null && has(c, "package_id")) {
            where.add("c.`" + firstExisting(c.columns, "package_id") + "` = ?");
            args.add(topicPackageId);
        }
        if (onlyConfirmed && has(c, "status", "recognition_status")) {
            String sc = firstExisting(c.columns, "status", "recognition_status");
            String marks = CONFIRMED_STATUSES.stream().map(x -> "?").collect(Collectors.joining(","));
            where.add("LOWER(CAST(c.`" + sc + "` AS CHAR)) IN (" + marks + ")");
            args.addAll(CONFIRMED_STATUSES);
        }
        if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
        try { return jdbc.queryForList(sql.toString(), args.toArray()); } catch (Exception ex) { return List.of(); }
    }

    private List<Map<String, Object>> loadHeuristicRows(ReportContext ctx, LocalDate reportDate, String platform, Long topicPackageId, boolean onlyConfirmed) {
        if (ctx.mainTable == null) return List.of();
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String timeColumn = firstExisting(ctx.mainTable.columns, "created_at", "created_time", "recognized_at", "published_at", "publish_time");
        String platformColumn = firstExisting(ctx.mainTable.columns, "platform_code", "platform");
        String statusColumn = firstExisting(ctx.mainTable.columns, "status", "review_status", "import_status", "data_status");
        String packageColumn = firstExisting(ctx.mainTable.columns, "package_id", "topic_package_id", "content_package_id");
        if (timeColumn != null) { where.add("DATE(`" + timeColumn + "`) = ?"); args.add(reportDate); }
        if (platformColumn != null && platform != null && !platform.isBlank() && !"ALL".equalsIgnoreCase(platform)) { where.add("`" + platformColumn + "` = ?"); args.add(platform); }
        if (packageColumn != null && topicPackageId != null) { where.add("`" + packageColumn + "` = ?"); args.add(topicPackageId); }
        if (onlyConfirmed && statusColumn != null) { String marks = CONFIRMED_STATUSES.stream().map(x -> "?").collect(Collectors.joining(",")); where.add("LOWER(CAST(`" + statusColumn + "` AS CHAR)) IN (" + marks + ")"); args.addAll(CONFIRMED_STATUSES); }
        String sql = "SELECT * FROM `" + ctx.mainTable.tableName + "`" + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where));
        try { return jdbc.queryForList(sql, args.toArray()); } catch (Exception ex) { return List.of(); }
    }

    public Map<String, Object> toReportRow(Map<String, Object> row, LocalDate fallbackDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(parseMap(row.get("topic_payload")));
        payload.putAll(parseMap(row.get("content_payload")));
        if (payload.isEmpty()) payload.putAll(parseMap(row.get("data_payload_json")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", nonBlank(text(row.get("topic_date")), String.valueOf(fallbackDate)));
        result.put("packageName", text(row.get("package_name")));
        result.put("platform", nonBlank(text(row.get("content_platform")), text(row.get("topic_platform")), text(row.get("platform_code")), text(row.get("platform")), "UNKNOWN"));
        result.put("account", nonBlank(text(row.get("topic_account")), text(row.get("account_name")), text(row.get("platform_account_name"))));
        result.put("title", nonBlank(text(row.get("content_title")), text(row.get("topic_title")), text(row.get("title"))));
        result.put("subTopic", nonBlank(text(row.get("topic_sub_name")), text(row.get("sub_topic_name"))));
        result.put("contentType", nonBlank(text(row.get("content_type_value")), text(row.get("topic_content_type")), text(row.get("content_type"))));
        result.put("publishedAt", nonBlank(text(row.get("content_published_at")), text(row.get("content_created_at")), text(row.get("created_at")), text(row.get("topic_date"))));
        result.put("operatorName", text(row.get("operator_names")));
        result.put("mediaName", text(row.get("media_names")));
        result.put("page1Views", findLong(payload, "page1Views", "views", "playCount", "viewCount", "播放量"));
        result.put("page1Likes", findLong(payload, "page1Likes", "likes", "likeCount", "点赞"));
        result.put("page1Comments", findLong(payload, "page1Comments", "comments", "commentCount", "评论"));
        result.put("page1Favorites", findLong(payload, "page1Favorites", "favorites", "favoriteCount", "收藏"));
        result.put("page1Shares", findLong(payload, "page1Shares", "shares", "shareCount", "转发"));
        result.put("page2Exposure", findLong(payload, "page2Exposure", "exposure", "impressions", "曝光"));
        result.put("page2ProfileViews", findLong(payload, "page2ProfileViews", "profileViews", "homeVisit", "主页访问"));
        result.put("page2Followers", findLong(payload, "page2Followers", "newFans", "followers", "涨粉"));
        result.put("page3CompletionRate", findDouble(payload, "page3CompletionRate", "completionRate", "完播率"));
        result.put("page3EngagementRate", findDouble(payload, "page3EngagementRate", "engagementRate", "互动率"));
        result.put("ocrConfidence", toDouble(firstNonNull(row.get("content_confidence"), row.get("ocr_confidence"), row.get("confidence"))));
        result.put("corrected", isTruthy(firstNonNull(row.get("content_corrected"), row.get("manually_corrected"), row.get("corrected"))));
        result.put("reviewer", nonBlank(text(row.get("reviewer_name")), text(row.get("reviewed_by_name"))));
        result.put("createdAt", nonBlank(text(row.get("content_created_at")), text(row.get("created_at"))));
        String status = nonBlank(text(row.get("content_status")), text(row.get("status")), "").toLowerCase(Locale.ROOT);
        result.put("confirmed", status.isBlank() || CONFIRMED_STATUSES.contains(status));
        return result;
    }

    private String select(TableMeta meta, String alias, String asName, String... candidates) {
        if (meta == null) return "'' AS `" + asName + "`";
        String column = firstExisting(meta.columns, candidates);
        return column == null ? "'' AS `" + asName + "`" : alias + ".`" + column + "` AS `" + asName + "`";
    }

    private boolean has(TableMeta meta, String... names) {
        return meta != null && firstExisting(meta.columns, names) != null;
    }

    private String firstExisting(Collection<String> available, String... names) {
        Map<String, String> normalized = new HashMap<>();
        for (String key : available) normalized.put(norm(key), key);
        for (String name : names) {
            String found = normalized.get(norm(name));
            if (found != null) return found;
        }
        return null;
    }

    private String norm(String value) {
        return value == null ? "" : value.replaceAll("[_\\-\\s]", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return Map.of();
        try { return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {}); } catch (Exception ex) { return Map.of(); }
    }

    private long findLong(Map<String, Object> data, String... keys) { return Math.round(findDouble(data, keys)); }

    private double findDouble(Map<String, Object> data, String... keys) {
        Object found = findDeep(data, Arrays.stream(keys).map(this::norm).collect(Collectors.toSet()));
        return toDouble(found);
    }

    private Object findDeep(Object node, Set<String> keys) {
        if (node == null) return null;
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) if (keys.contains(norm(String.valueOf(entry.getKey())))) return entry.getValue();
            for (Object value : map.values()) { Object found = findDeep(value, keys); if (found != null) return found; }
        } else if (node instanceof List<?> list) {
            for (Object item : list) { Object found = findDeep(item, keys); if (found != null) return found; }
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null && !String.valueOf(value).isBlank()) return value;
        return null;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private String nonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private boolean isTruthy(Object value) { String text = text(value).trim().toLowerCase(Locale.ROOT); return "1".equals(text) || "true".equals(text) || "y".equals(text) || "yes".equals(text); }
    private double toDouble(Object value) { if (value == null) return 0D; if (value instanceof Number n) return n.doubleValue(); String text = String.valueOf(value).replace("%", "").replace(",", "").trim(); if (text.isBlank()) return 0D; try { return Double.parseDouble(text); } catch (Exception ex) { return 0D; } }

    private record TableMeta(String tableName, List<String> columns, int score) {}
    private record ReportContext(TableMeta mainTable, TableMeta contentTable, TableMeta topicTable, TableMeta packageTable) { boolean structuredReady() { return contentTable != null; } }
    public record QueryResult(String tableName, String sourceMode, List<Map<String, Object>> rows) {}
}

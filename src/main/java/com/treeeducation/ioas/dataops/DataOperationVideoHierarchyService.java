package com.treeeducation.ioas.dataops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DataOperationVideoHierarchyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DataOperationVideoHierarchyService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                create table if not exists data_operation_account (
                    id bigint primary key auto_increment,
                    topic_package_id bigint not null,
                    platform_topic_id bigint not null,
                    platform_code varchar(32) not null,
                    account_name varchar(128) null,
                    platform_user_id varchar(128) null,
                    recognition_status varchar(32) not null default 'PENDING',
                    source varchar(64) null,
                    created_at datetime(6) not null default current_timestamp(6),
                    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
                    unique key uk_topic_account_user (platform_topic_id, platform_user_id),
                    index idx_topic_account (platform_topic_id),
                    index idx_package_account (topic_package_id)
                )
                """);
        jdbc.execute("""
                create table if not exists data_operation_video (
                    id bigint primary key auto_increment,
                    topic_package_id bigint not null,
                    platform_topic_id bigint not null,
                    account_id bigint not null,
                    content_id bigint null,
                    title_asset_id bigint null,
                    platform_code varchar(32) not null,
                    content_type varchar(32) not null default 'IMAGE_TEXT',
                    video_title varchar(255) null,
                    title_payload_json json null,
                    recognition_status varchar(32) not null default 'PENDING',
                    source varchar(64) null,
                    created_at datetime(6) not null default current_timestamp(6),
                    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
                    unique key uk_account_video_title (account_id, video_title),
                    index idx_topic_video (platform_topic_id),
                    index idx_content_video (content_id)
                )
                """);
        ensureColumn("data_operation_asset", "account_id", "alter table data_operation_asset add column account_id bigint null comment '识别归属账号ID' after platform_topic_id");
        ensureColumn("data_operation_asset", "video_id", "alter table data_operation_asset add column video_id bigint null comment '识别归属视频ID' after account_id");
        ensureColumn("data_operation_content", "account_id", "alter table data_operation_content add column account_id bigint null comment '归属账号ID' after platform_topic_id");
        ensureColumn("data_operation_content", "video_id", "alter table data_operation_content add column video_id bigint null comment '归属视频ID' after account_id");
        ensureColumn("data_operation_metric_value", "account_id", "alter table data_operation_metric_value add column account_id bigint null after platform_topic_id");
        ensureColumn("data_operation_metric_value", "video_id", "alter table data_operation_metric_value add column video_id bigint null after account_id");
    }

    @Transactional
    public HierarchyRef upsertForRecognizedAsset(Map<String, Object> asset, Map<String, Object> recognitionResult, String platformCode, String contentType, String payloadJson) {
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        if (topicId == null) return new HierarchyRef(null, null);
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = numberToLong(topic.get("package_id"));
        if (packageId == null) return new HierarchyRef(null, null);
        String accountName = firstNonBlank(stringValue(topic.get("ocr_account_name")), stringValue(recognitionResult.get("accountName")), "未识别账号");
        String platformUserId = firstNonBlank(stringValue(topic.get("ocr_platform_user_id")), stringValue(recognitionResult.get("douyinId")), stringValue(recognitionResult.get("wechatChannelId")), stringValue(recognitionResult.get("accountId")), "UNKNOWN-" + topicId);
        Long accountId = upsertAccount(packageId, topicId, platformCode, accountName, platformUserId);
        String videoTitle = firstValidTitle(recognitionResult);
        Long videoId = null;
        if (accountId != null && videoTitle != null) {
            videoId = upsertVideo(packageId, topicId, accountId, numberToLong(asset.get("content_id")), numberToLong(asset.get("id")), platformCode, contentType, videoTitle, payloadJson);
        }
        attachAssetAndContent(numberToLong(asset.get("id")), numberToLong(asset.get("content_id")), accountId, videoId);
        return new HierarchyRef(accountId, videoId);
    }

    @Transactional
    public Long upsertAccountFromCover(Long topicId, String platformCode, String accountName, String platformUserId) {
        if (topicId == null || isBlank(platformUserId)) return null;
        Map<String, Object> topic = queryOne("select * from data_operation_platform_topic where id = ?", topicId);
        Long packageId = numberToLong(topic.get("package_id"));
        if (packageId == null) return null;
        return upsertAccount(packageId, topicId, platformCode, firstNonBlank(accountName, "未识别账号"), platformUserId);
    }

    public Map<String, Object> topicHierarchy(Long topicId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        List<Map<String, Object>> accounts = jdbc.queryForList("""
                select id, account_name as accountName, platform_user_id as platformUserId, recognition_status as recognitionStatus
                from data_operation_account
                where platform_topic_id = ?
                order by id asc
                """, topicId);
        for (Map<String, Object> account : accounts) {
            Long accountId = numberToLong(account.get("id"));
            List<Map<String, Object>> videos = jdbc.queryForList("""
                    select id, video_title as videoTitle, recognition_status as recognitionStatus, content_id as contentId, title_asset_id as titleAssetId
                    from data_operation_video
                    where account_id = ?
                    order by id asc
                    """, accountId);
            for (Map<String, Object> video : videos) {
                Long videoId = numberToLong(video.get("id"));
                List<Map<String, Object>> metrics = jdbc.queryForList("""
                        select metric_group as metricGroup, metric_key as metricKey, metric_label as metricLabel, metric_value as metricValue,
                               metric_unit as metricUnit, recognition_status as recognitionStatus, asset_id as assetId, recognized_at as recognizedAt
                        from data_operation_metric_value
                        where video_id = ?
                        order by field(metric_group, 'OVERVIEW', 'OVERVIEW_CHART', 'FLOW_ANALYSIS'), metric_key
                        """, videoId);
                video.put("metrics", metrics);
            }
            account.put("videos", videos);
        }
        result.put("accounts", accounts);
        return result;
    }

    private Long upsertAccount(Long packageId, Long topicId, String platformCode, String accountName, String platformUserId) {
        jdbc.update("""
                insert into data_operation_account (topic_package_id, platform_topic_id, platform_code, account_name, platform_user_id, recognition_status, source)
                values (?, ?, ?, ?, ?, 'SUCCESS', 'OCR')
                on duplicate key update account_name = values(account_name), recognition_status = 'SUCCESS', updated_at = current_timestamp(6)
                """, packageId, topicId, platformCode, accountName, platformUserId);
        return queryLong("select id from data_operation_account where platform_topic_id = ? and platform_user_id = ?", topicId, platformUserId);
    }

    private Long upsertVideo(Long packageId, Long topicId, Long accountId, Long contentId, Long titleAssetId, String platformCode, String contentType, String videoTitle, String payloadJson) {
        jdbc.update("""
                insert into data_operation_video (topic_package_id, platform_topic_id, account_id, content_id, title_asset_id, platform_code, content_type, video_title, title_payload_json, recognition_status, source)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', 'OCR')
                on duplicate key update content_id = coalesce(values(content_id), content_id), title_asset_id = coalesce(values(title_asset_id), title_asset_id), title_payload_json = coalesce(values(title_payload_json), title_payload_json), recognition_status = 'SUCCESS', updated_at = current_timestamp(6)
                """, packageId, topicId, accountId, contentId, titleAssetId, platformCode, contentType, videoTitle, payloadJson);
        return queryLong("select id from data_operation_video where account_id = ? and video_title = ?", accountId, videoTitle);
    }

    private void attachAssetAndContent(Long assetId, Long contentId, Long accountId, Long videoId) {
        if (assetId != null) {
            safeUpdate("update data_operation_asset set account_id = ?, video_id = ? where id = ?", accountId, videoId, assetId);
        }
        if (contentId != null) {
            safeUpdate("update data_operation_content set account_id = ?, video_id = ? where id = ?", accountId, videoId, contentId);
        }
        if (assetId != null) {
            safeUpdate("update data_operation_metric_value set account_id = ?, video_id = ? where asset_id = ?", accountId, videoId, assetId);
        }
    }

    public void attachMetrics(Long assetId, Long accountId, Long videoId) {
        if (assetId != null) safeUpdate("update data_operation_metric_value set account_id = ?, video_id = ? where asset_id = ?", accountId, videoId, assetId);
    }

    private String firstValidTitle(Map<String, Object> recognitionResult) {
        Object candidates = recognitionResult.get("candidateTitles");
        if (candidates instanceof Collection<?> list) {
            for (Object item : list) {
                String title = cleanTitle(stringValue(item));
                if (validTitle(title)) return title;
            }
        }
        String title = cleanTitle(stringValue(recognitionResult.get("contentTitle")));
        return validTitle(title) ? title : null;
    }

    private boolean validTitle(String value) {
        if (isBlank(value)) return false;
        String normalized = value.trim();
        if (normalized.length() < 4 || normalized.length() > 180) return false;
        if (normalized.matches(".*(中国联通|中国移动|中国电信|作品数据详情|总览|流量分析|观众分析|每小时|每天|设置观测|DOU).*")) return false;
        if (normalized.matches("^[0-9:\\-\\s.%+]+.*$")) return false;
        return true;
    }

    private String cleanTitle(String value) {
        if (value == null) return null;
        return value.trim().replaceAll("\\s+", " ");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbc.queryForObject("""
                    select count(*)
                    from information_schema.columns
                    where table_schema = database() and table_name = ? and column_name = ?
                    """, Integer.class, table, column);
            if (count != null && count == 0) jdbc.execute(ddl);
        } catch (RuntimeException ignore) {
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : new LinkedHashMap<>(rows.get(0));
    }

    private Long queryLong(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) return null;
        return numberToLong(rows.get(0).values().stream().findFirst().orElse(null));
    }

    private void safeUpdate(String sql, Object... args) {
        try { jdbc.update(sql, args); } catch (RuntimeException ignore) {}
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!isBlank(value)) return value;
        return null;
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException ex) { return "{}"; }
    }

    public record HierarchyRef(Long accountId, Long videoId) {}
}

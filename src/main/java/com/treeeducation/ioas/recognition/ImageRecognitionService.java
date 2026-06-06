package com.treeeducation.ioas.recognition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.dataops.DataOperationAssetStorageService;
import com.treeeducation.ioas.dataops.DataOperationMetricService;
import com.treeeducation.ioas.dataops.DataOperationVideoHierarchyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImageRecognitionService {
    private final ImageRecognitionClient client;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DataOperationAssetStorageService storageService;
    private final DataOperationMetricExtractor metricExtractor;
    private final DataOperationMetricService metricService;
    private final DataOperationVideoHierarchyService hierarchyService;

    public ImageRecognitionService(ImageRecognitionClient client,
                                   JdbcTemplate jdbc,
                                   ObjectMapper objectMapper,
                                   DataOperationAssetStorageService storageService,
                                   DataOperationMetricExtractor metricExtractor,
                                   DataOperationMetricService metricService,
                                   DataOperationVideoHierarchyService hierarchyService) {
        this.client = client;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.metricExtractor = metricExtractor;
        this.metricService = metricService;
        this.hierarchyService = hierarchyService;
    }

    public ImageRecognitionDtos.Response recognizeUploaded(MultipartFile file, String platform, String scene) {
        String normalizedPlatform = normalizePlatform(platform);
        String normalizedScene = normalizeScene(scene);
        Map<String, Object> payload = client.recognize(file, normalizedPlatform, normalizedScene);
        return client.normalize(payload, normalizedPlatform, normalizedScene);
    }

    @Transactional
    public ImageRecognitionDtos.Response recognizeDataAsset(Long assetId, String platform, String scene) {
        Map<String, Object> asset = queryOne("select * from data_operation_asset where id = ?", assetId);
        String assetType = stringValue(asset.get("asset_type"));
        if (!"COVER".equalsIgnoreCase(assetType) && !"DATA_SCREENSHOT".equalsIgnoreCase(assetType)) {
            throw BusinessException.badRequest("该文件不是可识别的数据图片");
        }
        if ("DATA_SCREENSHOT".equalsIgnoreCase(assetType)) {
            requireAccountConfirmed(asset);
        }
        String objectKey = stringValue(asset.get("object_key"));
        String bucketName = stringValue(asset.get("bucket_name"));
        String fileName = stringValue(asset.get("original_filename"));
        String contentType = stringValue(asset.get("mime_type"));
        byte[] bytes = storageService.readBytes(bucketName, objectKey);

        String normalizedPlatform = normalizePlatform(platform);
        if (normalizedPlatform == null) {
            normalizedPlatform = resolvePlatform(asset);
        }
        String normalizedScene = normalizeScene(scene);
        if (normalizedScene == null) {
            normalizedScene = "CONTENT_DETAIL";
        }

        jdbc.update("update data_operation_asset set upload_status = 'processing' where id = ?", assetId);
        ImageRecognitionDtos.Response response = client.normalize(
                client.recognize(bytes, fileName, contentType, normalizedPlatform, normalizedScene),
                normalizedPlatform,
                normalizedScene);
        writeBack(asset, assetType, response, normalizedPlatform);
        jdbc.update("update data_operation_asset set upload_status = 'recognized' where id = ?", assetId);
        return response;
    }

    private void writeBack(Map<String, Object> asset, String assetType, ImageRecognitionDtos.Response response, String normalizedPlatform) {
        String payloadJson = toJson(response);
        if ("COVER".equalsIgnoreCase(assetType)) {
            Long topicId = numberToLong(asset.get("platform_topic_id"));
            if (topicId == null) return;
            String contentTitle = normalizeRecognizedName(stringFromResult(response, "contentTitle", "topicName", "title", "name", "ocrTitle"));
            String accountName = normalizeRecognizedName(stringFromResult(response, "accountName", "authorName", "nickname"));
            String accountId = normalizeRecognizedName(stringFromResult(response, "douyinId", "wechatChannelId", "videoAccountId", "accountId"));
            String displayTitle = contentTitle != null ? contentTitle : buildAccountDisplay(accountName, accountId);
            jdbc.update("""
                    update data_operation_platform_topic
                    set ocr_status = 'success',
                        sub_topic_name = coalesce(?, sub_topic_name),
                        ocr_title = coalesce(?, ocr_title),
                        ocr_account_name = coalesce(?, ocr_account_name),
                        ocr_platform_user_id = coalesce(?, ocr_platform_user_id),
                        ocr_content_title = coalesce(?, ocr_content_title),
                        ocr_fail_reason = null,
                        ocr_payload_json = ?,
                        recognized_at = ?,
                        updated_at = current_timestamp(6)
                    where id = ?
                    """, contentTitle, displayTitle, accountName, accountId, contentTitle, payloadJson, LocalDateTime.now(), topicId);
            hierarchyService.upsertAccountFromCover(topicId, normalizedPlatform, accountName, accountId);
            return;
        }
        Long contentId = numberToLong(asset.get("content_id"));
        if (contentId == null) return;
        String assetGroup = resolveAssetGroup(asset);
        Map<String, Object> extracted = metricExtractor.extract(assetGroup, response);
        String extractedJson = toJson(extracted);
        Long assetId = numberToLong(asset.get("id"));
        if (assetId != null) {
            jdbc.update("""
                    update data_operation_asset
                    set ocr_payload_json = ?,
                        data_payload_json = ?
                    where id = ?
                    """, payloadJson, extractedJson, assetId);
        }
        jdbc.update("""
                update data_operation_content
                set recognition_status = 'success',
                    data_payload_json = ?,
                    updated_at = current_timestamp(6)
                where id = ?
                """, extractedJson, contentId);
        metricService.upsertAssetMetrics(asset, extracted, normalizedPlatform, resolveContentType(asset));
        DataOperationVideoHierarchyService.HierarchyRef ref = hierarchyService.upsertForRecognizedAsset(asset, response.result(), normalizedPlatform, resolveContentType(asset), payloadJson);
        hierarchyService.attachMetrics(assetId, ref.accountId(), ref.videoId());
    }

    private void requireAccountConfirmed(Map<String, Object> asset) {
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        if (topicId == null) throw BusinessException.badRequest("图片缺少子主题归属");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("select account_confirmed_flag from data_operation_platform_topic where id = ?", topicId);
            Object value = rows.isEmpty() ? null : rows.get(0).get("account_confirmed_flag");
            Long flag = numberToLong(value);
            if (flag != null && flag == 1) return;
        } catch (RuntimeException ignored) {
        }
        throw BusinessException.badRequest("请先上传账号主页并确认账号名称/平台账号ID，再上传或识别图文/视频数据");
    }

    private String resolveAssetGroup(Map<String, Object> asset) {
        String direct = stringValue(asset.get("asset_group"));
        if (direct != null && !direct.isBlank()) return direct;
        String objectKey = stringValue(asset.get("object_key"));
        String marker = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
        if (marker.contains("douyin_flow_analysis")) return "DOUYIN_FLOW_ANALYSIS";
        if (marker.contains("douyin_overview_chart")) return "DOUYIN_OVERVIEW_CHART";
        return "DOUYIN_OVERVIEW";
    }

    private String resolveContentType(Map<String, Object> asset) {
        Long contentId = numberToLong(asset.get("content_id"));
        if (contentId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("select content_type from data_operation_content where id = ?", contentId);
            if (!rows.isEmpty()) {
                String value = stringValue(rows.get(0).get("content_type"));
                if (value != null && !value.isBlank()) return value;
            }
        }
        return "IMAGE_TEXT";
    }

    private String buildAccountDisplay(String accountName, String accountId) {
        if (accountName != null && accountId != null) return accountName + " / " + accountId;
        if (accountName != null) return accountName;
        if (accountId != null) return "账号ID " + accountId;
        return null;
    }

    private String stringFromResult(ImageRecognitionDtos.Response response, String... keys) {
        if (response == null || response.result() == null) return null;
        for (String key : keys) {
            Object value = response.result().get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        Object metrics = response.result().get("metrics");
        if (metrics instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
            }
        }
        return null;
    }

    private String normalizeRecognizedName(String value) {
        if (value == null) return null;
        String result = value.trim().replaceAll("\\s+", " ");
        if (result.isBlank()) return null;
        return result.length() > 180 ? result.substring(0, 180) : result;
    }

    private String resolvePlatform(Map<String, Object> asset) {
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        if (topicId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("select platform_code from data_operation_platform_topic where id = ?", topicId);
            if (!rows.isEmpty()) return normalizePlatform(stringValue(rows.get(0).get("platform_code")));
        }
        Long contentId = numberToLong(asset.get("content_id"));
        if (contentId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("select platform_code from data_operation_content where id = ?", contentId);
            if (!rows.isEmpty()) return normalizePlatform(stringValue(rows.get(0).get("platform_code")));
        }
        return "XIAOHONGSHU";
    }

    private String normalizePlatform(String value) {
        if (value == null || value.isBlank()) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        if ("DOUYIN".equals(code) || "抖音".equals(code)) return "DOUYIN";
        if ("XIAOHONGSHU".equals(code) || "XHS".equals(code) || "小红书".equals(code)) return "XIAOHONGSHU";
        if ("WECHAT_CHANNEL".equals(code) || "VIDEO_ACCOUNT".equals(code) || "视频号".equals(code)) return "WECHAT_CHANNEL";
        throw BusinessException.badRequest("不支持的平台：" + value);
    }

    private String normalizeScene(String value) {
        if (value == null || value.isBlank()) return null;
        String scene = value.trim().toUpperCase(Locale.ROOT);
        if ("CONTENT_DETAIL".equals(scene) || "CONTENT_LIST".equals(scene) || "ACCOUNT_OVERVIEW".equals(scene)) return scene;
        throw BusinessException.badRequest("不支持的识别场景：" + value);
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }
}

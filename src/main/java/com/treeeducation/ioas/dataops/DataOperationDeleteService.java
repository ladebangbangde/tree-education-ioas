package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.storage.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DataOperationDeleteService {
    private final JdbcTemplate jdbc;
    private final ObjectStorageService objectStorageService;

    @Value("${app.upload.base-dir:/app/uploads}")
    private String uploadBaseDir;

    public DataOperationDeleteService(JdbcTemplate jdbc, ObjectStorageService objectStorageService) {
        this.jdbc = jdbc;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public Map<String, Object> deleteAsset(Long assetId) {
        Map<String, Object> asset = queryOne("select * from data_operation_asset where id = ?", assetId, "资源不存在或已删除");
        List<Map<String, Object>> assets = List.of(asset);
        deleteAssetRows(assetIds(assets));
        refreshAfterAssetDelete(asset);
        List<String> warnings = removeStoredObjects(assets);
        return result("ASSET", 1, 0, 0, 0, warnings);
    }

    @Transactional
    public Map<String, Object> deleteContent(Long contentId) {
        Map<String, Object> content = queryOne("select * from data_operation_content where id = ?", contentId, "内容不存在或已删除");
        List<Map<String, Object>> assets = jdbc.queryForList("select * from data_operation_asset where content_id = ?", contentId);
        deleteMetricRowsForAssets(assetIds(assets));
        jdbc.update("delete from data_operation_metric_value where content_id = ?", contentId);
        jdbc.update("delete from data_operation_asset where content_id = ?", contentId);
        jdbc.update("delete from data_operation_content where id = ?", contentId);
        List<String> warnings = removeStoredObjects(assets);
        return result("CONTENT", assets.size(), 1, 0, 0, warnings);
    }

    @Transactional
    public Map<String, Object> deletePlatformTopic(Long topicId) {
        queryOne("select * from data_operation_platform_topic where id = ?", topicId, "平台账号/子主题不存在或已删除");
        List<Map<String, Object>> assets = jdbc.queryForList("select * from data_operation_asset where platform_topic_id = ?", topicId);
        int contentCount = count("select count(*) from data_operation_content where platform_topic_id = ?", topicId);
        deleteMetricRowsForAssets(assetIds(assets));
        jdbc.update("delete from data_operation_metric_value where platform_topic_id = ?", topicId);
        jdbc.update("delete from data_operation_asset where platform_topic_id = ?", topicId);
        jdbc.update("delete from data_operation_content where platform_topic_id = ?", topicId);
        jdbc.update("delete from data_operation_platform_topic where id = ?", topicId);
        List<String> warnings = removeStoredObjects(assets);
        return result("PLATFORM_TOPIC", assets.size(), contentCount, 1, 0, warnings);
    }

    @Transactional
    public Map<String, Object> deletePackage(Long packageId) {
        queryOne("select * from data_operation_topic_package where id = ?", packageId, "主题包不存在或已删除");
        List<Map<String, Object>> assets = jdbc.queryForList("select * from data_operation_asset where package_id = ?", packageId);
        int contentCount = count("select count(*) from data_operation_content where package_id = ?", packageId);
        int topicCount = count("select count(*) from data_operation_platform_topic where package_id = ?", packageId);
        deleteMetricRowsForAssets(assetIds(assets));
        jdbc.update("delete from data_operation_metric_value where topic_package_id = ?", packageId);
        jdbc.update("delete from data_operation_asset where package_id = ?", packageId);
        jdbc.update("delete from data_operation_content where package_id = ?", packageId);
        jdbc.update("delete from data_operation_platform_topic where package_id = ?", packageId);
        jdbc.update("delete from data_operation_topic_package where id = ?", packageId);
        List<String> warnings = removeStoredObjects(assets);
        return result("PACKAGE", assets.size(), contentCount, topicCount, 1, warnings);
    }

    private void deleteAssetRows(List<Long> assetIds) {
        if (assetIds.isEmpty()) return;
        deleteMetricRowsForAssets(assetIds);
        for (Long assetId : assetIds) {
            jdbc.update("delete from data_operation_asset where id = ?", assetId);
        }
    }

    private void deleteMetricRowsForAssets(List<Long> assetIds) {
        if (assetIds.isEmpty()) return;
        for (Long assetId : assetIds) {
            jdbc.update("delete from data_operation_metric_value where asset_id = ?", assetId);
        }
    }

    private void refreshAfterAssetDelete(Map<String, Object> asset) {
        Long assetId = numberToLong(asset.get("id"));
        Long contentId = numberToLong(asset.get("content_id"));
        Long topicId = numberToLong(asset.get("platform_topic_id"));
        String assetType = stringValue(asset.get("asset_type"));
        if (assetId != null && topicId != null && "COVER".equalsIgnoreCase(assetType)) {
            jdbc.update("""
                    update data_operation_platform_topic
                    set cover_asset_id = null,
                        cover_image_url = null,
                        ocr_status = 'PENDING',
                        ocr_payload_json = null,
                        recognized_at = null,
                        updated_at = current_timestamp(6)
                    where id = ? and cover_asset_id = ?
                    """, topicId, assetId);
        }
        if (contentId != null) {
            Integer screenshotCount = jdbc.queryForObject(
                    "select count(*) from data_operation_asset where content_id = ? and asset_type = 'DATA_SCREENSHOT'",
                    Integer.class,
                    contentId
            );
            jdbc.update("""
                    update data_operation_content
                    set screenshot_count = ?,
                        updated_at = current_timestamp(6)
                    where id = ?
                    """, screenshotCount == null ? 0 : screenshotCount, contentId);
        }
    }

    private List<String> removeStoredObjects(List<Map<String, Object>> assets) {
        Set<String> objectKeys = new LinkedHashSet<>();
        for (Map<String, Object> asset : assets) {
            String objectKey = stringValue(asset.get("object_key"));
            if (objectKey != null && !objectKey.isBlank()) objectKeys.add(objectKey);
        }
        List<String> warnings = new ArrayList<>();
        for (String objectKey : objectKeys) {
            try {
                deleteLocalObject(objectKey);
            } catch (Exception ex) {
                warnings.add("本地文件删除失败: " + objectKey + "，" + ex.getMessage());
            }
            try {
                objectStorageService.remove(objectKey);
            } catch (Exception ex) {
                warnings.add("对象存储删除失败: " + objectKey + "，" + ex.getMessage());
            }
        }
        return warnings;
    }

    private void deleteLocalObject(String objectKey) throws Exception {
        if (objectKey == null || objectKey.isBlank()) return;
        Path base = Path.of(uploadBaseDir).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).toAbsolutePath().normalize();
        if (!target.startsWith(base)) throw new IllegalArgumentException("非法文件路径");
        Files.deleteIfExists(target);
    }

    private Map<String, Object> queryOne(String sql, Object id, String notFoundMessage) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) throw BusinessException.notFound(notFoundMessage);
        return rows.get(0);
    }

    private int count(String sql, Object id) {
        Integer value = jdbc.queryForObject(sql, Integer.class, id);
        return value == null ? 0 : value;
    }

    private List<Long> assetIds(List<Map<String, Object>> assets) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> asset : assets) {
            Long id = numberToLong(asset.get("id"));
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private Map<String, Object> result(String scope, int deletedAssets, int deletedContents, int deletedTopics, int deletedPackages, List<String> warnings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope);
        result.put("deletedAssets", deletedAssets);
        result.put("deletedContents", deletedContents);
        result.put("deletedTopics", deletedTopics);
        result.put("deletedPackages", deletedPackages);
        result.put("storageWarnings", warnings == null ? List.of() : warnings);
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

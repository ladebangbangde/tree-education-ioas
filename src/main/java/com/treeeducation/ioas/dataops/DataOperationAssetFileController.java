package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.BusinessException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/assets")
public class DataOperationAssetFileController {
    private final JdbcTemplate jdbc;
    private final DataOperationAssetStorageService storageService;

    public DataOperationAssetFileController(JdbcTemplate jdbc, DataOperationAssetStorageService storageService) {
        this.jdbc = jdbc;
        this.storageService = storageService;
    }

    @GetMapping("/{assetId}/file")
    public ResponseEntity<Resource> previewAssetFile(@PathVariable Long assetId) {
        Map<String, Object> asset = queryOne("select * from data_operation_asset where id = ?", assetId);
        String objectKey = objectToString(asset.get("object_key"));
        String bucketName = objectToString(asset.get("bucket_name"));
        if (objectKey == null || objectKey.isBlank()) throw BusinessException.notFound("文件路径不存在");

        byte[] bytes = storageService.readBytes(bucketName, objectKey);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        String mimeType = objectToString(asset.get("mime_type"));
        MediaType mediaType = parseMediaType(mimeType);
        String originalFilename = objectToString(asset.get("original_filename"));
        if (originalFilename == null || originalFilename.isBlank()) originalFilename = String.valueOf(assetId) + ".bin";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(bytes.length)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(originalFilename, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    private MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
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

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ops/assets")
public class DataOperationAssetFileController {
    private final JdbcTemplate jdbc;

    @Value("${app.upload.base-dir:/app/uploads}")
    private String uploadBaseDir;

    public DataOperationAssetFileController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{assetId}/file")
    public ResponseEntity<Resource> previewAssetFile(@PathVariable Long assetId) throws MalformedURLException {
        Map<String, Object> asset = queryOne("select * from data_operation_asset where id = ?", assetId);
        String objectKey = objectToString(asset.get("object_key"));
        if (objectKey == null || objectKey.isBlank()) throw BusinessException.notFound("文件路径不存在");

        Path base = Path.of(uploadBaseDir).normalize().toAbsolutePath();
        Path target = base.resolve(objectKey).normalize().toAbsolutePath();
        if (!target.startsWith(base) || !Files.exists(target) || !Files.isRegularFile(target)) {
            throw BusinessException.notFound("文件不存在");
        }

        Resource resource = new UrlResource(target.toUri());
        String mimeType = objectToString(asset.get("mime_type"));
        MediaType mediaType = parseMediaType(mimeType);
        String originalFilename = objectToString(asset.get("original_filename"));
        if (originalFilename == null || originalFilename.isBlank()) originalFilename = target.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
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

package com.treeeducation.ioas.recognition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treeeducation.ioas.common.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImageRecognitionClient {
    private final ImageRecognitionProperties properties;
    private final ObjectMapper objectMapper;

    public ImageRecognitionClient(ImageRecognitionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> recognize(MultipartFile file, String platform, String scene) {
        if (!properties.isEnabled()) {
            throw BusinessException.badRequest("图片识别服务未启用");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("识别图片不能为空");
        }
        try {
            return recognize(file.getBytes(), file.getOriginalFilename(), file.getContentType(), platform, scene);
        } catch (IOException ex) {
            throw BusinessException.badRequest("读取识别图片失败：" + ex.getMessage());
        }
    }

    public Map<String, Object> recognize(byte[] bytes, String filename, String contentType, String platform, String scene) {
        if (!properties.isEnabled()) {
            throw BusinessException.badRequest("图片识别服务未启用");
        }
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("识别图片不能为空");
        }
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory())
                .baseUrl(trimRightSlash(properties.getBaseUrl()))
                .build();

        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "image.png" : filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("platform", platform);
        body.add("scene", scene);

        String response = client.post()
                .uri(normalizePath(properties.getRecognizePath()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(properties.getToken()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw BusinessException.badRequest("图片识别服务返回为空");
        }
        try {
            return objectMapper.readValue(response, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("rawText", response);
            raw.put("warnings", List.of("识别服务返回非 JSON，已按原文返回"));
            return raw;
        }
    }

    public ImageRecognitionDtos.Response normalize(Map<String, Object> payload, String platform, String scene) {
        Map<String, Object> root = unwrapApiResponse(payload);
        String requestId = stringValue(root.get("requestId"), root.get("request_id"), payload.get("requestId"), payload.get("request_id"));
        String engine = stringValue(root.get("engine"), payload.get("engine"));
        String rawText = stringValue(root.get("rawText"), root.get("raw_text"), payload.get("rawText"), payload.get("raw_text"));
        Long elapsedMs = longValue(root.get("elapsedMs"), root.get("elapsed_ms"), payload.get("elapsedMs"), payload.get("elapsed_ms"));
        List<String> warnings = listValue(root.get("warnings"), payload.get("warnings"));
        Map<String, Object> result = mapValue(root.get("result"));
        if (result.isEmpty()) {
            result = mapValue(root);
        }
        return new ImageRecognitionDtos.Response(
                requestId,
                engine,
                stringValue(root.get("platform"), payload.get("platform"), platform),
                stringValue(root.get("scene"), payload.get("scene"), scene),
                rawText,
                result,
                warnings,
                elapsedMs,
                payload
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapApiResponse(Map<String, Object> payload) {
        if (payload == null) return Map.of();
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private List<String> listValue(Object... values) {
        for (Object value : values) {
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    private String stringValue(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private Long longValue(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) return number.longValue();
            if (value != null && !String.valueOf(value).isBlank()) {
                try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return factory;
    }

    private String trimRightSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:18083";
        String cleaned = value.trim();
        while (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    private URI normalizePath(String path) {
        String cleaned = path == null || path.isBlank() ? "/api/v1/recognize" : path.trim();
        return URI.create(cleaned.startsWith("/") ? cleaned : "/" + cleaned);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

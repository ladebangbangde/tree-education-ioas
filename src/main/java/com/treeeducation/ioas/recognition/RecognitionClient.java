package com.treeeducation.ioas.recognition;

import com.treeeducation.ioas.recognition.dto.RecognitionDtos.RecognitionResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** Calls tree-education-datacollecting through the OA backend. */
@Service
public class RecognitionClient {
    private final RecognitionProperties properties;
    private final RestClient restClient;

    public RecognitionClient(RecognitionProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public RecognitionResponse recognize(MultipartFile file, String platform, String scene, String contentType) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "recognition service is disabled");
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new MultipartByteArrayResource(file.getBytes(), safeFilename(file.getOriginalFilename())));
            body.add("platform", defaultValue(platform, "UNKNOWN"));
            body.add("scene", defaultValue(scene, "CONTENT_DETAIL"));
            body.add("contentType", defaultValue(contentType, "AUTO"));

            return restClient.post()
                    .uri(recognitionUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(RecognitionResponse.class);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read uploaded screenshot", e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "failed to call recognition service", e);
        }
    }

    private URI recognitionUri() {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String path = properties.getRecognizePath() == null ? "/api/v1/recognize" : properties.getRecognizePath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(baseUrl + path);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://tree-education-datacollecting:18083";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "screenshot.png" : filename;
    }

    private static final class MultipartByteArrayResource extends ByteArrayResource {
        private final String filename;

        private MultipartByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}

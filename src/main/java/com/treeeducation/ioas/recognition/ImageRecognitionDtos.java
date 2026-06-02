package com.treeeducation.ioas.recognition;

import java.util.List;
import java.util.Map;

public final class ImageRecognitionDtos {
    private ImageRecognitionDtos() {}

    public record SocialMetricsRequest(String platform, String scene) {}

    public record Response(String requestId,
                           String engine,
                           String platform,
                           String scene,
                           String rawText,
                           Map<String, Object> result,
                           List<String> warnings,
                           Long elapsedMs,
                           Map<String, Object> rawPayload) {}
}

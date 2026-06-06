package com.treeeducation.ioas.recognition.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public final class RecognitionDtos {
    private RecognitionDtos() {
    }

    @Schema(description = "截图识别响应")
    public record RecognitionResponse(
            String requestId,
            String engine,
            String platform,
            String scene,
            String contentType,
            String rawText,
            RecognitionResult result,
            List<String> warnings,
            Integer elapsedMs
    ) {
    }

    @Schema(description = "截图识别结构化结果")
    public record RecognitionResult(
            String accountName,
            String accountId,
            String douyinId,
            String wechatChannelId,
            String contentType,
            String contentTitle,
            List<String> candidateTitles,
            Metrics metrics,
            ImageTextStats imageTextStats,
            VideoStats videoStats,
            Map<String, Object> keyValueMetrics,
            Double confidence
    ) {
    }

    @Schema(description = "兼容旧版 OA 的通用指标")
    public record Metrics(
            Long viewCount,
            Long likeCount,
            Long commentCount,
            Long favoriteCount,
            Long shareCount,
            Long followerCount,
            Long followerGain,
            String completionRate,
            String interactionRate,
            Double averageWatchSeconds,
            Long profileVisitCount
    ) {
    }

    @Schema(description = "图文/笔记专属指标")
    public record ImageTextStats(
            Long readCount,
            Long viewCount,
            Long likeCount,
            Long commentCount,
            Long favoriteCount,
            Long shareCount,
            Long imageCount,
            String coverClickRate,
            String copyExpandRate,
            String copyFinishRate,
            String commentEnterRate,
            String slideAwayRate,
            Long followerGain
    ) {
    }

    @Schema(description = "视频专属指标")
    public record VideoStats(
            Long playCount,
            Long exposureCount,
            Long likeCount,
            Long commentCount,
            Long favoriteCount,
            Long shareCount,
            String completionRate,
            String fiveSecondCompletionRate,
            Double averageWatchSeconds,
            String averageWatchText,
            Double durationSeconds,
            String durationText,
            String interactionRate,
            Long followerGain,
            Long profileVisitCount
    ) {
    }
}

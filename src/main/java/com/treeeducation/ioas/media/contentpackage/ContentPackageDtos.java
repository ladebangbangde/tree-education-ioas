package com.treeeducation.ioas.media.contentpackage;

import com.treeeducation.ioas.media.assetfile.AssetFileDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Content package DTOs. */
public final class ContentPackageDtos {
    private ContentPackageDtos() {}

    @Schema(description = "创建/编辑主题包请求")
    public record UpsertRequest(@NotNull Long operatorId, @NotBlank String topicName) {}

    @Schema(description = "主题包列表响应")
    public record Response(Long id, String packageNo, String topicName, Long operatorId, String operatorName,
                           String fullPath, String coverUrl, Integer scriptCount, Integer videoCount,
                           Integer imageCount, ContentPackageStatus uploadStatus, Long createdBy,
                           String createdByName, Instant createdAt) {}

    @Schema(description = "主题包详情响应")
    public record DetailResponse(Response packageInfo, List<AssetFileDtos.Response> scripts,
                                 List<AssetFileDtos.Response> videos, List<AssetFileDtos.Response> images) {}

    public static Response of(ContentPackage p) {
        return new Response(p.getId(), p.getPackageNo(), p.getTopicName(), p.getOperatorId(), p.getOperatorName(),
                p.getFullPath(), p.getCoverUrl(), p.getScriptCount(), p.getVideoCount(), p.getImageCount(),
                p.getUploadStatus(), p.getCreatedBy(), p.getCreatedByName(), p.getCreatedAt());
    }
}

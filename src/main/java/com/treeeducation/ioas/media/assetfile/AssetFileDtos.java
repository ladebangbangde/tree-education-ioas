package com.treeeducation.ioas.media.assetfile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Asset file DTOs. */
public final class AssetFileDtos {
    private AssetFileDtos() {}

    @Schema(description = "素材文件响应")
    public record Response(Long id, String fileNo, Long packageId, String fileName, AssetFileType fileType,
                           String mimeType, Long fileSize, String bucketName, String objectKey,
                           String thumbnailUrl, String previewUrl, Integer sortOrder, UploadStatus uploadStatus,
                           Long createdBy, String createdByName, Instant createdAt, Instant updatedAt,
                           Boolean isDeleted, Instant deletedAt, Long deletedBy, Instant purgeAt) {}

    @Schema(description = "回收站文件响应")
    public record RecycleBinResponse(Long id, String fileName, AssetFileType fileType, Long packageId,
                                     String packageTopicName, Long operatorId, String operatorName, String fullPath,
                                     Long fileSize, Long deletedBy, Instant deletedAt, Instant purgeAt,
                                     long remainSeconds) {}

    @Schema(description = "多文件上传汇总")
    public record UploadSummary(List<Response> scripts, List<Response> videos, List<Response> images,
                                int scriptCount, int videoCount, int imageCount, UploadStatus uploadStatus) {}

    public static Response of(AssetFile f) {
        return new Response(f.getId(), f.getFileNo(), f.getPackageId(), f.getFileName(), f.getFileType(),
                f.getMimeType(), f.getFileSize(), f.getBucketName(), f.getObjectKey(), f.getThumbnailUrl(),
                f.getPreviewUrl(), f.getSortOrder(), f.getUploadStatus(), f.getCreatedBy(), f.getCreatedByName(),
                f.getCreatedAt(), f.getUpdatedAt(), f.getIsDeleted(), f.getDeletedAt(), f.getDeletedBy(), f.getPurgeAt());
    }
}

package com.treeeducation.ioas.task.dto;

import com.treeeducation.ioas.media.assetfile.AssetFileType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "S3 Multipart 完成请求")
public record MultipartCompleteRequest(
        String uploadId,
        String bucketName,
        String objectKey,
        String publicUrl,
        String fileName,
        Long fileSize,
        String mimeType,
        AssetFileType fileType,
        List<Part> parts
) {
    public record Part(Integer partNumber, String etag) {}
}

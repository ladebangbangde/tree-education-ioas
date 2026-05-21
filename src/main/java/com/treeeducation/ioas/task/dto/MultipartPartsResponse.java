package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "S3 Multipart 已上传分片响应")
public record MultipartPartsResponse(
        Long taskId,
        String bucketName,
        String objectKey,
        String uploadId,
        Long fileSize,
        Long partSize,
        Integer partCount,
        Integer completedPartCount,
        Long uploadedBytes,
        List<Part> parts
) {
    public record Part(Integer partNumber, String etag, Long size) {}
}

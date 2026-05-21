package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 Multipart 创建响应")
public record MultipartCreateResponse(
        Long taskId,
        String bucketName,
        String objectKey,
        String uploadId,
        String publicUrl,
        Long partSize,
        Integer partCount
) {
}

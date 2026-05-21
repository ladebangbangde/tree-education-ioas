package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 Multipart 分片签名请求")
public record MultipartSignPartRequest(
        String uploadId,
        String bucketName,
        String objectKey,
        Integer partNumber
) {
}

package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 Multipart 分片签名响应")
public record MultipartSignPartResponse(
        String url,
        Integer partNumber
) {
}

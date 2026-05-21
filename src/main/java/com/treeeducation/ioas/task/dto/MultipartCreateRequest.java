package com.treeeducation.ioas.task.dto;

import com.treeeducation.ioas.media.assetfile.AssetFileType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 Multipart 创建请求")
public record MultipartCreateRequest(
        @Schema(description = "文件名") String fileName,
        @Schema(description = "文件大小") Long fileSize,
        @Schema(description = "MIME 类型") String mimeType,
        @Schema(description = "业务文件类型") AssetFileType fileType,
        @Schema(description = "建议分片大小，字节") Long partSize
) {
}

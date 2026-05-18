package com.treeeducation.ioas.task.dto;

import com.treeeducation.ioas.media.assetfile.AssetFileType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建上传任务请求")
public record UploadTaskCreateRequest(
        @Schema(description = "业务包ID，传0或空会先创建占位上传任务", example = "0") Long packageId,
        @Schema(description = "文件名", example = "offer.pdf") String fileName,
        @Schema(description = "文件大小，字节", example = "1048576") Long fileSize,
        @Schema(description = "MIME类型", example = "application/pdf") String mimeType,
        @Schema(description = "文件类型", example = "image") AssetFileType fileType
) {
}

package com.treeeducation.ioas.task.dto;

import com.treeeducation.ioas.media.assetfile.AssetFileType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "上传任务直传签名请求")
public record UploadTaskPresignRequest(
        @Schema(description = "文件名", example = "offer.mp4") String fileName,
        @Schema(description = "文件大小，字节", example = "1048576") Long fileSize,
        @Schema(description = "MIME类型", example = "video/mp4") String mimeType,
        @Schema(description = "文件类型", example = "video") AssetFileType fileType
) {
}

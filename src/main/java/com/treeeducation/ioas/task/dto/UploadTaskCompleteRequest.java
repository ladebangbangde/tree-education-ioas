package com.treeeducation.ioas.task.dto;

import com.treeeducation.ioas.media.assetfile.AssetFileType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "完成上传任务请求")
public record UploadTaskCompleteRequest(
        @Schema(description = "文件名", example = "offer.pdf") String fileName,
        @Schema(description = "对象存储Key", example = "2026/05/18/uuid.pdf") String objectKey,
        @Schema(description = "Bucket名称", example = "ioas-assets") String bucketName,
        @Schema(description = "公网预览/下载地址", example = "http://121.41.95.26:19000/ioas-assets/2026/05/18/uuid.pdf") String publicUrl,
        @Schema(description = "MIME类型", example = "application/pdf") String mimeType,
        @Schema(description = "文件大小，字节", example = "1048576") Long fileSize,
        @Schema(description = "文件类型", example = "image") AssetFileType fileType
) {
}

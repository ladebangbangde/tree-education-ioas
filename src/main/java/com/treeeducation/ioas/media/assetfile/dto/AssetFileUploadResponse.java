package com.treeeducation.ioas.media.assetfile.dto;

import com.treeeducation.ioas.media.assetfile.AssetFileType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件上传响应")
public record AssetFileUploadResponse(
        @Schema(description = "文件记录ID", example = "1") Long id,
        @Schema(description = "文件编号", example = "FILE202605181234567890") String fileNo,
        @Schema(description = "业务包ID", example = "0") Long packageId,
        @Schema(description = "原始文件名", example = "offer.pdf") String fileName,
        @Schema(description = "文件业务类型", example = "image") AssetFileType fileType,
        @Schema(description = "MIME类型", example = "image/png") String mimeType,
        @Schema(description = "文件大小，字节", example = "102400") Long fileSize,
        @Schema(description = "Bucket名称", example = "ioas-assets") String bucketName,
        @Schema(description = "对象存储Key", example = "2026/05/18/uuid.png") String objectKey,
        @Schema(description = "公网预览/下载地址", example = "http://121.41.95.26:19000/ioas-assets/2026/05/18/uuid.png") String publicUrl
) {
}

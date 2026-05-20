package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "上传任务直传签名响应")
public record UploadTaskPresignResponse(
        @Schema(description = "任务ID") Long taskId,
        @Schema(description = "Bucket名称") String bucketName,
        @Schema(description = "对象存储Key") String objectKey,
        @Schema(description = "MinIO/OSS直传URL") String uploadUrl,
        @Schema(description = "公网预览/下载地址") String publicUrl,
        @Schema(description = "有效期秒数") Integer expireSeconds
) {
}

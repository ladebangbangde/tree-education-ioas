package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "上传任务响应")
public record UploadTaskResponse(
        @Schema(description = "任务ID", example = "1") Long taskId,
        @Schema(description = "任务状态", example = "created") String status,
        @Schema(description = "任务进度", example = "0") Integer progress,
        @Schema(description = "关联资源包ID", example = "1001") Long packageId
) {
}

package com.treeeducation.ioas.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "批量删除任务请求")
public record TaskBatchDeleteRequest(
        @Schema(description = "任务ID列表") List<Long> taskIds,
        @Schema(description = "是否同时永久删除对象存储文件", example = "true") Boolean purgeFiles
) {
}

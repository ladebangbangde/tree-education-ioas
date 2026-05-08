package com.treeeducation.ioas.task;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "任务类型")
public enum TaskType {
    media_upload, operator_lead_generate
}

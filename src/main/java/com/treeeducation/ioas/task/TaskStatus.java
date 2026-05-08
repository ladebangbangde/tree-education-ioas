package com.treeeducation.ioas.task; import io.swagger.v3.oas.annotations.media.Schema; @Schema(description="任务状态") public enum TaskStatus{ pending, in_progress, completed, cancelled }

package com.treeeducation.ioas.task;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "媒体任务状态")
public enum MediaTaskStatus {
    uploading, success, failed, partial_success, pending_supplement
}

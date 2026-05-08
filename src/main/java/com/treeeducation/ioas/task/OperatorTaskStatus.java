package com.treeeducation.ioas.task;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "运营任务状态")
public enum OperatorTaskStatus {
    pending, processing, completed, overdue, rejected
}

package com.treeeducation.ioas.task;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "任务所属角色")
public enum TaskRoleType {
    media, operator
}

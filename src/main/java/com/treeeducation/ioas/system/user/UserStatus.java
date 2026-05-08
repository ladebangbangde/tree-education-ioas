package com.treeeducation.ioas.system.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** User account status. */
@Schema(description = "用户状态")
public enum UserStatus { ACTIVE, DISABLED }

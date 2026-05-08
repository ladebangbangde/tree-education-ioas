package com.treeeducation.ioas.system.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** Frontend-aligned user roles. */
@Schema(description = "用户角色：SUPER_ADMIN / MEDIA / OPERATOR / CONSULTANT")
public enum UserRole {
    SUPER_ADMIN, MEDIA, OPERATOR, CONSULTANT
}

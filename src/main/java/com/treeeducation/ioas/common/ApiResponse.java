package com.treeeducation.ioas.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** Unified API response wrapper. */
@Schema(description = "统一响应体")
public record ApiResponse<T>(
        @Schema(description = "业务状态码，0 表示成功", example = "0") int code,
        @Schema(description = "响应消息", example = "success") String message,
        @Schema(description = "响应数据") T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "success", data); }
    public static ApiResponse<Void> ok() { return new ApiResponse<>(0, "success", null); }
    public static ApiResponse<Void> error(int code, String message) { return new ApiResponse<>(code, message, null); }
}

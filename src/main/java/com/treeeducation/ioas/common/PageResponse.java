package com.treeeducation.ioas.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/** Cursor-free page response used by list endpoints. */
@Schema(description = "分页响应")
public record PageResponse<T>(@Schema(description = "数据列表") List<T> items,
                              @Schema(description = "总条数") long total,
                              @Schema(description = "页码，从 0 开始") int page,
                              @Schema(description = "每页条数") int size) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }
}

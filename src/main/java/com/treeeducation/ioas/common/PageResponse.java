package com.treeeducation.ioas.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/** Page response used by frontend lists. */
@Schema(description = "分页响应")
public record PageResponse<T>(List<T> items, long total, int pageNum, int pageSize) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public static <T> PageResponse<T> of(List<T> all, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int from = Math.min((safePageNum - 1) * safePageSize, all.size());
        int to = Math.min(from + safePageSize, all.size());
        return new PageResponse<>(all.subList(from, to), all.size(), safePageNum, safePageSize);
    }
}

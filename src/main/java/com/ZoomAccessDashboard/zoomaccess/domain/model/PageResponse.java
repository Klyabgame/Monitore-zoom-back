package com.ZoomAccessDashboard.zoomaccess.domain.model;

import java.util.List;

/**
 * Generic container for paginated results.
 */
public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, long totalElements, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(content, totalElements, page, size, totalPages);
    }
}

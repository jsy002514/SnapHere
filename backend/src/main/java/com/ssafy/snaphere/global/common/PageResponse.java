package com.ssafy.snaphere.global.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** API 규약: page 는 1부터. Spring Data 는 0부터라 여기서 변환한다. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}

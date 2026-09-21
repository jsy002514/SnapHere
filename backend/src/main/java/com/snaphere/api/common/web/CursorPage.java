package com.snaphere.api.common.web;

import java.util.List;

/**
 * 커서 페이징 응답. (SYS-003, SYS-004, CMU-010)
 * 커서는 클라이언트가 해석하지 않는 불투명 문자열이다.
 * 명세: 3. 응답 스키마 > CursorPage&lt;T&gt;
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext
) {
    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor, nextCursor != null);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}

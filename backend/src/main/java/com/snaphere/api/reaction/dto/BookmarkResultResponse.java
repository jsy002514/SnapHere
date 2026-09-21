package com.snaphere.api.reaction.dto;

import com.snaphere.api.reaction.BookmarkTargetType;

import java.time.OffsetDateTime;

/**
 * 명세: 3. 응답 스키마 &gt; BookmarkResult
 *
 * @param savedAt 저장 시각. 해제한 뒤에는 null 이다 — 앱이 저장함 목록에서 정렬 키로 쓴다
 */
public record BookmarkResultResponse(
        String targetType,
        String targetId,
        boolean isBookmarked,
        OffsetDateTime savedAt
) {
    public static BookmarkResultResponse saved(BookmarkTargetType targetType, long targetId,
                                               OffsetDateTime savedAt) {
        return new BookmarkResultResponse(
                targetType.name(), String.valueOf(targetId), true, savedAt);
    }

    public static BookmarkResultResponse removed(BookmarkTargetType targetType, long targetId) {
        return new BookmarkResultResponse(
                targetType.name(), String.valueOf(targetId), false, null);
    }
}

package com.snaphere.api.user;

import java.util.UUID;

/**
 * 게시글·댓글 응답에 붙는 작성자 정보. 명세: 3. 응답 스키마 &gt; UserSummary
 *
 * <p>{@code users} 테이블 전체가 아니라 목록 응답에 필요한 만큼만 담는다 (SYS-018).
 */
public record AuthorSnapshot(
        UUID userId,
        String nickname,
        String profileImageUrl
) {
}

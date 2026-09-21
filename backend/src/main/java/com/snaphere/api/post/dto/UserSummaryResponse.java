package com.snaphere.api.post.dto;

import com.snaphere.api.user.AuthorSnapshot;

/** 명세: 3. 응답 스키마 &gt; UserSummary. 목록·상세에 공통으로 붙는 작성자 요약. */
public record UserSummaryResponse(
        String userId,
        String nickname,
        String profileImageUrl
) {
    public static UserSummaryResponse from(AuthorSnapshot author) {
        return new UserSummaryResponse(
                author.userId().toString(), author.nickname(), author.profileImageUrl());
    }
}

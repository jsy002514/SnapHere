package com.snaphere.api.post.share;

/**
 * 명세: 3. 응답 스키마 &gt; ShareMetadata.
 *
 * <p>공개 웹 페이지가 이 값으로 OG 태그를 그린다 (CMU-020). HTML 을 서버가 만들지 않는 이유는
 * 공개 페이지가 프론트엔드 배포물이기 때문이다 — 여기서는 그 페이지가 채울 값만 준다.
 *
 * <p>{@code imageUrl} 은 사진이 아직 후처리 전이면 null 일 수 있다. 미리보기에 이미지가 없어도
 * 링크 자체는 열려야 하므로 오류로 만들지 않는다.
 */
public record ShareMetadataResponse(
        String shareUrl,
        String title,
        String description,
        String imageUrl
) {
}

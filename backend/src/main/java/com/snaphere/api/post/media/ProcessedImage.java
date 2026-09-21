package com.snaphere.api.post.media;

import java.math.BigDecimal;

/**
 * 이미지 후처리 결과. (PST-019)
 *
 * @param sanitized   메타데이터를 지운 공개용 이미지 (PST-020)
 * @param thumbnail   목록용 축소 이미지
 * @param sha256      <b>원본</b> 바이트의 SHA-256. 중복 판정 기준이다 (PST-031)
 * @param aspectRatio 가로/세로. 메이슨리 카드 높이 계산에 쓴다 (PST-021)
 * @param contentType {@code sanitized}·{@code thumbnail} 의 MIME 타입
 */
public record ProcessedImage(
        byte[] sanitized,
        byte[] thumbnail,
        String sha256,
        BigDecimal aspectRatio,
        String contentType
) {
}

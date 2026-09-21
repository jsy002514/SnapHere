package com.snaphere.api.media.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 발급된 업로드 주소 한 건.
 * 명세: 3. 응답 스키마 > UploadUrl
 *
 * @param imageKey  서버가 정한 S3 객체 키. 게시글 등록(API-PST-003)에 이 값을 그대로 보낸다
 * @param uploadUrl 앱이 PUT 할 주소. 서버는 원본 바이트를 중계하지 않는다 (PST-014)
 * @param headers   PUT 할 때 반드시 함께 보내야 하는 헤더
 * @param expiresAt 만료 시각. 발급 후 5분 (SYS-020)
 */
public record UploadUrl(
        String imageKey,
        String uploadUrl,
        Map<String, String> headers,
        OffsetDateTime expiresAt
) {
}

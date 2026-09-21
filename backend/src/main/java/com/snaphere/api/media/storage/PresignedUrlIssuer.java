package com.snaphere.api.media.storage;

import java.time.Duration;

/**
 * 객체 키 하나에 대한 업로드용 서명 주소를 발급한다.
 *
 * <p>저장소 종류(S3 / 로컬 스텁)를 도메인에서 감추기 위한 포트다.
 * 서버가 파일 바이트를 중계하지 않는다는 원칙(PST-014, SYS-020)은 구현이 무엇이든 같다.
 */
public interface PresignedUrlIssuer {

    Issued issue(String objectKey, String contentType, Duration ttl);

    /**
     * @param url     앱이 PUT 할 주소
     * @param headers PUT 시 반드시 붙여야 하는 헤더
     */
    record Issued(String url, java.util.Map<String, String> headers) {
    }
}

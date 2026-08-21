package com.ssafy.snaphere.domain.media.service;

/**
 * 미디어 저장소 추상화.
 *
 * 왜 인터페이스로 두는가
 *   S3 를 붙이려면 AWS 계정·키·버킷이 필요한데, 그게 준비되기 전에도 업로드 흐름 전체를
 *   프론트와 함께 개발·시연할 수 있어야 한다. 지금은 LocalMediaStorage 로 돌리고,
 *   키가 준비되면 S3MediaStorage 구현체 하나만 추가하면 된다. 서비스 코드는 바뀌지 않는다.
 *
 * S3 로 교체하는 방법
 *   1) build.gradle 의 awssdk 의존성 주석을 푼다
 *   2) 이 인터페이스를 구현한 S3MediaStorage 를 만들고 @Primary 또는 프로필 조건을 붙인다
 *      - issueUpload: S3Presigner 로 PUT presigned URL 발급
 *      - publicUrl:   CloudFront 또는 S3 퍼블릭 URL
 *   3) LocalMediaStorage 는 @ConditionalOnMissingBean 이므로 자동으로 비활성화된다
 */
public interface MediaStorage {

    /** 업로드용 URL 발급. 클라이언트가 이 URL 로 직접 PUT 한다. */
    Upload issueUpload(String mediaKey, String contentType);

    /** 저장된 미디어의 조회 URL */
    String publicUrl(String mediaKey);

    /** 업로드 URL 유효시간(초) */
    int expiresInSeconds();

    record Upload(String uploadUrl, String mediaKey, int expiresIn) {}
}

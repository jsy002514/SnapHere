package com.snaphere.api.media.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 미디어 저장소 설정.
 *
 * @param provider           s3 또는 stub. 로컬 개발에서는 stub
 * @param bucket             S3 버킷 이름
 * @param region             S3 리전
 * @param publicBaseUrl      업로드된 객체를 읽을 때 붙일 CDN·버킷 기본 주소
 * @param presignTtl         서명 주소 유효 시간. 기본 5분 (SYS-020, PST-013)
 * @param maxFileSizeBytes   장당 최대 용량. 기본 10MB (PST-015)
 */
@ConfigurationProperties(prefix = "snaphere.media")
public record MediaStorageProperties(
        String provider,
        String bucket,
        String region,
        String publicBaseUrl,
        Duration presignTtl,
        long maxFileSizeBytes
) {
    public MediaStorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        }
        if (presignTtl == null) {
            presignTtl = Duration.ofMinutes(5);
        }
        if (maxFileSizeBytes <= 0) {
            maxFileSizeBytes = 10L * 1024 * 1024;
        }
    }
}

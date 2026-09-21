package com.snaphere.api.media.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 로컬 개발용 스텁. 실제로 업로드되지는 않고 형태만 같은 주소를 돌려준다.
 *
 * <p>S3 버킷·IAM 이 준비되기 전에도 앱이 업로드 흐름(PST-013, PST-014)을 붙여볼 수 있게 한다.
 * {@code snaphere.media.provider=stub} 일 때만 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "snaphere.media", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubPresignedUrlIssuer implements PresignedUrlIssuer {

    private final MediaStorageProperties properties;

    public StubPresignedUrlIssuer(MediaStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Issued issue(String objectKey, String contentType, Duration ttl) {
        String base = properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()
                ? "http://localhost:8080/local-storage"
                : properties.publicBaseUrl();
        String url = base + "/" + objectKey + "?stub-presign=true&ttlSeconds=" + ttl.toSeconds();
        return new Issued(url, Map.of("Content-Type", contentType));
    }
}

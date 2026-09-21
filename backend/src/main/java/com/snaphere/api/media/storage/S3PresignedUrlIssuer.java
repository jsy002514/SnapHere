package com.snaphere.api.media.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * S3 Presigned PUT URL 발급. (PST-013, PST-014, SYS-020)
 *
 * <p>{@code snaphere.media.provider=s3} 일 때만 등록된다.
 * 자격증명은 SDK 기본 체인(환경변수 · 프로파일 · 인스턴스 역할)을 그대로 쓴다.
 * 애플리케이션 설정 파일에 키를 적지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "snaphere.media", name = "provider", havingValue = "s3")
public class S3PresignedUrlIssuer implements PresignedUrlIssuer {

    private final MediaStorageProperties properties;
    private final S3Presigner presigner;

    public S3PresignedUrlIssuer(MediaStorageProperties properties) {
        this.properties = properties;
        this.presigner = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    @Override
    public Issued issue(String objectKey, String contentType, Duration ttl) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        presigned.signedHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return new Issued(presigned.url().toString(), Map.copyOf(headers));
    }

    @PreDestroy
    void close() {
        presigner.close();
    }
}

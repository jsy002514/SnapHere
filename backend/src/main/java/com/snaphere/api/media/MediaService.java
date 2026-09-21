package com.snaphere.api.media;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.media.dto.PresignFileRequest;
import com.snaphere.api.media.dto.PresignRequest;
import com.snaphere.api.media.dto.UploadUrl;
import com.snaphere.api.media.storage.MediaStorageProperties;
import com.snaphere.api.media.storage.PresignedUrlIssuer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 업로드용 Presigned URL 발급. (PST-013, PST-014, PST-015, USER-004, SYS-020)
 *
 * <p>검증 순서가 응답 코드를 정한다.
 * <ol>
 *   <li>개수 — 게시글 1~4장 / 프로필 1장 → {@code MEDIA_COUNT_INVALID} 422 (PST-013)</li>
 *   <li>형식 — jpeg·png·heic·webp → {@code MEDIA_TYPE_UNSUPPORTED} 415 (PST-015)</li>
 *   <li>용량 — 장당 10MB → {@code MEDIA_TOO_LARGE} 413 (PST-015)</li>
 * </ol>
 *
 * <p>객체 키는 서버가 만든다. 클라이언트가 준 파일명을 쓰면 경로를 조작당하거나
 * 남의 파일을 덮어쓸 수 있다.
 */
@Service
public class MediaService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // SYS-005

    private final PresignedUrlIssuer issuer;
    private final MediaStorageProperties properties;

    public MediaService(PresignedUrlIssuer issuer, MediaStorageProperties properties) {
        this.issuer = issuer;
        this.properties = properties;
    }

    public List<UploadUrl> issueUploadUrls(UUID userId, PresignRequest request) {
        MediaPurpose purpose = request.purpose();
        List<PresignFileRequest> files = request.files();

        if (!purpose.allows(files.size())) {
            throw new ApiException(ErrorCode.MEDIA_COUNT_INVALID, Map.of(
                    "min", purpose.minCount(),
                    "max", purpose.maxCount(),
                    "actual", files.size()));
        }

        Duration ttl = properties.presignTtl();
        OffsetDateTime expiresAt = OffsetDateTime.now(KST).plus(ttl);

        List<UploadUrl> issued = new ArrayList<>(files.size());
        for (PresignFileRequest file : files) {
            AllowedImageType type = AllowedImageType.from(file.mimeType())
                    .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_TYPE_UNSUPPORTED, Map.of(
                            "mimeType", String.valueOf(file.mimeType()))));

            if (file.sizeBytes() > properties.maxFileSizeBytes()) {
                throw new ApiException(ErrorCode.MEDIA_TOO_LARGE, Map.of(
                        "maxBytes", properties.maxFileSizeBytes(),
                        "actualBytes", file.sizeBytes()));
            }

            String objectKey = buildObjectKey(purpose, userId, type);
            PresignedUrlIssuer.Issued result = issuer.issue(objectKey, type.mimeType(), ttl);
            issued.add(new UploadUrl(objectKey, result.url(), result.headers(), expiresAt));
        }
        return issued;
    }

    /** {@code posts/{userId}/{uuid}.webp} 형태. 확장자는 검증된 형식에서만 나온다. */
    private String buildObjectKey(MediaPurpose purpose, UUID userId, AllowedImageType type) {
        return (purpose == MediaPurpose.POST_IMAGE ? "originals/" : "") + purpose.keyPrefix()
                + "/" + userId
                + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + type.extension();
    }
}

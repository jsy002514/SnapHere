package com.snaphere.api.media.storage;

import com.snaphere.api.media.MediaPurpose;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 객체 키를 공개 주소로 바꾸고, 그 키가 요청자의 것인지 확인한다.
 *
 * <p>DB 에는 전체 URL 이 아니라 객체 키만 저장한다 (PST-013). 버킷·CDN 을 갈아도 저장된 행을
 * 고치지 않아도 되고, 같은 사진을 다른 크기로 서비스할 여지가 남는다.
 */
@Component
public class MediaUrlResolver {

    private final MediaStorageProperties properties;

    public MediaUrlResolver(MediaStorageProperties properties) {
        this.properties = properties;
    }

    public String publicUrl(String objectKey) {
        if (objectKey == null || MediaObjectKeys.isPrivateOriginal(objectKey)) return null;
        String base = properties.publicBaseUrl();
        if (base == null || base.isBlank()) {
            // 로컬 개발에서는 CDN 주소가 없다. 앱이 상대 경로를 그대로 붙일 수 있게 키를 준다.
            return "/" + objectKey;
        }
        return base.endsWith("/") ? base + objectKey : base + "/" + objectKey;
    }

    /**
     * 게시글 사진 키가 이 사용자에게 발급된 것인지. (PST-014)
     *
     * <p>키는 {@code posts/{userId}/{uuid}.{ext}} 라서 접두어만 보면 소유자를 알 수 있다.
     * 이 검사를 빼면 남의 발급 키를 그대로 넣어 남의 사진을 자기 게시글로 만들 수 있다.
     */
    public boolean isOwnedPostImageKey(String objectKey, UUID userId) {
        if (objectKey == null) {
            return false;
        }
        String expected = "originals/" + MediaPurpose.POST_IMAGE.keyPrefix() + "/" + userId + "/";
        return objectKey.startsWith(expected)
                && objectKey.length() > expected.length()
                && !objectKey.substring(expected.length()).contains("/");
    }
}

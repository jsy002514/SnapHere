package com.snaphere.api.media.storage;

/**
 * 후처리가 만드는 파생 객체의 키 규칙. (PST-019, PST-020)
 *
 * <p>공개용 이미지는 <b>발급받은 키를 그대로 덮어쓴다.</b> 새 키를 만들면 이미 저장된
 * {@code post_images.image_key} 행과 앱이 들고 있는 주소가 전부 어긋난다. 재인코딩으로 형식이
 * JPEG 이 되어 키의 확장자와 달라질 수 있는데, 브라우저·앱은 확장자가 아니라 응답의
 * {@code Content-Type} 을 보므로 문제가 되지 않는다.
 *
 * <p>원본은 좌표가 그대로 남은 채 {@link #original} 로 옮겨 둔다. 심사에서 "위치를 어떻게
 * 검증했는가"의 근거가 되고, 후처리를 다시 돌려야 할 때 다시 계산할 수 있다 (PST-020 비고).
 */
public final class MediaObjectKeys {

    private static final String ORIGINAL_PREFIX = "originals/";
    private static final String PUBLIC_PREFIX = "public/";
    private static final String THUMBNAIL_PREFIX = "public/thumbs/";

    private MediaObjectKeys() {
    }

    /** 좌표가 남은 원본 보관 키. 공개 경로가 아니다 — 버킷 정책에서 비공개로 둬야 한다. */
    public static String original(String publicKey) {
        return publicKey.startsWith(ORIGINAL_PREFIX) ? publicKey : ORIGINAL_PREFIX + publicKey;
    }

    /** 비공개 원본에 대응하는 EXIF 제거 공개 객체 키. */
    public static String publicImage(String originalKey) {
        String value = originalKey.startsWith(ORIGINAL_PREFIX)
                ? originalKey.substring(ORIGINAL_PREFIX.length()) : originalKey;
        return PUBLIC_PREFIX + value;
    }

    public static String thumbnail(String originalKey) {
        String value = originalKey.startsWith(ORIGINAL_PREFIX)
                ? originalKey.substring(ORIGINAL_PREFIX.length()) : originalKey;
        return THUMBNAIL_PREFIX + value;
    }

    public static boolean isDerived(String objectKey) {
        return objectKey != null
                && (objectKey.startsWith(ORIGINAL_PREFIX) || objectKey.startsWith(THUMBNAIL_PREFIX));
    }

    public static boolean isPrivateOriginal(String objectKey) {
        return objectKey != null && objectKey.startsWith(ORIGINAL_PREFIX);
    }

    public static String privateOriginalForPublic(String publicKey) {
        String value=publicKey.startsWith(PUBLIC_PREFIX)
                ? publicKey.substring(PUBLIC_PREFIX.length()) : publicKey;
        return ORIGINAL_PREFIX+value;
    }

    public static String thumbnailForPublic(String publicKey) {
        String value=publicKey.startsWith(PUBLIC_PREFIX)
                ? publicKey.substring(PUBLIC_PREFIX.length()) : publicKey;
        return THUMBNAIL_PREFIX+value;
    }
}

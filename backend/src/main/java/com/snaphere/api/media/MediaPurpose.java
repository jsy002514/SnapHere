package com.snaphere.api.media;

/**
 * 업로드 용도. 용도에 따라 허용 개수와 객체 키 접두어가 다르다.
 *
 * <p>게시글 사진은 1~4장 (PST-001, PST-013), 프로필 이미지는 1장이며
 * 게시글과 같은 Presigned URL 흐름을 쓴다 (USER-004).
 */
public enum MediaPurpose {

    POST_IMAGE("posts", 1, 4),
    PROFILE_IMAGE("profile", 1, 1);

    private final String keyPrefix;
    private final int minCount;
    private final int maxCount;

    MediaPurpose(String keyPrefix, int minCount, int maxCount) {
        this.keyPrefix = keyPrefix;
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public int minCount() {
        return minCount;
    }

    public int maxCount() {
        return maxCount;
    }

    public boolean allows(int count) {
        return count >= minCount && count <= maxCount;
    }
}

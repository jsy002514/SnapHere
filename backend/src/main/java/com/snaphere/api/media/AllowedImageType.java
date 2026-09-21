package com.snaphere.api.media;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 허용하는 이미지 형식. jpeg·png·heic·webp 만 받는다. (PST-015)
 *
 * <p>클라이언트가 보낸 mimeType 을 그대로 믿지 않고 이 목록으로 걸러낸 뒤,
 * 확장자도 서버가 정해서 객체 키를 만든다.
 */
public enum AllowedImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    HEIC("image/heic", "heic"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    AllowedImageType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<AllowedImageType> from(String rawMimeType) {
        if (rawMimeType == null) {
            return Optional.empty();
        }
        String normalized = rawMimeType.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equals(normalized))
                .findFirst();
    }
}

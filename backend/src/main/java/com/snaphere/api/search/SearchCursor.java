package com.snaphere.api.search;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** 타입별 정렬 위치와 검색 조건 지문을 담는 불투명 keyset 커서. */
public record SearchCursor(SearchType type, String fingerprint, int matchRank,
                           String first, String second, String third) {
    private static final String VERSION = "s1";
    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = String.join(SEPARATOR, VERSION, type.name(), fingerprint,
                Integer.toString(matchRank), value(first), value(second), value(third));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static SearchCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 7 || !VERSION.equals(parts[0])) throw new IllegalArgumentException();
            return new SearchCursor(SearchType.valueOf(parts[1]), parts[2],
                    Integer.parseInt(parts[3]), parts[4], parts[5], parts[6]);
        } catch (RuntimeException malformed) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "cursor"));
        }
    }

    public void require(SearchType expectedType, String expectedFingerprint) {
        if (type != expectedType || !fingerprint.equals(expectedFingerprint)) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "cursor"));
        }
    }

    public static String fingerprint(String normalizedQuery, Integer areaCode, boolean regionMode) {
        String raw = normalizedQuery + '\0' + (areaCode == null ? "ALL" : areaCode) + '\0' + regionMode;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}

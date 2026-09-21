package com.snaphere.api.common.web;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorCodec {
    private CursorCodec() { }

    public static String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("v1:" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith("v1:")) throw new IllegalArgumentException();
            return Long.parseLong(decoded.substring(3));
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.COMMON_400);
        }
    }
}

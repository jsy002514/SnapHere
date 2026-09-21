package com.snaphere.api.map;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record MapCellKey(MapPeriod period, int level, int latIndex, int lngIndex) {
    private static final String PREFIX = "hmc_";

    public String encode() {
        String raw = period.name() + "|" + level + "|" + latIndex + "|" + lngIndex;
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static MapCellKey decode(String value) {
        try {
            if (value == null || !value.startsWith(PREFIX)) throw new IllegalArgumentException();
            String raw = new String(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4) throw new IllegalArgumentException();
            MapPeriod period = MapPeriod.valueOf(parts[0]);
            int level = Integer.parseInt(parts[1]);
            int latIndex = Integer.parseInt(parts[2]);
            int lngIndex = Integer.parseInt(parts[3]);
            if (level < 0 || level > 3) throw new IllegalArgumentException();
            return new MapCellKey(period, level, latIndex, lngIndex);
        } catch (RuntimeException ignored) {
            throw new ApiException(ErrorCode.COMMON_400);
        }
    }
}

package com.snaphere.api.map;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

public record MapGrid(int level, int factor) {
    public static MapGrid forZoom(int zoom) {
        if (zoom < 0 || zoom > 22) throw new ApiException(ErrorCode.MAP_INVALID_BOUNDS);
        if (zoom <= 6) return new MapGrid(0, 1);
        if (zoom <= 9) return new MapGrid(1, 10);
        if (zoom <= 13) return new MapGrid(2, 100);
        return new MapGrid(3, 1000);
    }

    public int latIndex(double lat) { return (int) Math.floor(lat * factor); }
    public int lngIndex(double lng) { return (int) Math.floor(lng * factor); }
    public double centerLat(int index) { return (index + 0.5d) / factor; }
    public double centerLng(int index) { return (index + 0.5d) / factor; }
}

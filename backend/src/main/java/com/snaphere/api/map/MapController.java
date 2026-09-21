package com.snaphere.api.map;

import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {
    private final MapService maps;
    private final CurrentUserProvider users;

    public MapController(MapService maps, CurrentUserProvider users) {
        this.maps = maps;
        this.users = users;
    }

    @GetMapping("/regions")
    ApiResponse<List<MapDtos.MapRegion>> regions(@RequestParam(defaultValue = "WEEKLY") String period,
                                                  HttpServletRequest request) {
        return ok(maps.regions(period, users.optional(request).map(user -> user.userId())), request);
    }

    @GetMapping("/heatmap")
    ApiResponse<MapDtos.HeatmapResult> heatmap(@RequestParam double west, @RequestParam double south,
                                                @RequestParam double east, @RequestParam double north,
                                                @RequestParam int zoom,
                                                @RequestParam(defaultValue = "WEEKLY") String period,
                                                @RequestParam(defaultValue = "false") boolean forceRefresh,
                                                HttpServletRequest request) {
        return ok(maps.heatmap(west, south, east, north, zoom, period, forceRefresh), request);
    }

    @GetMapping("/photo-markers")
    ApiResponse<List<MapDtos.PhotoMarker>> markers(@RequestParam double west, @RequestParam double south,
                                                    @RequestParam double east, @RequestParam double north,
                                                    @RequestParam int zoom,
                                                    @RequestParam(defaultValue = "WEEKLY") String period,
                                                    HttpServletRequest request) {
        return ok(maps.photoMarkers(west, south, east, north, zoom, period,
                users.optional(request).map(user -> user.userId())), request);
    }

    @GetMapping("/cells/{cellKey}")
    ApiResponse<MapDtos.HeatmapCellDetail> cell(@PathVariable String cellKey, HttpServletRequest request) {
        return ok(maps.cellDetail(cellKey, users.optional(request).map(user -> user.userId())), request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.ok(data, TraceIdFilter.currentTraceId(request));
    }
}

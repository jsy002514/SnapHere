package com.snaphere.api.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class MapCache {
    private static final Logger log = LoggerFactory.getLogger(MapCache.class);
    private static final String VERSION_KEY = "map:response:version";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public MapCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<MapDtos.HeatmapResult> get(String queryKey) {
        try {
            String value = redis.opsForValue().get(key(queryKey));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, MapDtos.HeatmapResult.class));
        } catch (Exception failure) {
            log.debug("지도 캐시 조회 실패. DB 결과를 사용한다.", failure);
            return Optional.empty();
        }
    }

    public void put(String queryKey, MapDtos.HeatmapResult result) {
        try {
            redis.opsForValue().set(key(queryKey), json.writeValueAsString(result), Duration.ofSeconds(60));
        } catch (Exception failure) {
            log.debug("지도 캐시 저장 실패. DB 결과는 정상 반환한다.", failure);
        }
    }

    public void invalidate() {
        try { redis.opsForValue().increment(VERSION_KEY); }
        catch (RuntimeException failure) { log.debug("지도 캐시 버전 증가 실패", failure); }
    }

    private String key(String queryKey) {
        String version = "0";
        try {
            String stored = redis.opsForValue().get(VERSION_KEY);
            if (stored != null) version = stored;
        } catch (RuntimeException failure) {
            log.debug("지도 캐시 버전 조회 실패", failure);
        }
        return "map:response:" + version + ":" + queryKey;
    }
}

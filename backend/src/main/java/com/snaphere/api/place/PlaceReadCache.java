package com.snaphere.api.place;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** 정적 장소 읽기 캐시. 장애가 나면 호출자가 DB를 사용한다. (SYS-019) */
@Component
public class PlaceReadCache {
    private static final Logger log = LoggerFactory.getLogger(PlaceReadCache.class);
    private static final Duration MASTER_TTL = Duration.ofHours(24);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<PlaceDtos.Region>> REGIONS = new TypeReference<>() { };
    private static final TypeReference<List<PlaceDtos.Sigungu>> SIGUNGU = new TypeReference<>() { };
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public PlaceReadCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<List<PlaceDtos.Region>> regions() { return read("place:regions", REGIONS); }
    public void putRegions(List<PlaceDtos.Region> value) { write("place:regions", value, MASTER_TTL); }
    public Optional<List<PlaceDtos.Sigungu>> sigungu(int areaCode) {
        return read("place:sigungu:" + areaCode, SIGUNGU);
    }
    public void putSigungu(int areaCode, List<PlaceDtos.Sigungu> value) {
        write("place:sigungu:" + areaCode, value, MASTER_TTL);
    }
    public Optional<DetailContent> detail(long placeId, String language) {
        return read("place:detail:" + placeId + ':' + language, new TypeReference<>() { });
    }
    public void putDetail(long placeId, String language, DetailContent value) {
        write("place:detail:" + placeId + ':' + language, value, DETAIL_TTL);
    }
    public void evictRegions() { delete("place:regions"); }
    public void evictSigungu(int areaCode) { delete("place:sigungu:" + areaCode); }
    public void evictDetail(long placeId, String language) { delete("place:detail:" + placeId + ':' + language); }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value,type));
        } catch (Exception failure) {
            log.debug("장소 캐시 조회 실패. DB에서 조회한다. key={}",key,failure);
            return Optional.empty();
        }
    }
    private void write(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key,json.writeValueAsString(value),ttl); }
        catch (Exception failure) { log.debug("장소 캐시 저장 실패. key={}",key,failure); }
    }
    private void delete(String key) {
        try { redis.delete(key); }
        catch (RuntimeException failure) { log.debug("장소 캐시 삭제 실패. key={}",key,failure); }
    }

    public record DetailContent(String overview, String tel, String homepage) { }
}

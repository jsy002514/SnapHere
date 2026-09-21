package com.snaphere.api.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class PopularSearchCache {
    private static final Logger log = LoggerFactory.getLogger(PopularSearchCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<SearchDtos.PopularKeyword>> TYPE = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public PopularSearchCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<List<SearchDtos.PopularKeyword>> get(Integer areaCode, int limit) {
        try {
            String value = redis.opsForValue().get(key(areaCode, limit));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, TYPE));
        } catch (Exception failure) {
            log.debug("인기 검색어 캐시 조회 실패. DB에서 조회한다.", failure);
            return Optional.empty();
        }
    }

    public void put(Integer areaCode, int limit, List<SearchDtos.PopularKeyword> value) {
        try {
            redis.opsForValue().set(key(areaCode, limit), json.writeValueAsString(value), TTL);
        } catch (Exception failure) {
            log.debug("인기 검색어 캐시 저장 실패", failure);
        }
    }

    private static String key(Integer areaCode, int limit) {
        return "search:popular:" + (areaCode == null ? "all" : areaCode) + ':' + limit;
    }
}

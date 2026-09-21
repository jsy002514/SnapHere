package com.snaphere.api.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SCH-011 사용자별 최근 검색어. Redis 장애는 핵심 검색 응답을 막지 않는다. */
@Component
public class RecentSearchStore {
    private static final Logger log = LoggerFactory.getLogger(RecentSearchStore.class);
    private static final String ORDER_PREFIX = "search:recent:order:";
    private static final String META_PREFIX = "search:recent:meta:";
    private static final int MAX_KEEP = 20;
    private static final Duration TTL = Duration.ofDays(30);

    private static final DefaultRedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>("""
            local score = tonumber(ARGV[1])
            local newest = redis.call('ZRANGE', KEYS[1], -1, -1, 'WITHSCORES')
            if #newest > 0 and score <= tonumber(newest[2]) then
              score = tonumber(newest[2]) + 1
            end
            redis.call('ZADD', KEYS[1], score, ARGV[2])
            redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
            local extra = redis.call('ZCARD', KEYS[1]) - tonumber(ARGV[4])
            if extra > 0 then
              local old = redis.call('ZRANGE', KEYS[1], 0, extra - 1)
              if #old > 0 then
                redis.call('ZREMRANGEBYRANK', KEYS[1], 0, extra - 1)
                redis.call('HDEL', KEYS[2], unpack(old))
              end
            end
            redis.call('EXPIRE', KEYS[1], ARGV[5])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RecentSearchStore(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public void record(UUID userId, String normalizedKeyword, String displayKeyword) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Metadata metadata = new Metadata(UUID.randomUUID(), displayKeyword, now);
        try {
            redis.execute(RECORD_SCRIPT, List.of(orderKey(userId), metaKey(userId)),
                    Long.toString(now.toEpochSecond() * 1_000_000L + now.getNano() / 1_000L), normalizedKeyword,
                    json.writeValueAsString(metadata), Integer.toString(MAX_KEEP),
                    Long.toString(TTL.toSeconds()));
        } catch (Exception failure) {
            log.debug("최근 검색어 기록 실패 userId={}", userId, failure);
        }
    }

    public List<SearchDtos.RecentSearch> recent(UUID userId) {
        try {
            var normalized = redis.opsForZSet().reverseRange(orderKey(userId), 0, MAX_KEEP - 1L);
            if (normalized == null || normalized.isEmpty()) return List.of();
            List<Object> keys = new ArrayList<>();
            keys.addAll(normalized);
            List<Object> values = redis.opsForHash().multiGet(metaKey(userId), keys);
            if (values == null) return List.of();
            List<SearchDtos.RecentSearch> result = new ArrayList<>();
            for (Object value : values) {
                if (value == null) continue;
                Metadata item = json.readValue(value.toString(), Metadata.class);
                result.add(new SearchDtos.RecentSearch(
                        item.searchLogId(), item.keyword(), item.searchedAt()));
            }
            return List.copyOf(result);
        } catch (Exception failure) {
            log.debug("최근 검색어 조회 실패 userId={}", userId, failure);
            return List.of();
        }
    }

    public void remove(UUID userId, String normalizedKeyword) {
        try {
            redis.opsForZSet().remove(orderKey(userId), normalizedKeyword);
            redis.opsForHash().delete(metaKey(userId), normalizedKeyword);
        } catch (RuntimeException failure) {
            log.debug("최근 검색어 삭제 실패 userId={}", userId, failure);
        }
    }

    public void clear(UUID userId) {
        try {
            redis.delete(List.of(orderKey(userId), metaKey(userId)));
        } catch (RuntimeException failure) {
            log.debug("최근 검색어 전체 삭제 실패 userId={}", userId, failure);
        }
    }

    private static String orderKey(UUID userId) {
        return ORDER_PREFIX + userId;
    }

    private static String metaKey(UUID userId) {
        return META_PREFIX + userId;
    }

    private record Metadata(UUID searchLogId, String keyword, OffsetDateTime searchedAt) { }
}

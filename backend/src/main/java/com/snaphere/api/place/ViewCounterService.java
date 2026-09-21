package com.snaphere.api.place;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.snaphere.api.config.PlaceTaskConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.List;

@Service
public class ViewCounterService {
    private static final Logger log = LoggerFactory.getLogger(ViewCounterService.class);
    private static final String PREFIX = "place:view:";

    /** SCAN 한 번에 받아 올 키 수. 크면 왕복이 줄고 작으면 한 번에 막는 시간이 짧아진다. */
    private static final int SCAN_BATCH = 200;
    private final StringRedisTemplate redis;
    private final PlaceRepository places;

    public ViewCounterService(StringRedisTemplate redis, PlaceRepository places) {
        this.redis = redis;
        this.places = places;
    }

    @Async(PlaceTaskConfig.PLACE_TASK_EXECUTOR)
    public void increment(long placeId) {
        try {
            redis.opsForValue().increment(PREFIX + placeId);
        } catch (RuntimeException e) {
            try {
                places.addViewCount(placeId, 1);
            } catch (RuntimeException fallbackError) {
                e.addSuppressed(fallbackError);
                log.warn("장소 조회수 Redis·DB 증가 실패 placeId={}", placeId, e);
            }
        }
    }

    public long pending(long placeId) {
        try {
            String value = redis.opsForValue().get(PREFIX + placeId);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Scheduled(cron = "${snaphere.jobs.view-flush-cron:0 * * * * *}", zone = "Asia/Seoul")
    public void flush() {
        // KEYS 가 아니라 SCAN 으로 훑는다.
        //
        // KEYS 는 키 공간 전체를 한 번에 훑는 O(N) 명령이고, 그동안 Redis 싱글스레드가
        // 막힌다. 이 잡은 1분마다 돌고 place:view:* 는 조회된 장소마다 하나씩 생기므로,
        // 장소가 늘면 매분 한 번씩 지도 캐시·최근 본 장소 조회까지 같이 멈춘다.
        // SCAN 은 커서를 나눠 돌려주므로 한 번에 막는 시간이 COUNT 만큼으로 제한된다.
        //
        // 커서를 닫고 나서 DB 에 반영한다. 커서를 열어 둔 채 addViewCount 를 돌리면
        // DB 왕복만큼 커넥션을 붙잡고 있게 된다.
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor =
                     redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(SCAN_BATCH).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (RuntimeException e) {
            log.warn("조회수 키 조회 실패", e);
            return;
        }
        for (String key : keys) {
            Long delta = null;
            try {
                String value = redis.opsForValue().getAndDelete(key);
                if (value == null) continue;
                delta = Long.parseLong(value);
                long placeId = Long.parseLong(key.substring(PREFIX.length()));
                places.addViewCount(placeId, delta);
            } catch (RuntimeException e) {
                if (delta != null) {
                    try { redis.opsForValue().increment(key, delta); } catch (RuntimeException ignored) { }
                }
                log.error("조회수 DB 반영 실패 key={}", key, e);
            }
        }
    }
}

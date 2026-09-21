package com.snaphere.api.post.view;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로세스 메모리에 (게시글, 사용자) 조회 시각을 담아 24시간 중복을 막는다. (PST-042)
 *
 * <p><b>단일 인스턴스에서만 정확하다.</b> 서버를 여러 대로 늘리면 인스턴스마다 따로 세어
 * 최대 대수만큼 중복 집계된다. 재시작하면 기록이 사라져 그 직후 재조회도 한 번 더 센다.
 *
 * <p>`PST-042` 는 Could Have 라서 Redis 를 먼저 붙이지 않았다. 조회 캐시(`SYS-019`)로 Redis 가
 * 들어올 때 이 구현을 그것으로 바꾼다 — 그때 이 파일을 지운다.
 *
 * <p>맵이 무한히 커지지 않도록 쓰기 때마다 만료된 항목을 조금씩 걷어 낸다. 조회는 쓰기보다
 * 훨씬 잦으므로 전체를 훑는 청소를 매번 돌리지는 않는다.
 */
@Component
public class InMemoryPostViewCounter implements PostViewCounter {

    /** 같은 사용자의 재조회를 무시하는 기간. (PST-042) */
    static final Duration WINDOW = Duration.ofHours(24);

    /** 한 번에 걷어 낼 만료 항목 수. 청소가 조회 지연을 만들지 않게 제한한다. */
    private static final int SWEEP_LIMIT = 200;

    private final Map<String, Instant> lastViewed = new ConcurrentHashMap<>();

    @Override
    public boolean countIfFirstToday(long postId, Optional<UUID> viewerId) {
        if (viewerId.isEmpty()) {
            return false;
        }
        Instant now = Instant.now();
        String key = postId + ":" + viewerId.get();
        Instant previous = lastViewed.get(key);
        if (previous != null && previous.isAfter(now.minus(WINDOW))) {
            return false;
        }
        lastViewed.put(key, now);
        sweepExpired(now);
        return true;
    }

    private void sweepExpired(Instant now) {
        Instant threshold = now.minus(WINDOW);
        int removed = 0;
        for (Map.Entry<String, Instant> entry : lastViewed.entrySet()) {
            if (removed >= SWEEP_LIMIT) {
                return;
            }
            if (entry.getValue().isBefore(threshold)) {
                lastViewed.remove(entry.getKey(), entry.getValue());
                removed++;
            }
        }
    }

    /** 테스트에서 상태를 확인할 때만 쓴다. */
    int trackedCount() {
        return lastViewed.size();
    }
}

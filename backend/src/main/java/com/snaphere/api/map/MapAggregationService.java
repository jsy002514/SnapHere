package com.snaphere.api.map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
public class MapAggregationService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MapRepository maps;
    private final MapCache cache;

    public MapAggregationService(MapRepository maps, MapCache cache) {
        this.maps = maps;
        this.cache = cache;
    }

    @Transactional
    public int rebuild(MapPeriod period) {
        maps.lockAggregation();
        OffsetDateTime now = OffsetDateTime.now(KST);
        int changed = maps.rebuildRegions(period, period.from(now), now, null);
        for (int zoom : new int[]{0, 7, 10, 14}) {
            changed += maps.rebuildLevel(period, MapGrid.forZoom(zoom), period.from(now), now);
        }
        invalidateAfterCommit();
        return changed;
    }

    @Transactional
    public int refreshPost(long postId) {
        MapRepository.PostLocation post = maps.postLocation(postId).orElse(null);
        if (post == null || !post.eligible()) return 0;
        maps.lockAggregation();
        OffsetDateTime now = OffsetDateTime.now(KST);
        int changed = 0;
        for (MapPeriod period : MapPeriod.values()) {
            OffsetDateTime from = period.from(now);
            if (post.createdAt().isBefore(from)) continue;
            changed += maps.rebuildRegions(period, from, now, post.areaCode());
            for (int zoom : new int[]{0, 7, 10, 14}) {
                MapGrid grid = MapGrid.forZoom(zoom);
                changed += maps.rebuildCell(period, grid, grid.latIndex(post.lat()), grid.lngIndex(post.lng()), from, now);
            }
        }
        invalidateAfterCommit();
        return changed;
    }

    private void invalidateAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.invalidate(); }
        });
    }
}

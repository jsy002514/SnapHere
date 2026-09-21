package com.snaphere.api.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "snaphere.jobs", name = "enabled", matchIfMissing = true)
public class MapAggregationJobs {
    private static final Logger log = LoggerFactory.getLogger(MapAggregationJobs.class);
    private final MapAggregationService aggregation;

    public MapAggregationJobs(MapAggregationService aggregation) { this.aggregation = aggregation; }

    @Scheduled(fixedDelayString = "${snaphere.jobs.heatmap-realtime-delay:PT1M}", initialDelayString = "PT20S")
    public void realtime() { run(MapPeriod.LAST_1H); }

    @Scheduled(fixedDelayString = "${snaphere.jobs.heatmap-periodic-delay:PT10M}", initialDelayString = "PT40S")
    public void periodic() {
        run(MapPeriod.LAST_24H);
        run(MapPeriod.WEEKLY);
        run(MapPeriod.MONTHLY);
    }

    private void run(MapPeriod period) {
        try { log.info("지도 집계 완료. period={} rows={}", period, aggregation.rebuild(period)); }
        catch (RuntimeException failure) { log.error("지도 집계 실패. period={}", period, failure); }
    }
}

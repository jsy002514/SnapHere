package com.snaphere.api.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(prefix = "snaphere.jobs", name = "enabled", matchIfMissing = true)
public class SearchLogPurgeJob {
    private final SearchRepository searches;

    public SearchLogPurgeJob(SearchRepository searches) {
        this.searches = searches;
    }

    @Scheduled(cron = "${snaphere.jobs.search-log-purge-cron:0 20 5 * * *}", zone = "Asia/Seoul")
    public void purge() {
        searches.purgeBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
    }
}

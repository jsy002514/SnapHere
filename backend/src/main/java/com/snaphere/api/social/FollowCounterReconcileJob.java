package com.snaphere.api.social;

import com.snaphere.api.admin.CounterReconcileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 모든 비정규화 카운터를 매일 함께 보정한다. */
@Component
public class FollowCounterReconcileJob {
    private final CounterReconcileService counters;
    public FollowCounterReconcileJob(CounterReconcileService counters) { this.counters = counters; }
    @Scheduled(cron="${snaphere.jobs.counter-reconcile-cron:0 10 5 * * *}",zone="Asia/Seoul")
    public void reconcile() { counters.reconcile(); }
}

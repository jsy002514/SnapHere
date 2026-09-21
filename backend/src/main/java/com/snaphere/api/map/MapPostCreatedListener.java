package com.snaphere.api.map;

import com.snaphere.api.config.MapTaskConfig;
import com.snaphere.api.post.event.PostCreatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MapPostCreatedListener {
    private final MapAggregationService aggregation;

    public MapPostCreatedListener(MapAggregationService aggregation) { this.aggregation = aggregation; }

    @Async(MapTaskConfig.MAP_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        aggregation.refreshPost(event.postId());
    }
}

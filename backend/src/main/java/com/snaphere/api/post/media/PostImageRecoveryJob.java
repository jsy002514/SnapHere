package com.snaphere.api.post.media;

import com.snaphere.api.post.repository.PostImageRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실패하거나 서버 재시작으로 놓친 미디어 처리를 5분마다 복구한다. (JOB-003) */
@Component
public class PostImageRecoveryJob {
    private final PostImageRepository images;
    private final PostImagePostProcessor processor;
    public PostImageRecoveryJob(PostImageRepository images,PostImagePostProcessor processor) {
        this.images=images; this.processor=processor;
    }
    @Scheduled(fixedDelayString="${snaphere.jobs.media-retry-delay:PT5M}")
    public void retry() { images.findUnreadyPostIds().forEach(processor::process); }
}

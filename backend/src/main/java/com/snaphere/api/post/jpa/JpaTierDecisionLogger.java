package com.snaphere.api.post.jpa;

import com.snaphere.api.post.entity.TierLogEntity;
import com.snaphere.api.post.repository.TierLogRepository;
import com.snaphere.api.post.tier.Slf4jTierDecisionLogger;
import com.snaphere.api.post.tier.TierDecision;
import com.snaphere.api.post.tier.TierDecisionLogger;
import com.snaphere.api.post.tier.TierInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 판정 근거를 {@code tier_logs} 에 적재한다. (PST-028)
 *
 * <p><b>업로드 전 미리보기(API-PST-002)는 적재하지 않는다.</b> {@code tier_logs.post_id} 는
 * {@code posts} 를 가리키는 필수 외래키인데 미리보기 시점에는 게시글이 없다. 미리보기를 담으려면
 * 그 열을 nullable 로 풀어야 하고, 그러면 이 테이블이 "게시글의 판정 이력" 이 아니라 잡다한
 * 시도 로그가 된다 — 등급 안내 화면(PST-047)이 최신 1행을 읽는 전제가 깨진다.
 * 미리보기는 {@link Slf4jTierDecisionLogger} 로 애플리케이션 로그에만 남긴다.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "snaphere", name = "stub-data", havingValue = "false", matchIfMissing = true)
public class JpaTierDecisionLogger implements TierDecisionLogger {

    private final TierLogRepository tierLogs;
    private final Slf4jTierDecisionLogger fallback;

    public JpaTierDecisionLogger(TierLogRepository tierLogs, Slf4jTierDecisionLogger fallback) {
        this.tierLogs = tierLogs;
        this.fallback = fallback;
    }

    @Override
    @Transactional
    public void record(Long postId, UUID userId, long placeId, Long eventId,
                       TierInput input, TierDecision decision) {
        if (postId == null) {
            fallback.record(null, userId, placeId, eventId, input, decision);
            return;
        }
        tierLogs.save(TierLogEntity.from(postId, input, decision));
    }
}

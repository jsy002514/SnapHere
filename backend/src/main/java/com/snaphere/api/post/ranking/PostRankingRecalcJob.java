package com.snaphere.api.post.ranking;

import com.snaphere.api.post.PostFeedPeriod;
import com.snaphere.api.post.repository.PostRankingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * JOB-013 POST_RANKING_RECALC — 기간별 게시글 인기 점수·순위 집계.
 *
 * <p>요구사항: PST-035, CMU-002, CMU-008
 *
 * <p>10분마다 네 기간을 각각 전체 재계산한다. 부분 갱신을 하지 않는 이유는 순위가 상대값이라
 * 한 게시글의 점수가 바뀌면 그 아래 전부가 밀리기 때문이다.
 *
 * <p>기간마다 트랜잭션을 따로 둔다. {@code ALL} 계산이 실패해도 {@code HOURS_24} 결과는 남아야
 * 한다 — 인기 목록이 전부 비는 것보다 한 탭만 오래된 편이 낫다.
 *
 * <p>{@code snaphere.jobs.enabled=false} 로 끌 수 있다. 테스트와 로컬 개발에서 배치가 도는 것을
 * 막고, 여러 인스턴스를 띄울 때 한 대만 켜 두는 데도 쓴다 — 지금은 분산 락이 없어서 여러 대가
 * 동시에 돌면 같은 기간을 서로 지우고 넣는다.
 */
@Component
@ConditionalOnProperty(prefix = "snaphere.jobs", name = "enabled", matchIfMissing = true)
public class PostRankingRecalcJob {

    private static final Logger log = LoggerFactory.getLogger(PostRankingRecalcJob.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // SYS-005

    /** {@code ALL} 기간의 시작. 서비스 시작보다 앞서면 어떤 값이든 결과가 같다. */
    private static final OffsetDateTime EPOCH =
            OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    private final PostRankingRepository rankings;

    public PostRankingRecalcJob(PostRankingRepository rankings) {
        this.rankings = rankings;
    }

    /** 명세의 10분 주기. 이전 실행이 끝난 뒤부터 세므로 겹쳐 돌지 않는다. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void recalcAll() {
        for (PostFeedPeriod period : PostFeedPeriod.values()) {
            try {
                int inserted = recalc(period);
                log.info("게시글 인기 집계 완료. period={} rows={}", period, inserted);
            } catch (RuntimeException failure) {
                log.error("게시글 인기 집계 실패. 이 기간만 건너뛴다. period={}", period, failure);
            }
        }
    }

    /** 수동 실행용. 운영자가 배치를 당겨 돌릴 수 있어야 한다 (SYS-015). */
    @Transactional
    public int recalc(PostFeedPeriod period) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        OffsetDateTime createdFrom = period.from(now);

        rankings.deleteByPeriod(period);
        return rankings.insertRanking(period.name(),
                createdFrom == null ? EPOCH : createdFrom, now);
    }
}

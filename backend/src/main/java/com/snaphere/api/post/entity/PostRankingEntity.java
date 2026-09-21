package com.snaphere.api.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 기간별 게시글 인기 점수·순위. (PST-035, CMU-008)
 *
 * <p>배치({@code JOB-013})가 10분마다 기간 단위로 전체를 다시 채운다. 조회는 읽기만 한다 —
 * 요청마다 좋아요·댓글·조회수를 집계하면 무한 스크롤이 버티지 못한다.
 */
@Entity
@Table(name = "post_rankings")
public class PostRankingEntity {

    @EmbeddedId
    private PostRankingId id;

    @Column(name = "score", nullable = false, precision = 18, scale = 4)
    private BigDecimal score;

    /** 1부터. 기간 안에서 유일하다. */
    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    protected PostRankingEntity() {
    }

    public PostRankingId getId() {
        return id;
    }

    public BigDecimal getScore() {
        return score;
    }

    public int getRankNo() {
        return rankNo;
    }

    public OffsetDateTime getCalculatedAt() {
        return calculatedAt;
    }
}

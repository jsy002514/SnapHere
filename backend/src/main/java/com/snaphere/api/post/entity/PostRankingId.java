package com.snaphere.api.post.entity;

import com.snaphere.api.post.PostFeedPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

/** {@code post_rankings} 복합 키. 한 게시글이 기간마다 하나의 순위를 갖는다. */
@Embeddable
public class PostRankingId implements Serializable {

    @Column(name = "post_id")
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 20)
    private PostFeedPeriod period;

    protected PostRankingId() {
    }

    public PostRankingId(Long postId, PostFeedPeriod period) {
        this.postId = postId;
        this.period = period;
    }

    public Long getPostId() {
        return postId;
    }

    public PostFeedPeriod getPeriod() {
        return period;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostRankingId other)) {
            return false;
        }
        return Objects.equals(postId, other.postId) && period == other.period;
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, period);
    }
}

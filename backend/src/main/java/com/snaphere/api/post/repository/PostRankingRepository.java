package com.snaphere.api.post.repository;

import com.snaphere.api.post.PostFeedPeriod;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.PostRankingEntity;
import com.snaphere.api.post.entity.PostRankingId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 게시글 인기 집계 조회·재계산. (PST-035, JOB-013) */
public interface PostRankingRepository extends JpaRepository<PostRankingEntity, PostRankingId> {

    /**
     * 인기 목록. 순위 순으로 읽고 점수는 다시 계산하지 않는다. (PST-035)
     *
     * <p>{@code areaCode} 는 {@code post_rankings} 에 없어 {@code posts} 를 함께 본다. 지역으로
     * 걸러도 순위는 전체 기준이라 번호가 촘촘하지 않지만, 보여줄 순서는 그대로 유지된다.
     */
    @Query("""
            select p from PostRankingEntity r, PostEntity p
             where r.id.postId = p.postId
               and r.id.period = :period
               and p.status = com.snaphere.api.post.PostStatus.ACTIVE
               and (:areaCode is null or p.areaCode = :areaCode)
               and (:cursorRankNo is null or r.rankNo > :cursorRankNo)
             order by r.rankNo asc
            """)
    List<PostEntity> findPopular(@Param("period") PostFeedPeriod period,
                                 @Param("areaCode") Integer areaCode,
                                 @Param("cursorRankNo") Integer cursorRankNo,
                                 Pageable pageable);

    /** 커서를 만들 때 쓴다. 목록의 마지막 게시글이 몇 위인지 알아야 다음 페이지를 이어 붙인다. */
    @Query("select r.rankNo from PostRankingEntity r "
            + "where r.id.period = :period and r.id.postId = :postId")
    Integer findRankNo(@Param("period") PostFeedPeriod period, @Param("postId") Long postId);

    /**
     * 기간 단위 전체 삭제. 부분 갱신을 하지 않는 이유는 {@code (period, rank_no)} UNIQUE 때문이다 —
     * 순위를 하나씩 올리는 도중에 두 행이 같은 번호를 갖는 순간이 생긴다.
     */
    @Modifying
    @Query("delete from PostRankingEntity r where r.id.period = :period")
    int deleteByPeriod(@Param("period") PostFeedPeriod period);

    /**
     * 점수·순위 재계산 후 삽입. (JOB-013, CMU-008)
     *
     * <p>가중치는 랭킹 점수 규약을 따른다 — 좋아요 1.0 · 댓글 1.5 · 조회 0.05 에 등급 가중치
     * (높음 3.0 · 보통 1.8 · 낮음 0.5)를 곱한다 (RNK-001).
     *
     * <p><b>자기 게시글에 누른 좋아요는 뺀다 (PST-041).</b> 표시되는 {@code like_count} 에서는
     * 빼지 않는다 — 화면의 숫자는 실제 좋아요 수여야 하고, 요구사항이 제외하라는 것은 점수와
     * 순위다. 그래서 여기서만 차감한다.
     *
     * <p>동점은 {@code post_id} 오름차순으로 가른다. 결정적 보조 정렬이 없으면 같은 데이터로
     * 배치를 두 번 돌릴 때 순위가 흔들린다 (JOB-013).
     *
     * <p>애플리케이션으로 끌어와 정렬하지 않는다. 게시글 수만큼 메모리에 올려야 하고,
     * 10분마다 그러기에는 규모가 커진다.
     */
    @Modifying
    @Query(value = """
            insert into post_rankings (post_id, period, score, rank_no, calculated_at)
            select s.post_id, :period, s.score,
                   row_number() over (order by s.score desc, s.post_id asc),
                   :calculatedAt
              from (select p.post_id as post_id,
                           ((p.like_count - coalesce(sl.self_like_count, 0)) * 1.0
                            + p.comment_count * 1.5
                            + p.view_count * 0.05)
                           * (case p.tier when 'HIGH' then 3.0
                                          when 'MEDIUM' then 1.8
                                          else 0.5 end) as score
                      from posts p
                      left join (select l.target_id as post_id,
                                        count(*) as self_like_count
                                   from likes l
                                   join posts owner on owner.post_id = l.target_id
                                  where l.target_type = 'POST'
                                    and l.user_id = owner.user_id
                                  group by l.target_id) sl
                             on sl.post_id = p.post_id
                     where p.status = 'ACTIVE'
                       and p.created_at >= :createdFrom) s
            """, nativeQuery = true)
    int insertRanking(@Param("period") String period,
                      @Param("createdFrom") OffsetDateTime createdFrom,
                      @Param("calculatedAt") OffsetDateTime calculatedAt);
}

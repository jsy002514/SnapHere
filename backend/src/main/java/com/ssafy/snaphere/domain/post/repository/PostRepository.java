package com.ssafy.snaphere.domain.post.repository;

import com.ssafy.snaphere.domain.post.entity.Post;
import com.ssafy.snaphere.domain.post.entity.PostStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    Page<Post> findByAreaCodeAndStatus(Integer areaCode, PostStatus status, Pageable pageable);

    Page<Post> findByPlaceIdAndStatus(Long placeId, PostStatus status, Pageable pageable);

    Page<Post> findByUserIdAndStatus(Long userId, PostStatus status, Pageable pageable);

    Page<Post> findByIdInAndStatus(List<Long> ids, PostStatus status, Pageable pageable);

    /** 일일 업로드 한도 (POST_003) */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.userId = :userId AND p.createdAt >= :from AND p.status <> com.ssafy.snaphere.domain.post.entity.PostStatus.DELETED")
    long countByUserSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    /** 동일 장소 하루 한도 (POST_004) */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.userId = :userId AND p.placeId = :placeId AND p.createdAt >= :from AND p.status <> com.ssafy.snaphere.domain.post.entity.PostStatus.DELETED")
    long countByUserAndPlaceSince(@Param("userId") Long userId, @Param("placeId") Long placeId,
                                  @Param("from") LocalDateTime from);

    /** 장문 컬럼은 엔티티에 매핑하지 않는다. 상세에서만 따로 읽는다. */
    @Query(value = "SELECT content FROM posts WHERE post_id = :postId", nativeQuery = true)
    Optional<String> findContent(@Param("postId") Long postId);

    /**
     * 기간 + 미디어 유무로 좁힌 인기 게시물. "이번주 인기 사진" 탭이 쓴다.
     * idx_posts_area_period 를 태우기 위해 area_code → status → media_count → created_at 순서로 조건을 건다.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status = com.ssafy.snaphere.domain.post.entity.PostStatus.ACTIVE
              AND (:areaCode IS NULL OR p.areaCode = :areaCode)
              AND (:hasMedia = false OR p.mediaCount > 0)
              AND (:from IS NULL OR p.createdAt >= :from)
            ORDER BY p.popularityScore DESC, p.id DESC
            """)
    Page<Post> findPopular(@Param("areaCode") Integer areaCode,
                           @Param("hasMedia") boolean hasMedia,
                           @Param("from") LocalDateTime from,
                           Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.status = com.ssafy.snaphere.domain.post.entity.PostStatus.ACTIVE
              AND (:areaCode IS NULL OR p.areaCode = :areaCode)
              AND (:placeId IS NULL OR p.placeId = :placeId)
              AND (:category IS NULL OR p.category = :category)
              AND (:hasMedia = false OR p.mediaCount > 0)
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Page<Post> findLatest(@Param("areaCode") Integer areaCode,
                          @Param("placeId") Long placeId,
                          @Param("category") com.ssafy.snaphere.domain.post.entity.PostCategory category,
                          @Param("hasMedia") boolean hasMedia,
                          Pageable pageable);

    /**
     * 팔로잉 피드. 팔로우한 사람의 글에 가중치를 주는 방식이 아니라 별도 목록으로 뽑는다.
     * 첫 페이지에만 개인화를 적용하는 전략은 PostService 쪽에 있다(캐시 보존).
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status = com.ssafy.snaphere.domain.post.entity.PostStatus.ACTIVE
              AND p.userId IN (SELECT f.followingId FROM Follow f WHERE f.followerId = :userId)
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Page<Post> findFollowingFeed(@Param("userId") Long userId, Pageable pageable);

    /** ngram FULLTEXT 로 본문 검색. TourAPI 를 런타임 호출하지 않는 것과 같은 이유로 우리 DB 로 처리한다. */
    @Query(value = """
            SELECT p.post_id FROM posts p
            WHERE p.status = 'ACTIVE'
              AND MATCH(p.title, p.content) AGAINST (:keyword IN BOOLEAN MODE)
              AND (:areaCode IS NULL OR p.area_code = :areaCode)
            ORDER BY p.popularity_score DESC, p.post_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchIds(@Param("keyword") String keyword,
                         @Param("areaCode") Integer areaCode,
                         @Param("limit") int limit);

    /** 장소별 Tier 분포 — 장소 상세의 tierBreakdown */
    @Query(value = """
            SELECT p.tier AS tier, COUNT(*) AS cnt
            FROM posts p WHERE p.place_id = :placeId AND p.status = 'ACTIVE'
            GROUP BY p.tier
            """, nativeQuery = true)
    List<TierCountRow> countByTier(@Param("placeId") Long placeId);

    interface TierCountRow {
        String getTier();
        Long getCnt();
    }
}

package com.snaphere.api.comment.repository;

import com.snaphere.api.comment.CommentStatus;
import com.snaphere.api.comment.entity.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 댓글 조회. (CMU-013, CMU-014, CMU-016, CMU-017) */
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    Optional<CommentEntity> findByCommentIdAndStatus(Long commentId, CommentStatus status);

    /**
     * 살아 있는 최상위 댓글 한 페이지. 최신순이다. (CMU-013, CMU-010)
     *
     * <p>커서는 {@code (createdAt, commentId)} 두 키를 함께 본다 — 시각만 쓰면 같은 순간에 달린
     * 댓글이 두 페이지에 나오거나 사라진다.
     *
     * <p><b>커서 null 검사에 cast 를 씌운 이유.</b> {@code :x is null} 은 그 자리에서 타입을 추론할
     * 근거가 없다. Hibernate 가 Integer·Long 은 {@code setNull(idx, INTEGER)} 로 JDBC 타입까지 실어
     * 보내지만 {@code OffsetDateTime} null 은 타입 없이 나가고, PostgreSQL 은 준비 단계에서
     * {@code could not determine data type of parameter $n} 으로 거부한다. 첫 페이지(커서 없음)가
     * 항상 500 이 된다. H2 는 이를 받아 주므로 테스트로는 드러나지 않는다.
     */
    @Query("""
            select c from CommentEntity c
             where c.postId = :postId
               and c.parentId is null
               and c.status = com.snaphere.api.comment.CommentStatus.ACTIVE
               and (cast(:cursorCreatedAt as timestamp) is null
                    or c.createdAt < :cursorCreatedAt
                    or (c.createdAt = :cursorCreatedAt and c.commentId < :cursorCommentId))
             order by c.createdAt desc, c.commentId desc
            """)
    List<CommentEntity> findActiveRoots(@Param("postId") Long postId,
                                        @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                        @Param("cursorCommentId") Long cursorCommentId,
                                        Pageable pageable);

    /**
     * 삭제됐지만 살아 있는 자식이 있는 최상위 댓글. 자리표시자로 목록에 남는다. (CMU-017)
     *
     * <p>활성 댓글과 한 쿼리로 합치지 않는다. {@code status = ACTIVE or exists(...)} 로 묶으면
     * PostgreSQL 이 EXISTS 를 해시 서브플랜으로 바꿔 comments 전체를 한 번 훑는다 — 페이지 크기와
     * 무관하게 전체 행 수에 비례한다(20만 행에서 28ms). 상태별로 나누면 둘 다 인덱스로 끝난다(0.4ms).
     *
     * <p>커서 null 검사의 {@code cast} 는 {@link #findActiveRoots} 의 설명과 같은 이유다.
     */
    @Query("""
            select c from CommentEntity c
             where c.postId = :postId
               and c.parentId is null
               and c.status = com.snaphere.api.comment.CommentStatus.DELETED
               and exists (select r.commentId from CommentEntity r
                            where r.parentId = c.commentId
                              and r.status = com.snaphere.api.comment.CommentStatus.ACTIVE)
               and (cast(:cursorCreatedAt as timestamp) is null
                    or c.createdAt < :cursorCreatedAt
                    or (c.createdAt = :cursorCreatedAt and c.commentId < :cursorCommentId))
             order by c.createdAt desc, c.commentId desc
            """)
    List<CommentEntity> findDeletedRootsWithActiveReplies(@Param("postId") Long postId,
                                                          @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                                          @Param("cursorCommentId") Long cursorCommentId,
                                                          Pageable pageable);

    /**
     * 부모 여러 개의 대댓글을 한 번에 가져온다. (CMU-013)
     *
     * <p>부모마다 따로 조회하면 페이지 크기만큼 쿼리가 나간다 — 명세가 IN 절 일괄 조회를 못 박은
     * 이유다. 대댓글은 오래된 순이다: 대화는 위에서 아래로 읽힌다.
     */
    @Query("""
            select c from CommentEntity c
             where c.parentId in :parentIds
               and c.status = com.snaphere.api.comment.CommentStatus.ACTIVE
             order by c.createdAt asc, c.commentId asc
            """)
    List<CommentEntity> findRepliesOf(@Param("parentIds") Collection<Long> parentIds);

    boolean existsByParentIdAndStatus(Long parentId, CommentStatus status);

    @Modifying
    @Query("update CommentEntity c set c.likeCount = c.likeCount + :delta where c.commentId = :commentId")
    int addLikeCount(@Param("commentId") Long commentId, @Param("delta") int delta);
}

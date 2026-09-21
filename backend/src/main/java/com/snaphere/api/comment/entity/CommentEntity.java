package com.snaphere.api.comment.entity;

import com.snaphere.api.comment.CommentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 댓글. (CMU-012, CMU-014, CMU-015)
 *
 * <p>{@code parentId} 는 언제나 <b>최상위 댓글</b>을 가리킨다. 대댓글에 답글을 달면 그 대댓글이
 * 아니라 그 대댓글의 부모를 가리키게 정규화한다 — 깊이가 1단계로 고정되므로 목록 조회가
 * "부모 페이징 + 자식 일괄 조회" 두 번으로 끝난다 (CMU-013, CMU-015).
 */
@Entity
@Table(name = "comments")
public class CommentEntity {

    public static final int MIN_CONTENT_LENGTH = 1;
    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** null 이면 이 행이 최상위 댓글이다. */
    @Column(name = "parent_id")
    private Long parentId;

    /** 삭제하면 null 이 된다 (CMU-017). */
    @Column(name = "content", length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CommentEntity() {
    }

    /**
     * 최상위 댓글을 만든다. (CMU-012)
     *
     * <p>본문 길이 검증은 {@code CommentContent} 가 이미 끝냈다고 본다 — 엔티티가 다시 던지면
     * 같은 규칙이 두 곳에 생긴다.
     */
    public static CommentEntity root(Long postId, UUID userId, String content) {
        return of(postId, userId, null, content);
    }

    /** 대댓글을 만든다. {@code parentId} 는 최상위 댓글이어야 한다. (CMU-014, CMU-015) */
    public static CommentEntity reply(Long postId, UUID userId, Long parentId, String content) {
        return of(postId, userId, parentId, content);
    }

    private static CommentEntity of(Long postId, UUID userId, Long parentId, String content) {
        CommentEntity comment = new CommentEntity();
        comment.postId = postId;
        comment.userId = userId;
        comment.parentId = parentId;
        comment.content = content;
        comment.likeCount = 0;
        comment.status = CommentStatus.ACTIVE;
        comment.createdAt = OffsetDateTime.now();
        comment.updatedAt = comment.createdAt;
        return comment;
    }

    /** 본문을 고친다. (CMU-016) */
    public void changeContent(String newContent) {
        this.content = newContent;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 논리 삭제. (CMU-017)
     *
     * @return 이번 호출로 상태가 바뀌었으면 true. 이미 삭제된 댓글이면 false 다 —
     *         호출자가 이 값을 보고 {@code posts.comment_count} 를 한 번만 줄인다
     */
    public boolean markDeleted() {
        if (status != CommentStatus.ACTIVE) {
            return false;
        }
        this.status = CommentStatus.DELETED;
        this.content = null;
        this.updatedAt = OffsetDateTime.now();
        return true;
    }

    public boolean isOwnedBy(UUID candidate) {
        return candidate != null && candidate.equals(userId);
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public boolean isActive() {
        return status == CommentStatus.ACTIVE;
    }

    /** 이 댓글이 속한 스레드의 최상위 댓글 ID. 자기가 최상위면 자기 ID 다. (CMU-015) */
    public Long threadRootId() {
        return parentId != null ? parentId : commentId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getPostId() {
        return postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getContent() {
        return content;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public CommentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

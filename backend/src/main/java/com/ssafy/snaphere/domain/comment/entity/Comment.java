package com.ssafy.snaphere.domain.comment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글. 대댓글은 1단계까지만 허용한다(COMMENT_002).
 * 무제한 중첩을 허용하면 목록 조회가 재귀가 되고 화면도 무너진다.
 *
 * content 는 VARCHAR(1000) 이라 엔티티에 그대로 매핑한다(장문 TEXT 가 아님).
 */
@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @Column(name = "post_id", nullable = false) private Long postId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "parent_comment_id")         private Long parentCommentId;

    @Column(nullable = false, length = 1000) private String content;

    @Column(name = "like_count", nullable = false)   private int likeCount;
    @Column(name = "report_count", nullable = false) private int reportCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommentStatus status;

    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;

    public static Comment create(Long postId, Long userId, Long parentCommentId, String content) {
        Comment c = new Comment();
        c.postId = postId;
        c.userId = userId;
        c.parentCommentId = parentCommentId;
        c.content = content;
        c.status = CommentStatus.ACTIVE;
        return c;
    }

    public boolean isActive() { return status == CommentStatus.ACTIVE; }
    public boolean isReply()  { return parentCommentId != null; }
    public boolean isOwnedBy(Long uid) { return uid != null && uid.equals(userId); }

    public void updateContent(String newContent) { this.content = newContent; }
    public void softDelete() { this.status = CommentStatus.DELETED; }
    public void blind() { this.status = CommentStatus.BLINDED; }
}

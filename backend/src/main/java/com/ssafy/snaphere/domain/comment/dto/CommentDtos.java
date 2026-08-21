package com.ssafy.snaphere.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class CommentDtos {

    private CommentDtos() {}

    @Schema(name = "CommentCreateRequest")
    public record CreateRequest(
            @NotBlank @Size(min = 1, max = 1000) String content,
            @Schema(description = "대댓글이면 부모 댓글 id. 1단계까지만 허용된다")
            Long parentCommentId
    ) {}

    @Schema(name = "CommentUpdateRequest")
    public record UpdateRequest(@NotBlank @Size(min = 1, max = 1000) String content) {}

    @Schema(name = "CommentAuthor")
    public record Author(Long userId, String nickname, String profileImageUrl, String grade) {}

    @Schema(name = "CommentItem")
    public record Item(
            Long commentId, String content, Author author,
            int likeCount, boolean isLiked, boolean isMine,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            List<Item> replies
    ) {}

    @Schema(name = "CommentLikeResponse")
    public record LikeResponse(Long commentId, boolean liked, int likeCount) {}
}

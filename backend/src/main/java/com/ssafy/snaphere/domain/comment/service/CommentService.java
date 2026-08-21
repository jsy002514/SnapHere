package com.ssafy.snaphere.domain.comment.service;

import com.ssafy.snaphere.domain.comment.dto.CommentDtos.*;
import com.ssafy.snaphere.domain.comment.entity.Comment;
import com.ssafy.snaphere.domain.comment.entity.CommentStatus;
import com.ssafy.snaphere.domain.comment.repository.CommentLikeRepository;
import com.ssafy.snaphere.domain.comment.repository.CommentRepository;
import com.ssafy.snaphere.domain.notification.entity.NotificationTargetType;
import com.ssafy.snaphere.domain.notification.entity.NotificationType;
import com.ssafy.snaphere.domain.notification.service.NotificationService;
import com.ssafy.snaphere.domain.post.entity.Post;
import com.ssafy.snaphere.domain.post.repository.PostRepository;
import com.ssafy.snaphere.domain.post.repository.PostWriteRepository;
import com.ssafy.snaphere.domain.user.entity.User;
import com.ssafy.snaphere.domain.user.repository.UserRepository;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final PostWriteRepository postWriteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public Item create(Long postId, Long userId, CreateRequest req) {
        Post post = postRepository.findById(postId).filter(Post::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_002));

        Comment parent = null;
        if (req.parentCommentId() != null) {
            parent = commentRepository.findById(req.parentCommentId())
                    .filter(Comment::isActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001, "parentCommentId"));
            // 대댓글의 대댓글은 막는다. 허용하면 목록 조회가 재귀가 되고 화면 들여쓰기가 무너진다.
            if (parent.isReply()) throw new BusinessException(ErrorCode.COMMENT_002);
            if (!parent.getPostId().equals(postId)) {
                throw new BusinessException(ErrorCode.COMMON_400, "parentCommentId");
            }
        }

        Comment saved = commentRepository.save(
                Comment.create(postId, userId, req.parentCommentId(), req.content().trim()));
        postWriteRepository.addCommentCount(postId, 1);

        User author = userRepository.findById(userId).orElse(null);
        String nickname = author == null ? "" : author.getNickname();

        // 대댓글이면 부모 댓글 작성자에게, 일반 댓글이면 게시물 작성자에게 알린다.
        if (parent != null) {
            notificationService.notifyAsync(parent.getUserId(), userId,
                    NotificationType.COMMENT_REPLY, NotificationTargetType.COMMENT, saved.getId(),
                    Map.of("nickname", nickname), post.getThumbnailUrl());
        } else {
            notificationService.notifyAsync(post.getUserId(), userId,
                    NotificationType.COMMENT, NotificationTargetType.POST, postId,
                    Map.of("nickname", nickname), post.getThumbnailUrl());
        }

        return toItem(saved, author, false, true, List.of());
    }

    /**
     * 댓글 목록. 최상위 댓글을 페이징하고, 그 자식들을 한 번의 쿼리로 붙인다.
     * 댓글마다 자식을 조회하면 페이지당 20번의 추가 쿼리가 나간다(N+1).
     */
    @Transactional(readOnly = true)
    public PageResponse<Item> list(Long postId, Long viewerId, PageRequestParam pageParam) {
        Page<Comment> roots = commentRepository.findByPostIdAndParentCommentIdIsNullAndStatus(
                postId, CommentStatus.ACTIVE, pageParam.toPageable());

        List<Long> rootIds = roots.getContent().stream().map(Comment::getId).toList();
        List<Comment> replies = rootIds.isEmpty() ? List.of()
                : commentRepository.findByParentCommentIdInAndStatusOrderByIdAsc(rootIds, CommentStatus.ACTIVE);

        List<Comment> all = new ArrayList<>(roots.getContent());
        all.addAll(replies);

        Map<Long, User> authors = new HashMap<>();
        List<Long> userIds = all.stream().map(Comment::getUserId).distinct().toList();
        if (!userIds.isEmpty()) userRepository.findAllById(userIds).forEach(u -> authors.put(u.getId(), u));

        Set<Long> liked = viewerId == null ? Set.of()
                : new HashSet<>(commentLikeRepository.findLikedAmong(
                        viewerId, all.stream().map(Comment::getId).toList()));

        Map<Long, List<Item>> repliesByParent = new HashMap<>();
        for (Comment r : replies) {
            repliesByParent.computeIfAbsent(r.getParentCommentId(), k -> new ArrayList<>())
                    .add(toItem(r, authors.get(r.getUserId()), liked.contains(r.getId()),
                            viewerId != null && r.isOwnedBy(viewerId), List.of()));
        }

        return PageResponse.from(roots, c -> toItem(c, authors.get(c.getUserId()),
                liked.contains(c.getId()), viewerId != null && c.isOwnedBy(viewerId),
                repliesByParent.getOrDefault(c.getId(), List.of())));
    }

    @Transactional
    public void update(Long commentId, Long userId, UpdateRequest req) {
        Comment c = requireOwned(commentId, userId);
        c.updateContent(req.content().trim());
    }

    @Transactional
    public void delete(Long commentId, Long userId, boolean isAdmin) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001));
        if (!isAdmin && !c.isOwnedBy(userId)) throw new BusinessException(ErrorCode.COMMON_403);
        if (!c.isActive()) return;

        c.softDelete();
        postWriteRepository.addCommentCount(c.getPostId(), -1);
    }

    @Transactional
    public LikeResponse like(Long commentId, Long userId) {
        Comment c = requireActive(commentId);
        boolean added = commentLikeRepository.like(commentId, userId);
        if (added) commentLikeRepository.addLikeCount(commentId, 1);
        return new LikeResponse(commentId, true, c.getLikeCount() + (added ? 1 : 0));
    }

    @Transactional
    public LikeResponse unlike(Long commentId, Long userId) {
        Comment c = requireActive(commentId);
        boolean removed = commentLikeRepository.unlike(commentId, userId);
        if (removed) commentLikeRepository.addLikeCount(commentId, -1);
        return new LikeResponse(commentId, false, Math.max(0, c.getLikeCount() - (removed ? 1 : 0)));
    }

    private Comment requireActive(Long commentId) {
        return commentRepository.findById(commentId).filter(Comment::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001));
    }

    private Comment requireOwned(Long commentId, Long userId) {
        Comment c = requireActive(commentId);
        if (!c.isOwnedBy(userId)) throw new BusinessException(ErrorCode.COMMON_403);
        return c;
    }

    private static Item toItem(Comment c, User author, boolean isLiked, boolean isMine, List<Item> replies) {
        Author a = author == null ? null : new Author(author.getId(), author.getNickname(),
                author.getProfileImageUrl(),
                author.getGrade() == null ? null : author.getGrade().name());
        return new Item(c.getId(), c.getContent(), a, c.getLikeCount(), isLiked, isMine,
                c.getCreatedAt(), c.getUpdatedAt(), replies);
    }
}

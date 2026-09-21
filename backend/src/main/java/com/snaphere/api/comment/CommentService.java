package com.snaphere.api.comment;

import com.snaphere.api.comment.dto.CommentResponse;
import com.snaphere.api.comment.dto.CommentThreadResponse;
import com.snaphere.api.comment.dto.CreateCommentRequest;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.comment.repository.CommentRepository;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.PagingProperties;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API-CMU-004 · API-CMU-005 · API-CMU-006 — 댓글 작성·조회·대댓글.
 *
 * <p>기능 명세: 5.3 댓글 &gt; 댓글 작성·조회 · 대댓글
 * <p>요구사항: CMU-012, CMU-013, CMU-014, CMU-015
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final PostRepository posts;
    private final CommentResponseAssembler assembler;
    private final PagingProperties paging;

    public CommentService(CommentRepository comments,
                          PostRepository posts,
                          CommentResponseAssembler assembler,
                          PagingProperties paging) {
        this.comments = comments;
        this.posts = posts;
        this.assembler = assembler;
        this.paging = paging;
    }

    /**
     * 최상위 댓글을 작성한다. (CMU-012)
     *
     * <p>대상은 살아 있는 게시글만이다. 가려진 게시글({@code HIDDEN})에도 댓글을 막는다 — 신고로
     * 가려진 글에 대화가 계속 쌓이면 운영자가 복구를 판단할 때 무엇을 되살리는지가 흐려진다.
     */
    @Transactional
    public CommentResponse create(long postId, UUID userId, CreateCommentRequest request) {
        String content = CommentContent.require(request.content());
        requireActivePost(postId);

        CommentEntity saved = comments.save(CommentEntity.root(postId, userId, content));
        posts.addCommentCount(postId, 1);

        return assembler.response(saved, Set.of());
    }

    /**
     * 대댓글을 작성한다. (CMU-014, CMU-015)
     *
     * <p>깊이는 한 단계로 고정한다. 대댓글에 답글을 달면 그 대댓글이 아니라 <b>그 대댓글의
     * 부모</b>를 가리키게 바꿔 같은 스레드에 붙인다 — 앱에서는 여전히 "답글의 답글"로 보이지만
     * 저장 구조는 평평하다. 무한 트리는 화면도 쿼리도 감당이 안 된다 (CMU-015).
     *
     * <p>게시글 ID 는 요청에서 받지 않고 부모 댓글에서 가져온다. 경로에 없는 값을 본문으로
     * 받으면 남의 게시글 스레드에 댓글을 끼워 넣을 수 있다.
     *
     * <p>삭제된 댓글에는 답글을 달 수 없다. 자리표시자는 이미 달린 자식을 읽히게 하려고 남긴
     * 껍데기이지, 대화를 이어 갈 자리가 아니다 (CMU-017).
     */
    @Transactional
    public CommentResponse reply(long commentId, UUID userId, CreateCommentRequest request) {
        String content = CommentContent.require(request.content());

        CommentEntity target = comments.findById(commentId)
                .filter(CommentEntity::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
        requireActivePost(target.getPostId());

        CommentEntity saved = comments.save(CommentEntity.reply(
                target.getPostId(), userId, target.threadRootId(), content));
        posts.addCommentCount(target.getPostId(), 1);

        return assembler.response(saved, Set.of());
    }

    /**
     * 스레드 한 페이지. 최상위 댓글을 커서로 넘기고 각 부모의 대댓글을 한 번에 붙인다. (CMU-013)
     *
     * <p>없는 게시글이어도 404 가 아니라 빈 페이지다 — 명세의 오류 목록에 게시글 없음이 없다
     * (API-CMU-004). 상세 화면이 이미 게시글을 조회했고, 댓글 목록이 다시 404 를 내면 화면이
     * 통째로 오류가 된다.
     *
     * <p>한 페이지를 요청 크기보다 하나 더 읽어 다음 페이지 존재를 판단한다. 전체 개수를 세면
     * 댓글이 많은 글에서 매번 count 쿼리가 붙는다.
     */
    @Transactional(readOnly = true)
    public CursorPage<CommentThreadResponse> threads(long postId,
                                                     String cursor,
                                                     Integer requestedSize,
                                                     Optional<UUID> viewerId) {
        int size = paging.resolve(requestedSize);
        CommentCursor decoded = CommentCursor.decode(cursor);

        // 상태별로 두 번 읽어 자바에서 합친다. 한 쿼리에서 OR 로 묶으면 PostgreSQL 이 EXISTS 를
        // 해시 서브플랜으로 바꿔 comments 전체를 훑는다 (CommentRepository 주석 참고).
        OffsetDateTime cursorCreatedAt = decoded == null ? null : decoded.createdAt();
        Long cursorCommentId = decoded == null ? null : decoded.commentId();
        Pageable window = PageRequest.of(0, size + 1);

        List<CommentEntity> roots = mergeNewestFirst(
                comments.findActiveRoots(postId, cursorCreatedAt, cursorCommentId, window),
                comments.findDeletedRootsWithActiveReplies(postId, cursorCreatedAt, cursorCommentId, window),
                size + 1);

        boolean hasNext = roots.size() > size;
        if (hasNext) {
            roots = roots.subList(0, size);
        }
        if (roots.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> rootIds = new ArrayList<>(roots.size());
        for (CommentEntity root : roots) {
            rootIds.add(root.getCommentId());
        }
        List<CommentEntity> replies = comments.findRepliesOf(rootIds);

        // 요청자의 좋아요 상태는 CMU-018 에서 채운다. 그때까지는 회원이어도 null 이다 —
        // 빈 집합을 주면 "안 눌렀다"고 단정하는 셈이고, 명세가 isLiked 를 nullable 로 둔 이유가 그것이다.
        // viewerId 는 그 조회에 그대로 쓰이므로 미리 받아 둔다.
        Set<Long> likedByViewer = null;

        Map<Long, CommentResponse> repliesById = new LinkedHashMap<>();
        List<CommentResponse> replyResponses = assembler.responses(replies, likedByViewer);
        for (int i = 0; i < replies.size(); i++) {
            repliesById.put(replies.get(i).getCommentId(), replyResponses.get(i));
        }

        Map<Long, List<CommentResponse>> repliesByParent = new LinkedHashMap<>();
        for (CommentEntity reply : replies) {
            repliesByParent
                    .computeIfAbsent(reply.getParentId(), key -> new ArrayList<>())
                    .add(repliesById.get(reply.getCommentId()));
        }

        List<CommentResponse> rootResponses = assembler.responses(roots, likedByViewer);
        List<CommentThreadResponse> items = new ArrayList<>(roots.size());
        for (int i = 0; i < roots.size(); i++) {
            items.add(new CommentThreadResponse(
                    rootResponses.get(i),
                    repliesByParent.getOrDefault(roots.get(i).getCommentId(), List.of())));
        }

        CommentEntity last = roots.get(roots.size() - 1);
        String nextCursor = hasNext
                ? new CommentCursor(last.getCreatedAt(), last.getCommentId()).encode()
                : null;
        return CursorPage.of(items, nextCursor);
    }

    /** 최신순 정렬 기준. 커서와 같은 두 키를 본다 — 여기가 어긋나면 페이지 경계에서 행이 겹친다. */
    private static final Comparator<CommentEntity> NEWEST_FIRST =
            Comparator.comparing(CommentEntity::getCreatedAt)
                    .thenComparing(CommentEntity::getCommentId)
                    .reversed();

    /**
     * 이미 각각 최신순인 두 목록을 앞에서부터 큰 쪽만 집어 합친다.
     *
     * <p>합친 뒤 다시 정렬하지 않는다. 두 목록 다 한 페이지 분량뿐이라 비용 차이는 작지만, 정렬을
     * 한 번 더 두면 그 기준이 커서 기준과 어긋날 여지가 생긴다 — 경계에서 행이 겹치거나 사라진다.
     */
    private static List<CommentEntity> mergeNewestFirst(List<CommentEntity> active,
                                                        List<CommentEntity> tombstones,
                                                        int limit) {
        List<CommentEntity> merged = new ArrayList<>(limit);
        int i = 0;
        int j = 0;
        while (merged.size() < limit && (i < active.size() || j < tombstones.size())) {
            boolean takeActive = j >= tombstones.size()
                    || (i < active.size()
                        && NEWEST_FIRST.compare(active.get(i), tombstones.get(j)) <= 0);
            merged.add(takeActive ? active.get(i++) : tombstones.get(j++));
        }
        return merged;
    }

    private void requireActivePost(long postId) {
        posts.findByPostIdAndStatus(postId, PostStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }
}

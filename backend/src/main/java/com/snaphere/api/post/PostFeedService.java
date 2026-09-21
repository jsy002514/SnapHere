package com.snaphere.api.post;

import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.PagingProperties;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.PostRankingRepository;
import com.snaphere.api.post.repository.TagRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API-PST-004 — 게시글 목록 조회. (PST-034)
 *
 * <p>기능 명세: 4.1 지역 커뮤니티 · 5.1 본문
 *
 * <p>비회원도 조회할 수 있고, 삭제·블라인드 게시글은 목록에서 제외된다.
 */
@Service
public class PostFeedService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // SYS-005

    private final PostRepository posts;
    private final TagRepository tags;
    private final PostRankingRepository rankings;
    private final PostResponseAssembler assembler;
    private final PagingProperties paging;

    public PostFeedService(PostRepository posts,
                           TagRepository tags,
                           PostRankingRepository rankings,
                           PostResponseAssembler assembler,
                           PagingProperties paging) {
        this.posts = posts;
        this.tags = tags;
        this.rankings = rankings;
        this.assembler = assembler;
        this.paging = paging;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostSummaryResponse> list(Integer areaCode, Long placeId, String tag,
                                                PostFeedPeriod period, String cursor, Integer size,
                                                Optional<UUID> viewerId) {
        Long tagId = resolveTagId(tag);
        if (tag != null && !tag.isBlank() && tagId == null) {
            // 아무도 쓰지 않은 태그다. 에러가 아니라 결과가 없는 것이다 (CMU-030).
            return CursorPage.empty();
        }

        int pageSize = paging.resolve(size);
        PostCursor decoded = PostCursor.decode(cursor);
        OffsetDateTime createdFrom = (period == null ? PostFeedPeriod.DEFAULT : period)
                .from(OffsetDateTime.now(KST));

        // 한 건 더 읽어 다음 페이지가 있는지 본다. COUNT 를 따로 세면 같은 조건을 두 번 훑는다.
        List<PostEntity> rows = posts.findFeed(areaCode, placeId, tagId, createdFrom,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.postId(),
                PageRequest.of(0, pageSize + 1));

        boolean hasNext = rows.size() > pageSize;
        List<PostEntity> page = hasNext ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        PostEntity last = page.get(page.size() - 1);
        String nextCursor = hasNext
                ? new PostCursor(last.getCreatedAt(), last.getPostId()).encode()
                : null;
        return CursorPage.of(assembler.summaries(page, viewerId), nextCursor);
    }

    private Long resolveTagId(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return tags.findByNormalizedName(TagEntity.normalize(tag))
                .map(TagEntity::getTagId)
                .orElse(null);
    }

    /**
     * API-PST-005 — 기간별 인기 게시글. (PST-035)
     *
     * <p>{@code post_rankings} 만 읽는다. 좋아요·댓글·조회수를 요청마다 집계하면 무한 스크롤이
     * 버티지 못하고, 같은 목록을 두 사람이 볼 때 순서가 달라진다 (CMU-008).
     *
     * <p>배치가 아직 돌지 않았으면 빈 목록이다. 그때 실시간 계산으로 대신 채우면
     * "조회 시 계산 금지"를 어기게 되고, 배치가 도는 순간 순서가 통째로 바뀐다.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryResponse> popular(PostFeedPeriod period, Integer areaCode,
                                                   String cursor, Integer size,
                                                   Optional<UUID> viewerId) {
        int pageSize = paging.resolve(size);
        PostRankCursor decoded = PostRankCursor.decode(cursor);
        PostFeedPeriod window = period == null ? PostFeedPeriod.DEFAULT : period;

        List<PostEntity> rows = rankings.findPopular(window, areaCode,
                decoded == null ? null : decoded.rankNo(),
                PageRequest.of(0, pageSize + 1));

        boolean hasNext = rows.size() > pageSize;
        List<PostEntity> page = hasNext ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        String nextCursor = null;
        if (hasNext) {
            Integer lastRank = rankings.findRankNo(window, page.get(page.size() - 1).getPostId());
            nextCursor = lastRank == null ? null : new PostRankCursor(lastRank).encode();
        }
        return CursorPage.of(assembler.summaries(page, viewerId), nextCursor);
    }

    /** 장소 상세의 게시글 그리드. (PLC-013) */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryResponse> listByPlace(long placeId, String cursor, Integer size,
                                                       Optional<UUID> viewerId) {
        return list(null, placeId, null, PostFeedPeriod.ALL, cursor, size, viewerId);
    }

}

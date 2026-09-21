package com.snaphere.api.post.tag;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.PagingProperties;
import com.snaphere.api.post.PostCursor;
import com.snaphere.api.post.PostResponseAssembler;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.dto.TagSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.PostTagRepository;
import com.snaphere.api.post.repository.TagRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API-CMU-012 · API-CMU-013 — 인기 태그·태그 게시글 조회.
 *
 * <p>기능 명세: 1.2 검색 &gt; 태그 검색 (인기 태그 목록은 화면 정의에 없다 — 태그 검색의 진입을
 * 돕는 조회다)
 * <p>요구사항: CMU-030, CMU-031, SCH-007
 */
@Service
public class TagQueryService {

    /** 명세 기본 20 · 최대 50. 커서가 없는 목록이라 페이징 규약과 따로 둔다. */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final PostTagRepository postTags;
    private final TagRepository tags;
    private final PostRepository posts;
    private final PostResponseAssembler assembler;
    private final PagingProperties paging;

    public TagQueryService(PostTagRepository postTags,
                           TagRepository tags,
                           PostRepository posts,
                           PostResponseAssembler assembler,
                           PagingProperties paging) {
        this.postTags = postTags;
        this.tags = tags;
        this.posts = posts;
        this.assembler = assembler;
        this.paging = paging;
    }

    /**
     * 태그가 붙은 공개 게시글. (CMU-030, SCH-007)
     *
     * <p>없는 태그는 빈 목록이 아니라 {@code TAG_NOT_FOUND} 다. 태그를 병합·삭제하면 예전 링크가
     * 그 ID 를 계속 들고 오는데, 빈 목록으로 답하면 앱이 "글이 없는 태그"로 그려 사용자가 계속
     * 새로고침한다 — 명세가 이 엔드포인트에만 오류 코드를 둔 이유다.
     *
     * <p>이름이 아니라 ID 로 받는다. 이름으로 받으면 정규화 규칙이 바뀔 때 예전 링크가 깨진다.
     *
     * <p>기간 필터가 없다({@code createdFrom = null}). 태그 검색은 그 태그가 붙은 글 전부이고,
     * 목록 조회(API-PST-004)처럼 최근 7일로 자르면 태그를 눌렀는데 빈 화면이 흔해진다.
     *
     * <p>페이징 코드가 {@code PostFeedService} 와 겹친다. 공용 파일을 건드리지 않기 위해 일부러
     * 따로 두었다 — 그쪽을 고쳐 함께 쓰게 만들면 게시글 목록을 만지는 사람과 충돌한다.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryResponse> postsByTag(long tagId, String cursor, Integer size) {
        if (!tags.existsById(tagId)) {
            throw new ApiException(ErrorCode.TAG_NOT_FOUND);
        }

        int pageSize = paging.resolve(size);
        PostCursor decoded = PostCursor.decode(cursor);

        // 한 건 더 읽어 다음 페이지가 있는지 본다. COUNT 를 따로 세면 같은 조건을 두 번 훑는다.
        List<PostEntity> rows = posts.findFeed(null, null, tagId, null,
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
        return CursorPage.of(assembler.summaries(page), nextCursor);
    }

    /**
     * 지역별 인기 태그. {@code areaCode} 를 생략하면 전국이다. (CMU-031)
     *
     * <p>집계 순서를 응답 순서로 그대로 옮긴다 — {@code findAllById} 는 순서를 보장하지 않으므로
     * ID 목록으로 다시 줄을 세운다. 이걸 빼면 인기 순위가 조회마다 뒤섞인다.
     */
    @Transactional(readOnly = true)
    public List<TagSummaryResponse> popular(Integer areaCode, Integer requestedLimit) {
        int limit = resolveLimit(requestedLimit);

        List<Object[]> counted = postTags.findPopularTagCounts(areaCode, PageRequest.of(0, limit));
        if (counted.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> countByTagId = new LinkedHashMap<>();
        for (Object[] row : counted) {
            countByTagId.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        Map<Long, TagEntity> found = new LinkedHashMap<>();
        for (TagEntity tag : tags.findAllById(countByTagId.keySet())) {
            found.put(tag.getTagId(), tag);
        }

        List<TagSummaryResponse> result = new ArrayList<>(countByTagId.size());
        for (Map.Entry<Long, Long> entry : countByTagId.entrySet()) {
            TagEntity tag = found.get(entry.getKey());
            if (tag != null) {
                result.add(TagSummaryResponse.popular(tag, entry.getValue()));
            }
        }
        return result;
    }

    /**
     * 잘못된 개수를 400 으로 돌려주지 않고 조용히 자른다. 목록이 크기 때문에 실패하면 앱이 화면을
     * 못 그린다 — PagingProperties 와 같은 판단이다.
     */
    private int resolveLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}

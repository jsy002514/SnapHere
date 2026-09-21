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
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 인기 태그·태그 게시글 조회 — CMU-030, CMU-031
 *
 * <p>집계 순서를 응답이 그대로 지키는지, 개수 규약을 어떻게 자르는지, 없는 태그를 어떻게
 * 답하는지가 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagQueryServiceTest {

    private static final long TAG_ID = 3L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock private PostTagRepository postTags;
    @Mock private TagRepository tags;
    @Mock private PostRepository posts;
    @Mock private PostResponseAssembler assembler;

    private TagQueryService service;

    @BeforeEach
    void setUp() {
        service = new TagQueryService(postTags, tags, posts, assembler,
                new PagingProperties(20, 50));
        when(assembler.summaries(any()))
                .thenAnswer(call -> Collections.<PostSummaryResponse>nCopies(
                        ((List<?>) call.getArgument(0)).size(), null));
    }

    private static TagEntity tag(long tagId, String name) {
        TagEntity tag = TagEntity.of(name);
        ReflectionTestUtils.setField(tag, "tagId", tagId);
        ReflectionTestUtils.setField(tag, "usageCount", 9999L);
        return tag;
    }

    private static PostEntity post(long postId, int minute) {
        PostEntity post = PostEntity.create(AUTHOR, 1L, null, 1, "캡션", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
        ReflectionTestUtils.setField(post, "postId", postId);
        ReflectionTestUtils.setField(post, "createdAt",
                OffsetDateTime.parse("2026-09-03T12:%02d:00Z".formatted(minute)));
        return post;
    }

    @Test
    @DisplayName("집계 순서를 그대로 지킨다 — findAllById 는 순서를 보장하지 않는다")
    void keepsCountedOrder() {
        when(postTags.findPopularTagCounts(isNull(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 10L}, new Object[]{1L, 4L}));
        // 저장소가 ID 오름차순으로 돌려주는 상황을 만든다.
        when(tags.findAllById(any())).thenReturn(List.of(tag(1L, "서울"), tag(2L, "야경")));

        List<TagSummaryResponse> popular = service.popular(null, null);

        assertThat(popular).extracting(TagSummaryResponse::name)
                .containsExactly("야경", "서울");
    }

    @Test
    @DisplayName("사용 횟수는 집계값이다 — tags.usage_count 는 지역 구분 없는 누적이라 쓰지 않는다")
    void usesCountedValue() {
        when(postTags.findPopularTagCounts(any(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 4L}));
        when(tags.findAllById(any())).thenReturn(List.of(tag(1L, "서울")));

        assertThat(service.popular(1, null).get(0).usageCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("개수를 안 보내면 20, 50 을 넘기면 50 으로 자른다")
    void limitContract() {
        when(postTags.findPopularTagCounts(any(), any(Pageable.class))).thenReturn(List.of());

        service.popular(null, null);
        service.popular(null, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postTags, times(2)).findPopularTagCounts(isNull(), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(20);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("집계가 비면 태그 조회를 하지 않는다")
    void emptyCounts() {
        when(postTags.findPopularTagCounts(any(), any(Pageable.class))).thenReturn(List.of());

        assertThat(service.popular(null, null)).isEmpty();
        verify(tags, never()).findAllById(any());
    }

    @Test
    @DisplayName("없는 태그는 빈 목록이 아니라 TAG_NOT_FOUND — 병합·삭제된 태그 링크가 계속 들어온다")
    void missingTag() {
        when(tags.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.postsByTag(9L, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.TAG_NOT_FOUND));

        verify(posts, never()).findFeed(any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("태그 검색에는 기간 필터가 없다 — 최근 7일로 자르면 빈 화면이 흔해진다")
    void hasNoPeriodFilter() {
        when(tags.existsById(TAG_ID)).thenReturn(true);
        when(posts.findFeed(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of(post(1L, 1)));

        service.postsByTag(TAG_ID, null, null);

        // areaCode·placeId·createdFrom 이 모두 null 이고 tagId 만 걸린다.
        verify(posts).findFeed(isNull(), isNull(), eq(TAG_ID), isNull(),
                isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("요청 크기보다 하나 더 읽어 다음 페이지를 판단하고, 마지막 행으로 커서를 만든다")
    void paginates() {
        when(tags.existsById(TAG_ID)).thenReturn(true);
        List<PostEntity> rows = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            rows.add(post(100 + i, i));
        }
        when(posts.findFeed(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(rows);

        CursorPage<PostSummaryResponse> page = service.postsByTag(TAG_ID, null, null);

        assertThat(page.items()).hasSize(20);
        assertThat(page.hasNext()).isTrue();
        // 21번째 행은 존재 판단에만 쓰고 버린다. 커서는 20번째 행 기준이어야 다음 페이지가 이어진다.
        assertThat(PostCursor.decode(page.nextCursor()).postId()).isEqualTo(119L);
    }

    @Test
    @DisplayName("결과가 없으면 빈 페이지다")
    void emptyPage() {
        when(tags.existsById(TAG_ID)).thenReturn(true);
        when(posts.findFeed(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());

        CursorPage<PostSummaryResponse> page = service.postsByTag(TAG_ID, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }
}

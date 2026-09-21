package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.place.repository.PlaceRepository;
import com.snaphere.api.post.dto.UpdatePostRequest;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.PostTagRepository;
import com.snaphere.api.post.repository.TagRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시글 수정·삭제 — PST-036, PST-037, PST-038, AUTH-013
 *
 * <p>권한 판정과 삭제 멱등성, 사진 순서 검증이 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostEditServiceTest {

    private static final long POST_ID = 7L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID STRANGER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Mock private PostRepository posts;
    @Mock private PostImageRepository postImages;
    @Mock private PostTagRepository postTags;
    @Mock private TagRepository tags;
    @Mock private PlaceRepository places;
    @Mock private TagService tagService;
    @Mock private PostCreateValidator validator;
    @Mock private PostResponseAssembler assembler;

    private PostEditService service;

    @BeforeEach
    void setUp() {
        service = new PostEditService(posts, postImages, postTags, tags, places,
                tagService, validator, assembler);
    }

    private static PostEntity activePost() {
        return PostEntity.create(AUTHOR, 1L, null, 1, "원래 캡션", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    private static UpdatePostRequest contentOnly(String content) {
        return new UpdatePostRequest(content, null, null);
    }

    // ───────────────────────────────────────────── 권한 (AUTH-013)

    @Test
    @DisplayName("없는 게시글은 POST_NOT_FOUND")
    void 없는_게시글() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(POST_ID, AUTHOR, contentOnly("새 캡션")))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("남의 게시글은 POST_NOT_AUTHOR — 404 를 주면 앱이 목록에서 지워 버린다")
    void 남의_게시글_수정() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));

        assertThatThrownBy(() -> service.update(POST_ID, STRANGER, contentOnly("새 캡션")))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_AUTHOR));
    }

    @Test
    @DisplayName("남의 게시글은 삭제도 POST_NOT_AUTHOR")
    void 남의_게시글_삭제() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));

        assertThatThrownBy(() -> service.delete(POST_ID, STRANGER))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_AUTHOR));
    }

    // ───────────────────────────────────────────── 수정 (PST-036)

    @Test
    @DisplayName("캡션만 보내면 캡션만 바뀐다")
    void 캡션_수정() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));

        service.update(POST_ID, AUTHOR, contentOnly("새 캡션"));

        assertThat(post.getContent()).isEqualTo("새 캡션");
        verify(postTags, never()).deleteByIdPostId(anyLong());
        verify(postImages, never()).saveAll(any());
    }

    @Test
    @DisplayName("보내지 않은 필드는 건드리지 않는다 — PATCH 는 부분 수정이다")
    void 빈_요청() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));

        service.update(POST_ID, AUTHOR, new UpdatePostRequest(null, null, null));

        assertThat(post.getContent()).isEqualTo("원래 캡션");
        verify(postTags, never()).deleteByIdPostId(anyLong());
    }

    @Test
    @DisplayName("캡션을 빈 문자열로 비울 수 있다")
    void 캡션_비우기() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));

        service.update(POST_ID, AUTHOR, contentOnly(""));

        assertThat(post.getContent()).isEmpty();
    }

    @Test
    @DisplayName("사진 순서는 기존 ID 전체를 받아야 한다")
    void 사진_순서_개수_불일치() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postImages.findByPostIdOrderBySortOrder(any())).thenReturn(List.of(
                image(101L, 1), image(102L, 2)));

        UpdatePostRequest request = new UpdatePostRequest(null, null, List.of(101L));

        assertThatThrownBy(() -> service.update(POST_ID, AUTHOR, request))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMON_422));
    }

    @Test
    @DisplayName("없는 사진 ID 가 섞이면 거부한다")
    void 사진_순서_잘못된_ID() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postImages.findByPostIdOrderBySortOrder(any())).thenReturn(List.of(
                image(101L, 1), image(102L, 2)));

        UpdatePostRequest request = new UpdatePostRequest(null, null, List.of(101L, 999L));

        assertThatThrownBy(() -> service.update(POST_ID, AUTHOR, request))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMON_422));
    }

    @Test
    @DisplayName("같은 사진 ID 를 두 번 보내면 거부한다")
    void 사진_순서_중복() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postImages.findByPostIdOrderBySortOrder(any())).thenReturn(List.of(
                image(101L, 1), image(102L, 2)));

        UpdatePostRequest request = new UpdatePostRequest(null, null, List.of(101L, 101L));

        assertThatThrownBy(() -> service.update(POST_ID, AUTHOR, request))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMON_422));
    }

    @Test
    @DisplayName("순서를 맞바꾸면 1부터 다시 매긴다")
    void 사진_순서_교환() {
        PostEntity post = activePost();
        PostImageEntity first = image(101L, 1);
        PostImageEntity second = image(102L, 2);
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postImages.findByPostIdOrderBySortOrder(any())).thenReturn(List.of(first, second));

        service.update(POST_ID, AUTHOR, new UpdatePostRequest(null, null, List.of(102L, 101L)));

        assertThat(second.getSortOrder()).isEqualTo((short) 1);
        assertThat(first.getSortOrder()).isEqualTo((short) 2);
        verify(postImages).saveAll(any());
    }

    // ───────────────────────────────────────────── 삭제 (PST-038)

    @Test
    @DisplayName("삭제는 상태만 바꾼다 — 행은 남는다")
    void 논리_삭제() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));

        service.delete(POST_ID, AUTHOR);

        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        verify(posts, never()).delete(any());
    }

    @Test
    @DisplayName("삭제하면 장소 게시글 수를 되돌린다")
    void 장소_카운터_감소() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));

        service.delete(POST_ID, AUTHOR);

        verify(places).addPostCount(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("이미 삭제된 게시글을 다시 삭제하면 COMMON_409")
    void 중복_삭제() {
        PostEntity post = activePost();
        post.softDelete();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.delete(POST_ID, AUTHOR))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMON_409));
    }

    private static PostImageEntity image(long id, int sortOrder) {
        PostImageEntity image = PostImageEntity.create(POST_ID, "posts/x/" + id + ".jpg",
                sortOrder, null, null);
        setId(image, id);
        return image;
    }

    /** {@code postImageId} 는 DB 가 만든다. 테스트에서만 직접 넣는다. */
    private static void setId(PostImageEntity image, long id) {
        try {
            java.lang.reflect.Field field = PostImageEntity.class.getDeclaredField("postImageId");
            field.setAccessible(true);
            field.set(image, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

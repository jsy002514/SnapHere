package com.snaphere.api.post;

import com.snaphere.api.media.storage.MediaUrlResolver;
import com.snaphere.api.place.entity.PlaceEntity;
import com.snaphere.api.place.repository.PlaceRepository;
import com.snaphere.api.post.dto.PlaceSummaryResponse;
import com.snaphere.api.post.dto.PostDetailResponse;
import com.snaphere.api.post.dto.PostImageResponse;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.dto.TagSummaryResponse;
import com.snaphere.api.post.dto.TierResultResponse;
import com.snaphere.api.post.dto.UserSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.entity.PostTagEntity;
import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostTagRepository;
import com.snaphere.api.post.repository.TagRepository;
import com.snaphere.api.post.repository.TierLogRepository;
import com.snaphere.api.reaction.BookmarkTargetType;
import com.snaphere.api.reaction.LikeTargetType;
import com.snaphere.api.reaction.repository.BookmarkRepository;
import com.snaphere.api.reaction.repository.LikeRepository;
import com.snaphere.api.user.AuthorSnapshot;
import com.snaphere.api.user.AuthorSnapshotReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 게시글 응답 조립. (PST-021, PST-033, PST-034, SOC-013, SYS-018)
 *
 * <p><b>여러 건을 한 번에 조립한다.</b> 카드마다 작성자·장소·사진·태그를 따로 조회하면 20개
 * 목록에 쿼리가 80번 넘게 나간다(N+1). 목록의 게시글 ID 를 모아 각 테이블을 한 번씩만 읽는다.
 *
 * <p>등록·조회·수정이 모두 이 클래스를 쓴다. 응답 모양이 엔드포인트마다 갈라지면 앱이 같은
 * 게시글을 두 가지로 파싱해야 한다.
 */
@Component
public class PostResponseAssembler {

    private final PostImageRepository postImages;
    private final PostTagRepository postTags;
    private final TagRepository tags;
    private final PlaceRepository places;
    private final TierLogRepository tierLogs;
    private final LikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final AuthorSnapshotReader authors;
    private final MediaUrlResolver mediaUrls;

    public PostResponseAssembler(PostImageRepository postImages,
                                 PostTagRepository postTags,
                                 TagRepository tags,
                                 PlaceRepository places,
                                 TierLogRepository tierLogs,
                                 LikeRepository likes,
                                 BookmarkRepository bookmarks,
                                 AuthorSnapshotReader authors,
                                 MediaUrlResolver mediaUrls) {
        this.postImages = postImages;
        this.postTags = postTags;
        this.tags = tags;
        this.places = places;
        this.tierLogs = tierLogs;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.authors = authors;
        this.mediaUrls = mediaUrls;
    }

    /**
     * 목록 응답. 입력 순서를 그대로 유지한다 — 정렬은 조회 쪽이 이미 정했다.
     *
     * @param viewerId 로그인 사용자. 비회원이면 비어 있고 {@code isLiked} 는 null 이 된다
     */
    public List<PostSummaryResponse> summaries(List<PostEntity> posts, Optional<UUID> viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Batch batch = load(posts, viewerId);
        List<PostSummaryResponse> result = new ArrayList<>(posts.size());
        for (PostEntity post : posts) {
            result.add(summary(post, batch));
        }
        return result;
    }

    /** 비회원 목록 조립용 호환 오버로드. */
    public List<PostSummaryResponse> summaries(List<PostEntity> posts) {
        return summaries(posts, Optional.empty());
    }

    /** 상세 응답. 사진 전체와 태그, 판정 근거를 함께 담는다. */
    public PostDetailResponse detail(PostEntity post, Optional<UUID> viewerId) {
        Batch batch = load(List.of(post), viewerId);
        List<PostImageResponse> images = batch.images.getOrDefault(post.getPostId(), List.of());
        return PostDetailResponse.of(post, summary(post, batch), images,
                batch.tags.getOrDefault(post.getPostId(), List.of()), tierResult(post));
    }

    /**
     * 등록 직후 응답. 방금 만든 엔티티를 이미 손에 들고 있으므로 다시 읽지 않는다.
     *
     * <p>등급 근거도 판정 결과에서 그대로 온다 — {@code tier_logs} 를 다시 읽으면 같은 트랜잭션
     * 안에서 방금 넣은 행을 찾는 셈이 된다.
     */
    public PostDetailResponse detailOf(PostEntity post, PlaceEntity place,
                                       List<PostImageEntity> images,
                                       List<TagEntity> resolvedTags,
                                       List<PostTagEntity> tagLinks,
                                       TierResultResponse tierResult) {
        List<PostImageResponse> imageResponses = toImageResponses(images);
        List<TagSummaryResponse> tagResponses = new ArrayList<>(resolvedTags.size());
        for (int i = 0; i < resolvedTags.size(); i++) {
            tagResponses.add(TagSummaryResponse.from(
                    resolvedTags.get(i), i < tagLinks.size() ? tagLinks.get(i) : null));
        }
        // 방금 만든 게시글이다. 작성자가 아직 좋아요·저장을 누를 수 없으므로 둘 다 false 다.
        PostSummaryResponse summary = PostSummaryResponse.of(
                post, author(post.getUserId()), PlaceSummaryResponse.from(place), imageResponses,
                false, false);
        return PostDetailResponse.of(post, summary, imageResponses, tagResponses, tierResult);
    }

    // ─────────────────────────────────────────────────────────── 내부

    private PostSummaryResponse summary(PostEntity post, Batch batch) {
        PlaceEntity place = batch.places.get(post.getPlaceId());
        List<PostImageResponse> images = batch.images.getOrDefault(post.getPostId(), List.of());
        return PostSummaryResponse.of(post,
                batch.authors.getOrDefault(post.getUserId(), fallbackAuthor(post.getUserId())),
                place == null ? null : PlaceSummaryResponse.from(place),
                images,
                batch.likedPostIds == null ? null : batch.likedPostIds.contains(post.getPostId()),
                batch.bookmarkedPostIds == null
                        ? null : batch.bookmarkedPostIds.contains(post.getPostId()));
    }

    /**
     * 판정 근거는 {@code tier_logs} 의 최신 1행에서 온다 (PST-028, PST-047). 행이 없으면
     * 등급만 담아 준다 — 후처리 이전 게시글이나 로그 적재 실패로 비어 있을 수 있다.
     */
    private TierResultResponse tierResult(PostEntity post) {
        return tierLogs.findFirstByPostIdOrderByDecidedAtDesc(post.getPostId())
                .map(log -> TierResultResponse.from(log.toDecision()))
                .orElseGet(() -> TierResultResponse.tierOnly(post.getTier()));
    }

    /**
     * 목록에서도 사진을 전부 읽는다. 대표 한 장만 가져오면 첨부 장수(SOC-013)를 셀 수 없고,
     * 그것 때문에 카드마다 count 쿼리를 또 날리게 된다.
     */
    private Batch load(List<PostEntity> posts, Optional<UUID> viewerId) {
        Set<Long> postIds = new LinkedHashSet<>();
        Set<Long> placeIds = new LinkedHashSet<>();
        Set<UUID> userIds = new LinkedHashSet<>();
        for (PostEntity post : posts) {
            postIds.add(post.getPostId());
            placeIds.add(post.getPlaceId());
            userIds.add(post.getUserId());
        }

        Map<Long, List<PostImageResponse>> images = new LinkedHashMap<>();
        List<PostImageEntity> imageRows =
                postImages.findByPostIdInOrderByPostIdAscSortOrderAsc(postIds);
        for (PostImageEntity image : imageRows) {
            images.computeIfAbsent(image.getPostId(), key -> new ArrayList<>())
                    .add(PostImageResponse.from(image, mediaUrls.publicUrl(image.getImageKey())));
        }

        Map<Long, PlaceEntity> placeMap = new LinkedHashMap<>();
        for (PlaceEntity place : places.findAllById(placeIds)) {
            placeMap.put(place.getPlaceId(), place);
        }

        Map<UUID, UserSummaryResponse> authorMap = new LinkedHashMap<>();
        authors.findAllByIds(userIds)
                .forEach((id, snapshot) -> authorMap.put(id, UserSummaryResponse.from(snapshot)));

        return new Batch(images, placeMap, authorMap, loadTags(postIds),
                loadLiked(postIds, viewerId), loadBookmarked(postIds, viewerId));
    }

    /**
     * 요청자가 좋아요를 누른 게시글. (PST-040)
     *
     * <p>비회원이면 null 을 준다 — false 가 아니다. 명세의 {@code isLiked} 는 선택 필드이고,
     * "안 눌렀다"와 "알 수 없다"는 앱에서 다르게 그려진다.
     */
    private Set<Long> loadLiked(Collection<Long> postIds, Optional<UUID> viewerId) {
        if (viewerId.isEmpty()) {
            return null;
        }
        return new LinkedHashSet<>(
                likes.findLikedTargetIds(viewerId.get(), LikeTargetType.POST, postIds));
    }

    /** 요청자가 저장한 게시글. 비회원이면 null 이다. (CMU-023) */
    private Set<Long> loadBookmarked(Collection<Long> postIds, Optional<UUID> viewerId) {
        if (viewerId.isEmpty()) {
            return null;
        }
        return new LinkedHashSet<>(bookmarks.findBookmarkedTargetIds(
                viewerId.get(), BookmarkTargetType.POST, postIds));
    }

    private Map<Long, List<TagSummaryResponse>> loadTags(Collection<Long> postIds) {
        List<PostTagEntity> links = postTags.findByIdPostIdIn(postIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = new LinkedHashSet<>();
        for (PostTagEntity link : links) {
            tagIds.add(link.getId().getTagId());
        }
        Map<Long, TagEntity> tagMap = new LinkedHashMap<>();
        for (TagEntity tag : tags.findAllById(tagIds)) {
            tagMap.put(tag.getTagId(), tag);
        }
        Map<Long, List<TagSummaryResponse>> result = new LinkedHashMap<>();
        for (PostTagEntity link : links) {
            TagEntity tag = tagMap.get(link.getId().getTagId());
            if (tag != null) {
                result.computeIfAbsent(link.getId().getPostId(), key -> new ArrayList<>())
                        .add(TagSummaryResponse.from(tag, link));
            }
        }
        return result;
    }

    private List<PostImageResponse> toImageResponses(List<PostImageEntity> images) {
        List<PostImageResponse> result = new ArrayList<>(images.size());
        for (PostImageEntity image : images) {
            result.add(PostImageResponse.from(image, mediaUrls.publicUrl(image.getImageKey())));
        }
        return result;
    }

    private UserSummaryResponse author(UUID userId) {
        return authors.findById(userId)
                .map(UserSummaryResponse::from)
                .orElseGet(() -> fallbackAuthor(userId));
    }

    /** 탈퇴·삭제된 작성자. 닉네임을 비워 주고 앱이 "알 수 없는 사용자"로 표시한다 (USER-015). */
    private UserSummaryResponse fallbackAuthor(UUID userId) {
        return UserSummaryResponse.from(new AuthorSnapshot(userId, null, null));
    }

    private record Batch(
            Map<Long, List<PostImageResponse>> images,
            Map<Long, PlaceEntity> places,
            Map<UUID, UserSummaryResponse> authors,
            Map<Long, List<TagSummaryResponse>> tags,
            Set<Long> likedPostIds,
            Set<Long> bookmarkedPostIds
    ) {
    }
}

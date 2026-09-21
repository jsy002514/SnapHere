package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.dto.PostDetailResponse;
import com.snaphere.api.post.dto.UpdatePostRequest;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.entity.PostTagEntity;
import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.PostTagRepository;
import com.snaphere.api.post.repository.TagRepository;
import com.snaphere.api.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API-PST-007 · API-PST-008 — 게시글 수정·삭제. (PST-036, PST-038)
 *
 * <p>기능 명세: 5.5 관리
 *
 * <p>수정할 수 있는 것은 캡션·태그·사진 순서뿐이다. 장소·좌표·등급·촬영 시각은 요청 본문에
 * 아예 없다 (PST-037) — 게시 후에 바꿀 수 있으면 다른 곳에서 찍은 사진을 올린 뒤 장소만
 * 바꿔치기해 높음 등급을 얻을 수 있다.
 *
 * <p>삭제는 상태만 바꾼다 (PST-038). 방문 기록과 이미 받은 뱃지는 남는다 — 실제로 갔던 사실은
 * 사라지지 않는다 (PST-039). 사진 파일 삭제는 30일 후 배치가 한다.
 */
@Service
public class PostEditService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // SYS-005

    private final PostRepository posts;
    private final PostImageRepository postImages;
    private final PostTagRepository postTags;
    private final TagRepository tags;
    private final PlaceRepository places;
    private final TagService tagService;
    private final PostCreateValidator validator;
    private final PostResponseAssembler assembler;

    public PostEditService(PostRepository posts,
                           PostImageRepository postImages,
                           PostTagRepository postTags,
                           TagRepository tags,
                           PlaceRepository places,
                           TagService tagService,
                           PostCreateValidator validator,
                           PostResponseAssembler assembler) {
        this.posts = posts;
        this.postImages = postImages;
        this.postTags = postTags;
        this.tags = tags;
        this.places = places;
        this.tagService = tagService;
        this.validator = validator;
        this.assembler = assembler;
    }

    @Transactional
    public PostDetailResponse update(long postId, UUID userId, UpdatePostRequest request) {
        PostEntity post = loadOwned(postId, userId);

        if (request.hasContent()) {
            post.editContent(request.content());
        }
        if (request.hasTagNames()) {
            replaceTags(post.getPostId(), request.tagNames());
        }
        if (request.hasImageOrder()) {
            reorderImages(post.getPostId(), request.imageOrder());
        }
        return assembler.detail(post, Optional.of(userId));
    }

    /**
     * 삭제. 상태만 바꾸고 행은 남긴다. (PST-038)
     *
     * <p>이미 삭제된 게시글에 다시 요청하면 {@code COMMON_409} 다. 조용히 성공시키면 앱이
     * 두 번째 요청도 통했다고 보고 화면을 다시 갱신한다.
     */
    @Transactional
    public void delete(long postId, UUID userId) {
        PostEntity post = loadOwned(postId, userId);
        if (post.getStatus() == PostStatus.DELETED) {
            throw new ApiException(ErrorCode.COMMON_409, Map.of("postId", postId));
        }
        post.softDelete();

        // 장소 게시글 수를 되돌린다. 삭제된 게시글이 장소 랭킹에 계속 잡히면 안 된다 (RNK-001).
        places.addPostCount(post.getPlaceId(), -1, OffsetDateTime.now(KST));
    }

    // ─────────────────────────────────────────────────────────── 내부

    /**
     * 작성자 본인만 수정·삭제할 수 있다. (AUTH-013)
     *
     * <p>없는 게시글과 남의 게시글을 다른 코드로 준다. 남의 게시글에 404 를 주면 앱이 목록에서
     * 지워 버리는데, 실제로는 그 게시글이 남아 있어 다음 조회에 다시 나타난다.
     */
    private PostEntity loadOwned(long postId, UUID userId) {
        PostEntity post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND,
                        Map.of("postId", postId)));
        if (!post.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.POST_NOT_AUTHOR, Map.of("postId", postId));
        }
        return post;
    }

    /**
     * 태그 전체 교체. (CMU-032)
     *
     * <p>기존 연결을 모두 지우고 다시 넣는다. 무엇이 빠지고 무엇이 들어왔는지 계산하는 방식은
     * 버그를 부른다 — 정규화 결과가 같은 태그를 표기만 바꿔 보내면 차이 계산이 어긋난다.
     *
     * <p>사용 횟수도 함께 되돌린다. 지운 태그는 내리고 새로 넣은 태그는 올린다.
     */
    private void replaceTags(Long postId, List<String> tagNames) {
        List<TagEntity> resolved = tagService.resolveAll(tagNames);
        validator.validateTagCount(resolved.size());

        List<PostTagEntity> existing = postTags.findByIdPostId(postId);
        List<Long> removed = new ArrayList<>(existing.size());
        Set<Long> keepLocked = new LinkedHashSet<>();
        for (PostTagEntity link : existing) {
            if (link.isLocked()) {
                // 행사 고정 태그는 사용자가 뗄 수 없다 (EVT-018). 교체 대상에서 제외한다.
                keepLocked.add(link.getId().getTagId());
            } else {
                removed.add(link.getId().getTagId());
            }
        }

        postTags.deleteByIdPostId(postId);
        postTags.flush();
        if (!removed.isEmpty()) {
            tags.addUsageCount(removed, -1);
        }

        List<PostTagEntity> links = new ArrayList<>();
        List<Long> added = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long lockedTagId : keepLocked) {
            links.add(PostTagEntity.of(postId, lockedTagId, true, false));
            seen.add(lockedTagId);
        }
        for (TagEntity tag : resolved) {
            if (seen.add(tag.getTagId())) {
                links.add(PostTagEntity.of(postId, tag.getTagId(), false, false));
                added.add(tag.getTagId());
            }
        }
        postTags.saveAll(links);
        if (!added.isEmpty()) {
            tags.addUsageCount(added, 1);
        }
    }

    /**
     * 사진 순서 변경. (PST-036)
     *
     * <p>기존 사진 ID 전체를 새 순서로 받는다. 일부만 받으면 나머지 순서가 불명확해지고,
     * 없는 ID 나 남의 사진 ID 가 섞이면 그대로 거부한다.
     *
     * <p>{@code (post_id, sort_order)} UNIQUE 는 V6 에서 DEFERRABLE 로 바꿔 두었다. 그래야
     * 1번과 2번을 맞바꾸는 중간 상태를 지나갈 수 있다.
     */
    private void reorderImages(Long postId, List<Long> imageOrder) {
        List<PostImageEntity> images = postImages.findByPostIdOrderBySortOrder(postId);
        Map<Long, PostImageEntity> byId = new LinkedHashMap<>();
        for (PostImageEntity image : images) {
            byId.put(image.getPostImageId(), image);
        }

        if (imageOrder.size() != images.size() || !byId.keySet().containsAll(imageOrder)
                || new LinkedHashSet<>(imageOrder).size() != imageOrder.size()) {
            throw new ApiException(ErrorCode.COMMON_422, Map.of(
                    "field", "imageOrder",
                    "expectedCount", images.size(),
                    "actualCount", imageOrder.size()));
        }

        for (int i = 0; i < imageOrder.size(); i++) {
            byId.get(imageOrder.get(i)).reorderTo(i + 1);
        }
        postImages.saveAll(byId.values());
    }
}

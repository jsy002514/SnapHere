package com.ssafy.snaphere.domain.post.service;

import com.ssafy.snaphere.domain.follow.repository.FollowRepository;
import com.ssafy.snaphere.domain.heatmap.service.HeatmapAggregationService;
import com.ssafy.snaphere.domain.notification.entity.NotificationTargetType;
import com.ssafy.snaphere.domain.notification.entity.NotificationType;
import com.ssafy.snaphere.domain.notification.service.NotificationService;
import com.ssafy.snaphere.domain.media.dto.MediaDtos.*;
import com.ssafy.snaphere.domain.media.service.MediaStorage;
import com.ssafy.snaphere.domain.media.service.MediaValidator;
import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.place.repository.PlaceWriteRepository;
import com.ssafy.snaphere.domain.post.dto.PostDtos.*;
import com.ssafy.snaphere.domain.post.entity.*;
import com.ssafy.snaphere.domain.post.repository.*;
import com.ssafy.snaphere.domain.tag.service.TagService;
import com.ssafy.snaphere.domain.user.entity.User;
import com.ssafy.snaphere.domain.user.repository.UserRepository;
import com.ssafy.snaphere.domain.visit.repository.VisitRepository;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import com.ssafy.snaphere.global.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물 등록·조회. 이 서비스의 핵심은 Tier 판정을 서버가 독점한다는 것이다.
 *
 * 등록 흐름
 *   1) 자격 검사 (약관·정지·일일한도·장소별한도)
 *   2) 미디어 중복 검사 (같은 파일 재업로드 차단)
 *   3) ★ Tier 판정 — 좌표·촬영시각·촬영방식으로 서버가 결정. 요청의 tier 는 읽지 않는다
 *   4) 게시물 INSERT (geom 은 nativeQuery)
 *   5) 미디어·태그 저장
 *   6) 방문 기록 (Tier 가 인정하는 경우만)
 *   7) 카운터 증가
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostWriteRepository postWriteRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostLikeRepository postLikeRepository;
    private final PlaceRepository placeRepository;
    private final PlaceWriteRepository placeWriteRepository;
    private final UserRepository userRepository;
    private final VisitRepository visitRepository;
    private final FollowRepository followRepository;
    private final TagService tagService;
    private final MediaStorage mediaStorage;
    private final MediaValidator mediaValidator;
    private final NotificationService notificationService;
    private final HeatmapAggregationService heatmapAggregationService;

    @Value("${app.post.daily-limit}")            private int dailyLimit;
    @Value("${app.post.per-place-daily-limit}")  private int perPlaceDailyLimit;
    @Value("${app.tier.on-site-window-minutes}") private int onSiteWindowMinutes;
    @Value("${app.tier.location-confirmed-days}") private int locationConfirmedDays;
    @Value("${app.tier.default-verify-radius-m}") private int defaultVerifyRadiusM;

    // ── 1단계: 업로드 URL 발급 ─────────────────────────────────

    @Transactional(readOnly = true)
    public UploadUrlResponse issueUploadUrls(Long userId, UploadUrlRequest req) {
        User user = requireUploadableUser(userId);
        mediaValidator.validateCount(req.files().size());

        List<UploadUrlItem> items = new ArrayList<>();
        for (FileRequest f : req.files()) {
            MediaValidator.Kind kind = mediaValidator.validate(f);
            String key = mediaValidator.newMediaKey(kind, f.fileName());
            MediaStorage.Upload up = mediaStorage.issueUpload(key, f.contentType());
            items.add(new UploadUrlItem(up.uploadUrl(), up.mediaKey(), kind.name(), up.expiresIn()));
        }
        log.info("[UPLOAD-URL] userId={} count={}", user.getId(), items.size());
        return new UploadUrlResponse(items);
    }

    // ── 3단계: 게시물 등록 ─────────────────────────────────────

    @Transactional
    public CreateResponse create(Long userId, CreateRequest req) {
        User user = requireUploadableUser(userId);

        if (isBlank(req.title()) && isBlank(req.content())) {
            throw new BusinessException(ErrorCode.POST_001, "title");
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long usedToday = postRepository.countByUserSince(userId, todayStart);
        if (usedToday >= dailyLimit) throw new BusinessException(ErrorCode.POST_003);

        if (req.placeId() != null
                && postRepository.countByUserAndPlaceSince(userId, req.placeId(), todayStart) >= perPlaceDailyLimit) {
            throw new BusinessException(ErrorCode.POST_004);
        }

        // 미디어 중복 — 같은 파일을 다시 올려 랭킹을 올리는 것을 막는다
        List<MediaRequest> media = req.media() == null ? List.of() : req.media();
        for (MediaRequest m : media) {
            if (m.mediaHash() != null && postMediaRepository.existsByMediaHash(m.mediaHash())) {
                throw new BusinessException(ErrorCode.POST_005, "mediaHash");
            }
        }

        Place place = null;
        if (req.placeId() != null) {
            place = placeRepository.findById(req.placeId())
                    .filter(Place::isActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_001));
        }

        // ★ Tier 판정 — 요청의 어떤 필드도 tier 를 직접 정하지 못한다
        TierEvaluator.Result tierResult = evaluateTier(req, place);

        PostSource source = parseSource(req.source());
        PostCategory category = parseCategory(req.category());
        int areaCode = place != null ? place.getAreaCode() : req.areaCode();

        // 대표 썸네일과 비율 — masonry 레이아웃이 목록 단계에서 높이를 알아야 한다
        MediaRequest first = media.isEmpty() ? null : media.get(0);
        String thumbnailUrl = first == null ? null : mediaStorage.publicUrl(first.mediaKey());
        BigDecimal ratio = ratioOf(first);
        int videoCount = (int) media.stream().filter(m -> "VIDEO".equalsIgnoreCase(m.mediaType())).count();

        Long postId = postWriteRepository.insert(
                userId, req.placeId(), areaCode, category.name(),
                nullIfBlank(req.title()), nullIfBlank(req.content()),
                media.size(), videoCount, thumbnailUrl, ratio,
                req.capturedLat() == null ? null : req.capturedLat().doubleValue(),
                req.capturedLng() == null ? null : req.capturedLng().doubleValue(),
                tierResult.distanceMeters(),
                source.name(), tierResult.tier().name(), req.takenAt());

        List<MediaItem> savedMedia = saveMedia(postId, media);
        List<TagItem> savedTags = tagService.attach(postId, req.tags());

        // 방문 기록 — Tier 가 인정하는 경우만. NO_LOCATION 은 방문이 아니다.
        boolean visitCreated = false;
        if (tierResult.createsVisit() && place != null) {
            visitCreated = visitRepository.record(userId, place.getId(), areaCode, postId,
                    "AUTO_FROM_POST", tierResult.tier().name(), LocalDate.now());
            if (visitCreated) placeWriteRepository.addVisitCount(place.getId(), 1);
        }

        if (place != null) placeWriteRepository.addPostCount(place.getId(), 1);

        userRepository.addPostCount(userId, 1);

        // 지도에 즉시 반영. 폴링 주기(60초)를 기다리면 시연에서 "안 올라간다" 는 오해를 산다.
        if (tierResult.tier().countsForHeatmap()) {
            heatmapAggregationService.refreshAfterUploadAsync();
        }

        // 팔로워에게 새 글 알림. 대량이 될 수 있어 상한을 두고 비동기로 보낸다.
        notifyFollowersAsync(userId, postId, user.getNickname(), thumbnailUrl);

        log.info("[POST-CREATE] postId={} userId={} placeId={} tier={} 근거={} distance={}",
                postId, userId, req.placeId(), tierResult.tier(), tierResult.reason(), tierResult.distanceMeters());

        return new CreateResponse(
                postId, tierResult.tier().name(), tierResult.tier().messageKey(), tierResult.reason(),
                tierResult.countsForRanking(), tierResult.distanceMeters(),
                place == null ? null : new PlaceBrief(place.getId(), place.getTitle(), place.getAreaCode()),
                visitCreated, savedMedia, savedTags,
                new Quota(dailyLimit, (int) usedToday + 1, (int) Math.max(0, dailyLimit - usedToday - 1)),
                LocalDateTime.now());
    }

    /**
     * Tier 판정에 필요한 거리를 계산해 TierEvaluator 에 넘긴다.
     * 거리 계산을 DB 로 보내지 않는 이유: 이미 장소 좌표를 들고 있고, 한 건이라 Java 계산이 더 싸다.
     */
    private TierEvaluator.Result evaluateTier(CreateRequest req, Place place) {
        Integer distance = null;
        int verifyRadius = defaultVerifyRadiusM;

        boolean hasCoords = req.capturedLat() != null && req.capturedLng() != null;
        if (hasCoords && place != null && place.isHasCoordinateSafe()) {
            double d = GeoUtils.distanceMeters(
                    req.capturedLat().doubleValue(), req.capturedLng().doubleValue(),
                    place.getLat().doubleValue(), place.getLng().doubleValue());
            distance = (int) Math.round(d);
            verifyRadius = place.getVerifyRadiusM();
        }
        return TierEvaluator.evaluate(distance, verifyRadius, parseSource(req.source()),
                req.takenAt(), LocalDateTime.now(), onSiteWindowMinutes, locationConfirmedDays);
    }

    private List<MediaItem> saveMedia(Long postId, List<MediaRequest> media) {
        if (media.isEmpty()) return List.of();
        List<PostMedia> entities = new ArrayList<>();
        for (MediaRequest m : media) {
            MediaType type = "VIDEO".equalsIgnoreCase(m.mediaType()) ? MediaType.VIDEO : MediaType.IMAGE;
            entities.add(PostMedia.create(postId, type, m.mediaKey(),
                    mediaStorage.publicUrl(m.mediaKey()),
                    // 이미지는 원본을 썸네일로 쓴다. 영상은 추출 후 채워진다.
                    type == MediaType.IMAGE ? mediaStorage.publicUrl(m.mediaKey()) : null,
                    m.mediaHash(), m.width(), m.height(), m.durationSec(), m.fileSize(), m.sortOrder()));
        }
        return postMediaRepository.saveAll(entities).stream().map(MediaItem::from).toList();
    }

    // ── 조회 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ListItem> list(Integer areaCode, Long placeId, String category,
                                        String sort, String period, boolean hasMedia,
                                        Long viewerId, PageRequestParam pageParam) {
        Page<Post> page;
        if ("POPULAR".equalsIgnoreCase(sort)) {
            page = postRepository.findPopular(areaCode, hasMedia, periodFrom(period), pageParam.toPageable());
        } else {
            page = postRepository.findLatest(areaCode, placeId, parseCategoryOrNull(category),
                    hasMedia, pageParam.toPageable());
        }
        return PageResponse.from(page, p -> toListItem(p, viewerId, enrich(page.getContent(), viewerId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ListItem> feed(Long userId, PageRequestParam pageParam) {
        Page<Post> page = postRepository.findFollowingFeed(userId, pageParam.toPageable());
        return PageResponse.from(page, p -> toListItem(p, userId, enrich(page.getContent(), userId)));
    }

    @Transactional
    public Detail detail(Long postId, Long viewerId) {
        Post post = postRepository.findById(postId)
                .filter(Post::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_002));

        postWriteRepository.increaseViewCount(postId);

        String content = postRepository.findContent(postId).orElse(null);
        List<MediaItem> media = postMediaRepository.findByPostIdOrderBySortOrderAscIdAsc(postId)
                .stream().map(MediaItem::from).toList();
        List<TagItem> tags = tagService.findByPostId(postId);

        User author = userRepository.findById(post.getUserId()).orElse(null);
        Place place = post.getPlaceId() == null ? null
                : placeRepository.findById(post.getPlaceId()).orElse(null);

        boolean isLiked = viewerId != null && postLikeRepository.exists(postId, viewerId);
        boolean isFollowing = viewerId != null && author != null
                && !viewerId.equals(author.getId())
                && followRepository.exists(viewerId, author.getId());

        return new Detail(
                post.getId(), post.getCategory().name(), post.getTitle(), content,
                media, tags,
                post.getTier().name(), post.getTier().messageKey(), post.getTier().countsForRanking(),
                post.getDistanceM(), post.getLat(), post.getLng(), post.getTakenAt(), post.getSource().name(),
                post.getLikeCount(), post.getCommentCount(), post.getViewCount() + 1, post.getBookmarkCount(),
                isLiked, false, viewerId != null && post.isOwnedBy(viewerId),
                toAuthor(author, isFollowing),
                place == null ? null : new PlaceBrief(place.getId(), place.getTitle(), place.getAreaCode()),
                post.getAreaCode(), post.getCreatedAt(), post.getUpdatedAt());
    }

    // ── 수정 · 삭제 ────────────────────────────────────────────

    @Transactional
    public void update(Long postId, Long userId, UpdateRequest req) {
        Post post = requireOwned(postId, userId);
        if (req.title() != null) post.updateTitle(nullIfBlank(req.title()));
        if (req.content() != null) postWriteRepository.updateContent(postId, nullIfBlank(req.content()));
        if (req.tags() != null) {
            tagService.detachAll(postId);
            tagService.attach(postId, req.tags());
        }
    }

    /**
     * 소프트 삭제. 물리 삭제하지 않는 이유
     *  · 랭킹·통계의 과거 값이 갑자기 바뀌면 원인 추적이 불가능해진다
     *  · InnoDB FULLTEXT 는 하드 삭제한 문서 ID 가 쌓이면 이후 삽입된 행이 검색에서 누락된다
     */
    @Transactional
    public void delete(Long postId, Long userId, boolean isAdmin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_002));
        if (!isAdmin && !post.isOwnedBy(userId)) throw new BusinessException(ErrorCode.COMMON_403);

        post.softDelete();
        tagService.detachAll(postId);
        if (post.getPlaceId() != null) placeWriteRepository.addPostCount(post.getPlaceId(), -1);
        log.info("[POST-DELETE] postId={} by userId={} admin={}", postId, userId, isAdmin);
    }

    // ── 좋아요 ────────────────────────────────────────────────

    @Transactional
    public LikeResponse like(Long postId, Long userId) {
        Post post = requireActive(postId);
        boolean added = postLikeRepository.like(postId, userId);
        if (added) {
            postWriteRepository.addLikeCount(postId, 1);
            if (post.getPlaceId() != null) placeWriteRepository.addLikeCount(post.getPlaceId(), 1);

            User me = userRepository.findById(userId).orElse(null);
            notificationService.notifyAsync(post.getUserId(), userId,
                    NotificationType.POST_LIKE, NotificationTargetType.POST, postId,
                    Map.of("nickname", me == null ? "" : me.getNickname()), post.getThumbnailUrl());
        }
        return new LikeResponse(postId, true, post.getLikeCount() + (added ? 1 : 0));
    }

    @Transactional
    public LikeResponse unlike(Long postId, Long userId) {
        Post post = requireActive(postId);
        boolean removed = postLikeRepository.unlike(postId, userId);
        if (removed) {
            postWriteRepository.addLikeCount(postId, -1);
            if (post.getPlaceId() != null) placeWriteRepository.addLikeCount(post.getPlaceId(), -1);
        }
        return new LikeResponse(postId, false, Math.max(0, post.getLikeCount() - (removed ? 1 : 0)));
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────

    /** 목록 응답을 만들기 전에 작성자·좋아요를 한 번에 조회한다. 게시물당 쿼리를 날리면 N+1 이 된다. */
    private record Enriched(Map<Long, User> authors, Set<Long> likedPostIds, Map<Long, Place> places) {}

    private Enriched enrich(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) return new Enriched(Map.of(), Set.of(), Map.of());

        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> authors = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> authors.put(u.getId(), u));

        List<Long> placeIds = posts.stream().map(Post::getPlaceId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Place> places = new HashMap<>();
        if (!placeIds.isEmpty()) placeRepository.findAllById(placeIds).forEach(p -> places.put(p.getId(), p));

        Set<Long> liked = Set.of();
        if (viewerId != null) {
            liked = new HashSet<>(postLikeRepository.findLikedPostIds(
                    viewerId, posts.stream().map(Post::getId).toList()));
        }
        return new Enriched(authors, liked, places);
    }

    private ListItem toListItem(Post p, Long viewerId, Enriched e) {
        User author = e.authors().get(p.getUserId());
        Place place = p.getPlaceId() == null ? null : e.places().get(p.getPlaceId());
        return new ListItem(
                p.getId(), p.getCategory().name(), p.getTitle(),
                p.getThumbnailUrl(), p.getThumbnailRatio(),
                p.getMediaCount(), p.getVideoCount(),
                p.getTier().name(), p.getTier().messageKey(),
                p.getLikeCount(), p.getCommentCount(), p.getViewCount(),
                e.likedPostIds().contains(p.getId()),
                toAuthor(author, false),
                place == null ? null : new PlaceBrief(place.getId(), place.getTitle(), place.getAreaCode()),
                p.getAreaCode(), p.getCreatedAt());
    }

    private static Author toAuthor(User u, boolean isFollowing) {
        if (u == null) return null;
        return new Author(u.getId(), u.getNickname(), u.getProfileImageUrl(),
                u.getGrade() == null ? null : u.getGrade().name(), isFollowing);
    }

    private User requireUploadableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_401));
        if (user.isWithdrawn()) throw new BusinessException(ErrorCode.USER_004);
        if (!user.isActive()) throw new BusinessException(ErrorCode.USER_003);
        if (!user.hasAgreedTerms()) throw new BusinessException(ErrorCode.USER_002);
        if (user.isUploadBlocked()) throw new BusinessException(ErrorCode.POST_006);
        return user;
    }

    private Post requireActive(Long postId) {
        return postRepository.findById(postId).filter(Post::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_002));
    }

    private Post requireOwned(Long postId, Long userId) {
        Post post = requireActive(postId);
        if (!post.isOwnedBy(userId)) throw new BusinessException(ErrorCode.COMMON_403);
        return post;
    }

    /** 대표 미디어의 가로/세로 비율. 0 나눗셈과 비정상 값을 막는다. */
    static BigDecimal ratioOf(MediaRequest m) {
        if (m == null || m.width() == null || m.height() == null
                || m.width() <= 0 || m.height() <= 0) return null;
        BigDecimal r = BigDecimal.valueOf(m.width())
                .divide(BigDecimal.valueOf(m.height()), 3, RoundingMode.HALF_UP);
        // DECIMAL(5,3) 범위. 극단적인 파노라마도 저장은 되게 상한을 둔다.
        if (r.compareTo(new BigDecimal("99.999")) > 0) return new BigDecimal("99.999");
        if (r.compareTo(new BigDecimal("0.001")) < 0) return new BigDecimal("0.001");
        return r;
    }

    static LocalDateTime periodFrom(String period) {
        if (period == null) return null;
        LocalDateTime now = LocalDateTime.now();
        return switch (period.trim().toUpperCase()) {
            case "DAY" -> now.minusDays(1);
            case "WEEK" -> now.minusDays(7);
            case "MONTH" -> now.minusDays(30);
            default -> null;
        };
    }

    private static PostSource parseSource(String raw) {
        if (raw == null) return PostSource.NONE;
        try { return PostSource.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.COMMON_400, "source"); }
    }

    private static PostCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return PostCategory.PHOTO;
        try { return PostCategory.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.COMMON_400, "category"); }
    }

    private static PostCategory parseCategoryOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseCategory(raw);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /**
     * 팔로워에게 새 글 알림.
     * 팔로워가 수천 명이면 알림 INSERT 가 그만큼 늘어나므로 상한을 둔다.
     * 인기 사용자의 업로드 때문에 업로드 API 가 느려지는 것을 막는 장치다.
     */
    private static final int FOLLOWER_NOTIFY_LIMIT = 500;

    private void notifyFollowersAsync(Long authorId, Long postId, String nickname, String thumbnailUrl) {
        List<Long> followers = followRepository.findFollowerIds(authorId, FOLLOWER_NOTIFY_LIMIT, 0);
        for (Long f : followers) {
            notificationService.notifyAsync(f, authorId,
                    NotificationType.FOLLOWEE_POST, NotificationTargetType.POST, postId,
                    Map.of("nickname", nickname == null ? "" : nickname), thumbnailUrl);
        }
    }

    private static String nullIfBlank(String s) { return isBlank(s) ? null : s.trim(); }
}

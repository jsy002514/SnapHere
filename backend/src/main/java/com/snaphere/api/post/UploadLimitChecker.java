package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.dto.PostImageRequest;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.report.UploadSuspensionReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 게시글 등록 전 업로드 제한 검사. (PST-029 ~ PST-032)
 *
 * <p>순서가 있다. 업로드 정지 → 하루 한도 → 장소별 한도 → 중복 이미지. 정지된 사용자에게
 * "한도 초과"라고 알려 주면 왜 막혔는지 잘못 이해한다.
 */
@Component
public class UploadLimitChecker {

    private final PostRepository posts;
    private final PostImageRepository postImages;
    private final UploadSuspensionReader suspensions;

    public UploadLimitChecker(PostRepository posts,
                              PostImageRepository postImages,
                              UploadSuspensionReader suspensions) {
        this.posts = posts;
        this.postImages = postImages;
        this.suspensions = suspensions;
    }

    @Transactional(readOnly = true)
    public void check(UUID userId, long placeId, List<PostImageRequest> images, OffsetDateTime now) {
        requireNotSuspended(userId, now);
        requireWithinDailyLimit(userId, now);
        requireWithinPlaceDailyLimit(userId, placeId, now);
        requireNoDuplicateImage(userId, images);
    }

    /** 신고 누적 정지. 남은 시간을 함께 줘서 앱이 "n시간 후 가능"으로 보여줄 수 있게 한다. (PST-032) */
    private void requireNotSuspended(UUID userId, OffsetDateTime now) {
        suspensions.suspendedUntil(userId)
                .filter(until -> until.isAfter(now))
                .ifPresent(until -> {
                    int retryAfter = (int) Math.max(1, Duration.between(now, until).getSeconds());
                    throw new ApiException(ErrorCode.POST_UPLOAD_SUSPENDED,
                            Map.of("suspendedUntil", until.toString()), retryAfter);
                });
    }

    /** 하루 30개. (PST-029) */
    private void requireWithinDailyLimit(UUID userId, OffsetDateTime now) {
        OffsetDateTime from = UploadLimitPolicy.startOfDay(now);
        long today = posts.countByUserIdAndStatusAndCreatedAtGreaterThanEqual(
                userId, PostStatus.ACTIVE, from);
        if (UploadLimitPolicy.exceedsDailyLimit(today)) {
            throw new ApiException(ErrorCode.POST_DAILY_LIMIT,
                    Map.of("limit", UploadLimitPolicy.DAILY_POST_LIMIT, "used", today),
                    UploadLimitPolicy.secondsUntilNextDay(now));
        }
    }

    /** 같은 장소 하루 3개. (PST-030) */
    private void requireWithinPlaceDailyLimit(UUID userId, long placeId, OffsetDateTime now) {
        OffsetDateTime from = UploadLimitPolicy.startOfDay(now);
        long todayAtPlace = posts.countByUserIdAndPlaceIdAndStatusAndCreatedAtGreaterThanEqual(
                userId, placeId, PostStatus.ACTIVE, from);
        if (UploadLimitPolicy.exceedsPlaceDailyLimit(todayAtPlace)) {
            throw new ApiException(ErrorCode.POST_PLACE_DAILY_LIMIT,
                    Map.of("limit", UploadLimitPolicy.PLACE_DAILY_POST_LIMIT,
                            "used", todayAtPlace, "placeId", placeId),
                    UploadLimitPolicy.secondsUntilNextDay(now));
        }
    }

    /**
     * 본인 계정 안 중복 이미지. (PST-031)
     *
     * <p>해시는 클라이언트가 보낸 값으로 미리 걸러 낸다. 서버가 이 시점에 원본을 내려받아
     * 계산하면 사진 4장에 최대 40MB 를 등록 응답 앞에 끼워 넣게 되고, 후처리를 응답과 분리하라는
     * PST-019 와 정면으로 어긋난다. 값을 속여 보내면 후처리 배치가 실제 해시로 덮어쓰므로
     * 중복은 그때 드러난다 — 즉 이 검사는 정확성이 아니라 빠른 되돌림을 위한 것이다.
     *
     * <p>해시를 보내지 않은 사진은 검사하지 않는다. 후처리 전까지는 판정할 근거가 없다.
     */
    private void requireNoDuplicateImage(UUID userId, List<PostImageRequest> images) {
        for (PostImageRequest image : images) {
            String hash = image.imageHash();
            if (hash == null || hash.isBlank()) {
                continue;
            }
            if (postImages.countSameHashOwnedBy(userId, hash) > 0) {
                throw new ApiException(ErrorCode.POST_DUPLICATE_IMAGE,
                        Map.of("imageKey", image.imageKey()));
            }
        }
    }
}

package com.ssafy.snaphere.domain.post.dto;

import com.ssafy.snaphere.domain.post.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PostDtos {

    private PostDtos() {}

    // ───────── 요청 ─────────

    @Schema(name = "PostMediaRequest")
    public record MediaRequest(
            @NotBlank String mediaKey,
            @NotBlank String mediaType,
            Integer width,
            Integer height,
            Integer durationSec,
            Long fileSize,
            @Schema(description = "SHA-256. 같은 파일 재업로드 차단에 쓴다") String mediaHash,
            int sortOrder
    ) {}

    @Schema(name = "PostTagRequest")
    public record TagRequest(@NotBlank @Size(max = 50) String name, String source) {}

    @Schema(name = "PostCreateRequest",
            description = "⚠️ tier 를 보내지 않는다. 서버가 좌표·시각·촬영방식으로 판정한다.")
    public record CreateRequest(
            Long placeId,
            @NotNull Integer areaCode,
            String category,
            @Size(max = 100) String title,
            String content,
            @Valid @Size(max = 10) List<MediaRequest> media,
            @NotBlank String source,
            BigDecimal capturedLat,
            BigDecimal capturedLng,
            LocalDateTime takenAt,
            @Valid @Size(max = 20) List<TagRequest> tags
    ) {}

    @Schema(name = "PostUpdateRequest")
    public record UpdateRequest(
            @Size(max = 100) String title,
            String content,
            @Valid @Size(max = 20) List<TagRequest> tags
    ) {}

    // ───────── 응답 ─────────

    @Schema(name = "PostMediaItem")
    public record MediaItem(Long mediaId, String mediaType, String mediaUrl, String thumbnailUrl,
                            String processStatus, Integer width, Integer height, Integer durationSec) {
        public static MediaItem from(PostMedia m) {
            return new MediaItem(m.getId(), m.getMediaType().name(), m.getMediaUrl(), m.getThumbnailUrl(),
                    m.getProcessStatus().name(), m.getWidth(), m.getHeight(), m.getDurationSec());
        }
    }

    @Schema(name = "PostTagItem")
    public record TagItem(Long tagId, String name, String source) {}

    @Schema(name = "PostAuthor")
    public record Author(Long userId, String nickname, String profileImageUrl, String grade,
                         boolean isFollowing) {}

    @Schema(name = "PostPlaceBrief")
    public record PlaceBrief(Long placeId, String title, Integer areaCode) {}

    @Schema(name = "PostListItem", description = "masonry 레이아웃 때문에 thumbnailRatio 가 목록에 포함된다")
    public record ListItem(
            Long postId, String category, String title,
            String thumbnailUrl,
            @Schema(description = "대표 미디어 가로/세로 비율. 이미지를 받기 전에 높이를 계산할 수 있게 한다")
            BigDecimal thumbnailRatio,
            int mediaCount, int videoCount,
            String tier,
            @Schema(description = "프론트가 다국어 문구로 매핑하는 키. 서버는 완성된 문장을 만들지 않는다")
            String tierMessageKey,
            int likeCount, int commentCount, int viewCount,
            boolean isLiked,
            Author author,
            PlaceBrief place,
            Integer areaCode,
            LocalDateTime createdAt
    ) {}

    @Schema(name = "PostDetail")
    public record Detail(
            Long postId, String category, String title, String content,
            List<MediaItem> media, List<TagItem> tags,
            String tier, String tierMessageKey, boolean countsForRanking,
            Integer distanceMeters,
            BigDecimal lat, BigDecimal lng, LocalDateTime takenAt, String source,
            int likeCount, int commentCount, int viewCount, int bookmarkCount,
            boolean isLiked, boolean isBookmarked, boolean isMine,
            Author author, PlaceBrief place, Integer areaCode,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {}

    @Schema(name = "PostQuota")
    public record Quota(int dailyLimit, int used, int remaining) {}

    @Schema(name = "PostCreateResponse")
    public record CreateResponse(
            Long postId,
            String tier,
            @Schema(description = "프론트가 배지 문구를 매핑하는 키") String tierMessageKey,
            @Schema(description = "판정 근거. 사용자에게 \"왜 위치 미확인인지\" 설명할 수 있어야 한다")
            String tierReason,
            boolean countsForRanking,
            Integer distanceMeters,
            PlaceBrief place,
            boolean visitCreated,
            List<MediaItem> media,
            List<TagItem> tags,
            Quota quota,
            LocalDateTime createdAt
    ) {}

    @Schema(name = "TagSuggestion")
    public record TagSuggestion(String name, String source, Long placeId,
                                java.time.LocalDate eventStartDate, java.time.LocalDate eventEndDate) {}

    @Schema(name = "TagSuggestionResponse")
    public record TagSuggestionResponse(List<TagSuggestion> suggestions) {}

    @Schema(name = "LikeResponse")
    public record LikeResponse(Long postId, boolean liked, int likeCount) {}
}

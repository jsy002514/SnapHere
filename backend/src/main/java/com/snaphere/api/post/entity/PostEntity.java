package com.snaphere.api.post.entity;

import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 게시글. (PST-016)
 *
 * <p>사진과 장소는 필수다 (PST-001, PST-002). 신뢰등급은 클라이언트가 보낸 값을 쓰지 않고
 * 서버가 {@code TierPolicy} 로 판정한다 (PST-022).
 *
 * <p>카운터({@code likeCount} 등)는 비정규화한다. 메이슨리 무한 스크롤이 목록마다 COUNT 를
 * 돌리면 버티지 못한다. 값은 좋아요·댓글 쪽에서 증감한다.
 */
@Entity
@Table(name = "posts")
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    /** 진행 중 행사 연결. {@code events} 테이블이 생기면 외래키를 건다. (EVT-018) */
    @Column(name = "event_id")
    private Long eventId;

    /** 클라이언트 값을 믿지 않고 장소에서 역산한다. (PST-018) */
    @Column(name = "area_code", nullable = false)
    private Integer areaCode;

    /** 캡션. 5,000자까지 쓸 수 있고 비워도 게시된다. (PST-003) */
    @Column(name = "content", length = 5000)
    private String content;

    @Column(name = "original_language_code", length = 10)
    private String originalLanguageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 10)
    private TrustTier tier;

    /** 촬영 좌표. 공개 이미지에서는 EXIF 를 지우지만 판정 근거로 여기 남긴다. (PST-020) */
    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "taken_at")
    private OffsetDateTime takenAt;

    /** 판정 입력값이라 null 을 허용한다. null 이면 등급 상향 근거가 없다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private PhotoSource source;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected PostEntity() {
    }

    /**
     * 새 게시글. 등급과 지역 코드는 이미 서버가 정한 값을 받는다 — 호출자가 임의로 넣지 못하게
     * 판정과 역산을 서비스 계층에서 끝내고 여기로 넘긴다.
     */
    public static PostEntity create(UUID userId, Long placeId, Long eventId, int areaCode,
                                    String content, TrustTier tier,
                                    Double lat, Double lng,
                                    OffsetDateTime takenAt, PhotoSource source) {
        PostEntity post = new PostEntity();
        post.userId = userId;
        post.placeId = placeId;
        post.eventId = eventId;
        post.areaCode = areaCode;
        post.content = content;
        post.tier = tier;
        post.lat = lat;
        post.lng = lng;
        post.takenAt = takenAt;
        post.source = source;
        post.status = PostStatus.ACTIVE;
        post.createdAt = post.updatedAt = OffsetDateTime.now();
        return post;
    }

    /** 캡션 수정. 등급과 장소는 수정으로 바뀌지 않는다. (PST-036) */
    public void editContent(String content) {
        this.content = content;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 신고 누적으로 가린다. (PST-045)
     *
     * <p>삭제가 아니다. 작성자에게는 계속 보이고 운영자가 복구할 수 있다 (SYS-017).
     * 이미 삭제된 게시글은 건드리지 않는다 — 가림은 삭제보다 약한 상태라 되돌리는 셈이 된다.
     */
    public boolean blindByReports() {
        if (status != PostStatus.ACTIVE) {
            return false;
        }
        this.status = PostStatus.HIDDEN;
        this.updatedAt = OffsetDateTime.now();
        return true;
    }

    /** 비공개 원본의 후처리가 끝날 때까지 공개 조회에서 숨긴다. (SYS-021) */
    public void beginMediaProcessing() {
        if (status == PostStatus.ACTIVE) {
            status = PostStatus.HIDDEN;
            updatedAt = OffsetDateTime.now();
        }
    }

    /** 미디어 처리 성공 시에만 공개 상태로 전환한다. */
    public void completeMediaProcessing() {
        if (status == PostStatus.HIDDEN) {
            status = PostStatus.ACTIVE;
            updatedAt = OffsetDateTime.now();
        }
    }

    /** 논리 삭제. 행을 지우지 않아 방문 기록·뱃지가 함께 사라지지 않는다. (PST-043) */
    public void softDelete() {
        this.status = PostStatus.DELETED;
        this.deletedAt = OffsetDateTime.now();
        this.updatedAt = this.deletedAt;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public Long getPostId() {
        return postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Integer getAreaCode() {
        return areaCode;
    }

    public String getContent() {
        return content;
    }

    public String getOriginalLanguageCode() {
        return originalLanguageCode;
    }

    public TrustTier getTier() {
        return tier;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public OffsetDateTime getTakenAt() {
        return takenAt;
    }

    public PhotoSource getSource() {
        return source;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public PostStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

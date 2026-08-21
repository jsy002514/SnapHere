package com.ssafy.snaphere.domain.post.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시물 (사진 + 글 통합).
 *
 * ⚠️ 매핑하지 않는 컬럼
 *   · geom (POINT SRID 4326) — 좌표 쓰기는 nativeQuery 로만 (축 순서 사고 방지)
 *   · content (TEXT)        — 장문 컬럼. 목록에 딸려오면 안 되고 Hibernate 의 text 타입 검증도 까다롭다.
 *                             상세에서만 PostRepository.findContent() 로 따로 읽는다.
 *
 * ⚠️ tier 는 서버가 판정한 값만 들어온다. 프론트가 보낸 값을 쓰는 경로를 만들지 말 것.
 */
@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @Column(name = "user_id", nullable = false)  private Long userId;
    @Column(name = "place_id")                   private Long placeId;
    @Column(name = "area_code", nullable = false) private Integer areaCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostCategory category;

    @Column(length = 100) private String title;

    @Column(name = "media_count", nullable = false) private int mediaCount;
    @Column(name = "video_count", nullable = false) private int videoCount;
    @Column(name = "thumbnail_url", length = 500)   private String thumbnailUrl;

    /**
     * 대표 미디어의 가로/세로 비율. masonry(핀터레스트식) 레이아웃 때문에 목록 단계에서 필요하다.
     * 이게 없으면 앱이 이미지를 다 받은 뒤에야 높이를 알아 레이아웃이 튄다.
     */
    @Column(name = "thumbnail_ratio", precision = 5, scale = 3) private BigDecimal thumbnailRatio;

    @Column(name = "has_location", nullable = false) private boolean hasLocation;
    @Column(precision = 10, scale = 7) private BigDecimal lat;
    @Column(precision = 10, scale = 7) private BigDecimal lng;
    @Column(name = "distance_m") private Integer distanceM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostTier tier;

    @Column(name = "taken_at") private LocalDateTime takenAt;

    @Column(name = "view_count", nullable = false)     private int viewCount;
    @Column(name = "like_count", nullable = false)     private int likeCount;
    @Column(name = "comment_count", nullable = false)  private int commentCount;
    @Column(name = "bookmark_count", nullable = false) private int bookmarkCount;
    @Column(name = "report_count", nullable = false)   private int reportCount;

    @Column(name = "popularity_score", nullable = false, precision = 12, scale = 2)
    private BigDecimal popularityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostStatus status;

    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;

    public boolean isActive() { return status == PostStatus.ACTIVE; }

    public boolean isOwnedBy(Long uid) { return uid != null && uid.equals(userId); }

    /** 제목 수정. 본문은 장문 컬럼이라 nativeQuery 로 따로 갱신한다. */
    public void updateTitle(String newTitle) { this.title = newTitle; }

    public void softDelete() { this.status = PostStatus.DELETED; }

    public void blind() { this.status = PostStatus.BLINDED; }
}

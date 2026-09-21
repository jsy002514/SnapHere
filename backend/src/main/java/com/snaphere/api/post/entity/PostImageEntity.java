package com.snaphere.api.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 게시글 사진. 게시글당 1~4장. (PST-001)
 *
 * <p>{@code aspectRatio} 는 메이슨리 카드 높이를 이미지가 도착하기 <em>전에</em> 잡기 위한 값이다
 * (PST-021). 없으면 스크롤이 튄다.
 *
 * <p>{@code thumbnailUrl} 과 {@code imageHash} 는 업로드 응답과 분리된 후처리 배치가 채운다
 * (PST-019, JOB-003). 그래서 생성 시점에는 null 이다.
 */
@Entity
@Table(name = "post_images")
public class PostImageEntity {

    /** 게시글당 최대 장수. (PST-001) */
    public static final int MAX_PER_POST = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long postImageId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** S3 오브젝트 키. 공개 URL 은 이 키로 조립한다 — 전체 URL 을 저장하면 버킷 이전이 막힌다. */
    @Column(name = "image_key", nullable = false, length = 1024)
    private String imageKey;

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl;

    @Column(name = "aspect_ratio", precision = 6, scale = 4)
    private BigDecimal aspectRatio;

    /** 1~4. 사용자가 정한 순서 그대로 보여준다. */
    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    /** 본인 계정 안 중복 업로드 차단용. (PST-031) */
    @Column(name = "image_hash", length = 64)
    private String imageHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PostImageEntity() {
    }

    public static PostImageEntity create(Long postId, String imageKey, int sortOrder,
                                         BigDecimal aspectRatio, String imageHash) {
        if (sortOrder < 1 || sortOrder > MAX_PER_POST) {
            throw new IllegalArgumentException("사진 순서는 1~" + MAX_PER_POST + " 이다: " + sortOrder);
        }
        PostImageEntity image = new PostImageEntity();
        image.postId = postId;
        image.imageKey = imageKey;
        image.sortOrder = (short) sortOrder;
        image.aspectRatio = aspectRatio;
        image.imageHash = imageHash;
        image.createdAt = OffsetDateTime.now();
        return image;
    }

    /**
     * 사진 순서 변경. (PST-036)
     *
     * <p>{@code (post_id, sort_order)} UNIQUE 라 한 장씩 옮기면 중간에 값이 겹친다. 호출자가
     * 전체 순서를 한 번에 정하고, 저장 전에 모든 사진의 값을 새로 매겨야 한다.
     */
    public void reorderTo(int sortOrder) {
        if (sortOrder < 1 || sortOrder > MAX_PER_POST) {
            throw new IllegalArgumentException("사진 순서는 1~" + MAX_PER_POST + " 이다: " + sortOrder);
        }
        this.sortOrder = (short) sortOrder;
    }

    /** 후처리 배치가 채운다. (PST-019) */
    public void completePostProcessing(String publicImageKey, String thumbnailUrl,
                                       String imageHash, BigDecimal aspectRatio) {
        this.imageKey = publicImageKey;
        this.thumbnailUrl = thumbnailUrl;
        this.imageHash = imageHash;
        if (aspectRatio != null) {
            this.aspectRatio = aspectRatio;
        }
    }

    public Long getPostImageId() {
        return postImageId;
    }

    public Long getPostId() {
        return postId;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public BigDecimal getAspectRatio() {
        return aspectRatio;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public String getImageHash() {
        return imageHash;
    }
}

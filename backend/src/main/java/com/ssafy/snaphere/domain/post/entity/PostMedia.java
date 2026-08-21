package com.ssafy.snaphere.domain.post.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "post_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Long id;

    @Column(name = "post_id", nullable = false) private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 30)
    private MediaType mediaType;

    @Column(name = "media_key", nullable = false, length = 500) private String mediaKey;
    @Column(name = "media_url", nullable = false, length = 500) private String mediaUrl;
    @Column(name = "thumbnail_url", length = 500) private String thumbnailUrl;

    /** SHA-256. 같은 파일을 다시 올리는 것을 막는다(POST_005). */
    @Column(name = "media_hash", length = 64) private String mediaHash;

    private Integer width;
    private Integer height;
    @Column(name = "duration_sec") private Integer durationSec;
    @Column(name = "file_size")    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", nullable = false, length = 30)
    private ProcessStatus processStatus;

    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;

    public static PostMedia create(Long postId, MediaType type, String mediaKey, String mediaUrl,
                                   String thumbnailUrl, String mediaHash,
                                   Integer width, Integer height, Integer durationSec, Long fileSize,
                                   int sortOrder) {
        PostMedia m = new PostMedia();
        m.postId = postId;
        m.mediaType = type;
        m.mediaKey = mediaKey;
        m.mediaUrl = mediaUrl;
        m.thumbnailUrl = thumbnailUrl;
        m.mediaHash = mediaHash;
        m.width = width;
        m.height = height;
        m.durationSec = durationSec;
        m.fileSize = fileSize;
        m.sortOrder = sortOrder;
        // 이미지는 바로 사용 가능. 영상은 썸네일 추출이 끝나야 READY 가 된다.
        m.processStatus = type == MediaType.VIDEO ? ProcessStatus.PROCESSING : ProcessStatus.READY;
        return m;
    }

    public void markReady(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        this.processStatus = ProcessStatus.READY;
    }

    public void markFailed() { this.processStatus = ProcessStatus.FAILED; }
}

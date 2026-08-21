package com.ssafy.snaphere.domain.tag.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    /** # 을 제외한 순수 문자열. 소문자로 정규화해 저장한다(같은 태그가 여러 개 생기지 않게). */
    @Column(nullable = false, length = 50) private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 30)
    private TagType tagType;

    @Column(name = "ref_id")    private Long refId;
    @Column(name = "usage_count", nullable = false) private int usageCount;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;

    public static Tag of(String name, TagType type, Long refId) {
        Tag t = new Tag();
        t.name = name;
        t.tagType = type;
        t.refId = refId;
        return t;
    }
}

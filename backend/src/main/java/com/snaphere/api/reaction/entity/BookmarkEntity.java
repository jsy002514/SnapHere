package com.snaphere.api.reaction.entity;

import com.snaphere.api.reaction.BookmarkTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 저장 한 건. (CMU-023, PLC-015) */
@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {

    @EmbeddedId
    private BookmarkId id;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected BookmarkEntity() {
    }

    public static BookmarkEntity of(UUID userId, BookmarkTargetType targetType, Long targetId) {
        BookmarkEntity bookmark = new BookmarkEntity();
        bookmark.id = new BookmarkId(userId, targetType, targetId);
        bookmark.createdAt = OffsetDateTime.now();
        return bookmark;
    }

    public BookmarkId getId() {
        return id;
    }

    /** 저장 시각. 해제하면 응답에서 null 이 된다 (명세 BookmarkResult.savedAt). */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

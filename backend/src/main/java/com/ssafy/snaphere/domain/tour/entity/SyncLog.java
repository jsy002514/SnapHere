package com.ssafy.snaphere.domain.tour.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배치 실행 기록. 조합(지역 × 콘텐츠유형) 하나가 한 행이다.
 * 실패한 조합만 골라 재실행할 수 있어야 하므로 target 을 남긴다.
 *
 * ⚠️ enum 컬럼은 length = 30 을 명시한다 (기본값 255 면 ddl-auto: validate 가 막는다).
 */
@Getter
@Entity
@Table(name = "sync_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_type", nullable = false, length = 30)
    private SyncType syncType;

    @Column(length = 100)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SyncStatus status;

    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "updated_count", nullable = false) private int updatedCount;
    @Column(name = "failed_count",  nullable = false) private int failedCount;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static SyncLog start(SyncType type, String target) {
        SyncLog log = new SyncLog();
        log.syncType = type;
        log.target = target;
        log.status = SyncStatus.RUNNING;
        log.startedAt = LocalDateTime.now();
        return log;
    }

    public void succeed(int created, int updated) {
        succeed(created, updated, null);
    }

    /** @param note 예: "fetched=595 skipped=0" — 집계가 이상할 때 원인 추적용 */
    public void succeed(int created, int updated, String note) {
        this.createdCount = created;
        this.updatedCount = updated;
        this.status = SyncStatus.SUCCESS;
        this.message = truncate(note);
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.failedCount++;
        this.status = SyncStatus.FAILED;
        this.message = truncate(reason);
        this.finishedAt = LocalDateTime.now();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}

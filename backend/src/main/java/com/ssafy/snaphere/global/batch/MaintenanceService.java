package com.ssafy.snaphere.global.batch;

import com.ssafy.snaphere.domain.tour.entity.SyncLog;
import com.ssafy.snaphere.domain.tour.entity.SyncType;
import com.ssafy.snaphere.domain.tour.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유지보수 배치.
 *
 * 이 클래스가 존재하는 이유: 우리는 성능을 위해 카운터를 비정규화했다.
 * 동시성·예외·수동 SQL 때문에 카운터는 반드시 어긋난다. 매일 한 번 실제 값으로 되돌려야
 * "좋아요 5개인데 3이라고 표시" 같은 문제가 누적되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final JdbcTemplate jdbcTemplate;
    private final SyncLogRepository syncLogRepository;

    @Value("${app.account.purge-grace-days}") private int purgeGraceDays;

    // ── 1. 카운터 보정 ────────────────────────────────────────

    @Transactional
    public int fixCounters() {
        int fixed = 0;

        // posts.like_count / comment_count
        fixed += jdbcTemplate.update("""
                UPDATE posts p
                LEFT JOIN (SELECT post_id, COUNT(*) c FROM post_likes GROUP BY post_id) l ON l.post_id = p.post_id
                SET p.like_count = COALESCE(l.c, 0)
                WHERE p.like_count <> COALESCE(l.c, 0)
                """);
        fixed += jdbcTemplate.update("""
                UPDATE posts p
                LEFT JOIN (SELECT post_id, COUNT(*) c FROM comments WHERE status = 'ACTIVE' GROUP BY post_id) c
                       ON c.post_id = p.post_id
                SET p.comment_count = COALESCE(c.c, 0)
                WHERE p.comment_count <> COALESCE(c.c, 0)
                """);

        // users.follower_count / following_count / post_count
        fixed += jdbcTemplate.update("""
                UPDATE users u
                LEFT JOIN (SELECT following_id id, COUNT(*) c FROM follows GROUP BY following_id) f ON f.id = u.user_id
                SET u.follower_count = COALESCE(f.c, 0)
                WHERE u.follower_count <> COALESCE(f.c, 0)
                """);
        fixed += jdbcTemplate.update("""
                UPDATE users u
                LEFT JOIN (SELECT follower_id id, COUNT(*) c FROM follows GROUP BY follower_id) f ON f.id = u.user_id
                SET u.following_count = COALESCE(f.c, 0)
                WHERE u.following_count <> COALESCE(f.c, 0)
                """);
        fixed += jdbcTemplate.update("""
                UPDATE users u
                LEFT JOIN (SELECT user_id, COUNT(*) c FROM posts WHERE status = 'ACTIVE' GROUP BY user_id) p
                       ON p.user_id = u.user_id
                SET u.post_count = COALESCE(p.c, 0)
                WHERE u.post_count <> COALESCE(p.c, 0)
                """);
        fixed += jdbcTemplate.update("""
                UPDATE users u
                LEFT JOIN (SELECT user_id, COUNT(DISTINCT place_id) c FROM visits GROUP BY user_id) v
                       ON v.user_id = u.user_id
                SET u.visit_count = COALESCE(v.c, 0)
                WHERE u.visit_count <> COALESCE(v.c, 0)
                """);

        // places.post_count / visit_count / bookmark_count
        fixed += jdbcTemplate.update("""
                UPDATE places pl
                LEFT JOIN (SELECT place_id, COUNT(*) c FROM posts WHERE status = 'ACTIVE' AND place_id IS NOT NULL
                           GROUP BY place_id) p ON p.place_id = pl.place_id
                SET pl.post_count = COALESCE(p.c, 0)
                WHERE pl.post_count <> COALESCE(p.c, 0)
                """);
        fixed += jdbcTemplate.update("""
                UPDATE places pl
                LEFT JOIN (SELECT place_id, COUNT(DISTINCT user_id) c FROM visits GROUP BY place_id) v
                       ON v.place_id = pl.place_id
                SET pl.visit_count = COALESCE(v.c, 0)
                WHERE pl.visit_count <> COALESCE(v.c, 0)
                """);
        fixed += jdbcTemplate.update("""
                UPDATE places pl
                LEFT JOIN (SELECT target_id, COUNT(*) c FROM bookmarks WHERE target_type = 'PLACE'
                           GROUP BY target_id) b ON b.target_id = pl.place_id
                SET pl.bookmark_count = COALESCE(b.c, 0)
                WHERE pl.bookmark_count <> COALESCE(b.c, 0)
                """);

        // tags.usage_count
        fixed += jdbcTemplate.update("""
                UPDATE tags t
                LEFT JOIN (SELECT tag_id, COUNT(*) c FROM post_tags GROUP BY tag_id) pt ON pt.tag_id = t.tag_id
                SET t.usage_count = COALESCE(pt.c, 0)
                WHERE t.usage_count <> COALESCE(pt.c, 0)
                """);
        return fixed;
    }

    // ── 2. 게시물 인기 점수 ───────────────────────────────────

    /**
     * 기간별 인기 정렬용 사전 계산 점수.
     * 시간 감쇠를 넣어 오래된 글이 영원히 상위에 남는 것을 막는다(반감기 약 3일).
     */
    @Transactional
    public int refreshPopularityScore() {
        return jdbcTemplate.update("""
                UPDATE posts
                SET popularity_score = ROUND(
                        (like_count * 1.0 + comment_count * 2.0 + view_count * 0.05
                         + CASE tier WHEN 'ON_SITE' THEN 5 WHEN 'LOCATION_CONFIRMED' THEN 3 ELSE 0 END)
                        / POW(2, LEAST(30, TIMESTAMPDIFF(HOUR, created_at, NOW(6)) / 72))
                    , 2)
                WHERE status = 'ACTIVE'
                """);
    }

    /** 사용자 인기 지수와 등급. 등급 구간은 Grade enum 과 맞춰야 한다. */
    @Transactional
    public int refreshUserPopularity() {
        jdbcTemplate.update("""
                UPDATE users u
                LEFT JOIN (
                    SELECT p.user_id,
                           SUM(p.like_count) * 2 + SUM(p.comment_count) * 3 + COUNT(*) * 5 AS score
                    FROM posts p WHERE p.status = 'ACTIVE' AND p.tier <> 'NO_LOCATION'
                    GROUP BY p.user_id
                ) s ON s.user_id = u.user_id
                SET u.popularity_score = COALESCE(s.score, 0) + u.follower_count * 10
                """);
        return jdbcTemplate.update("""
                UPDATE users
                SET grade = CASE
                        WHEN popularity_score >= 20000 THEN 'LEGEND'
                        WHEN popularity_score >= 5000  THEN 'FOREST'
                        WHEN popularity_score >= 1500  THEN 'TREE'
                        WHEN popularity_score >= 300   THEN 'SPROUT'
                        ELSE 'SEED' END
                """);
    }

    // ── 3. 신고 누적 자동 블라인드 ────────────────────────────

    /** 신고 3회 누적이면 자동 숨김. 운영자 검토 전까지 노출을 막는다. */
    @Transactional
    public int autoBlindReported() {
        int posts = jdbcTemplate.update("""
                UPDATE posts p
                JOIN (SELECT target_id, COUNT(*) c FROM reports
                      WHERE target_type = 'POST' AND status = 'PENDING' GROUP BY target_id) r
                  ON r.target_id = p.post_id
                SET p.status = 'BLINDED', p.report_count = r.c
                WHERE r.c >= 3 AND p.status = 'ACTIVE'
                """);
        int comments = jdbcTemplate.update("""
                UPDATE comments c
                JOIN (SELECT target_id, COUNT(*) cnt FROM reports
                      WHERE target_type = 'COMMENT' AND status = 'PENDING' GROUP BY target_id) r
                  ON r.target_id = c.comment_id
                SET c.status = 'BLINDED', c.report_count = r.cnt
                WHERE r.cnt >= 3 AND c.status = 'ACTIVE'
                """);
        return posts + comments;
    }

    // ── 4. 계정 완전 파기 ─────────────────────────────────────

    /**
     * 유예 기간이 지난 탈퇴 계정을 파기한다.
     *
     * ⚠️ 개인 식별 정보는 탈퇴 시점에 이미 지웠다(User.withdraw). 여기서는 계정 행과 콘텐츠를 정리한다.
     * ⚠️ FK 때문에 자식 행부터 지운다. 순서를 바꾸면 제약 위반으로 전부 롤백된다.
     */
    @Transactional
    public int purgeWithdrawnAccounts() {
        var ids = jdbcTemplate.queryForList("""
                SELECT user_id FROM users
                WHERE status = 'WITHDRAWN' AND purge_scheduled_at IS NOT NULL AND purge_scheduled_at <= NOW(6)
                LIMIT 200
                """, Long.class);
        if (ids.isEmpty()) return 0;

        String ph = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = ids.toArray();

        jdbcTemplate.update("DELETE FROM user_devices WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("DELETE FROM notifications WHERE recipient_id IN (" + ph + ") OR actor_id IN (" + ph + ")",
                concat(args, args));
        jdbcTemplate.update("DELETE FROM comment_likes WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("DELETE FROM post_likes WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("DELETE FROM bookmarks WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("DELETE FROM follows WHERE follower_id IN (" + ph + ") OR following_id IN (" + ph + ")",
                concat(args, args));
        jdbcTemplate.update("DELETE FROM visits WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("DELETE FROM reports WHERE reporter_user_id IN (" + ph + ")", args);
        // 게시물은 논리 삭제로 남긴다. 물리 삭제하면 랭킹·통계의 과거 값이 흔들린다.
        jdbcTemplate.update("UPDATE posts SET status = 'DELETED' WHERE user_id IN (" + ph + ")", args);
        jdbcTemplate.update("UPDATE comments SET status = 'DELETED' WHERE user_id IN (" + ph + ")", args);
        // 사용자가 만든 장소는 소유자만 끊고 남긴다. 다른 사람의 게시물이 붙어 있을 수 있다.
        jdbcTemplate.update("UPDATE places SET created_by_user_id = NULL WHERE created_by_user_id IN (" + ph + ")", args);

        int purged = jdbcTemplate.update("DELETE FROM users WHERE user_id IN (" + ph + ")", args);
        log.info("[PURGE] 계정 {}건 완전 파기", purged);
        return purged;
    }

    // ── 5. FULLTEXT 최적화 ────────────────────────────────────

    /**
     * InnoDB FULLTEXT 는 하드 삭제한 문서 ID 가 쌓이면 이후 삽입된 행이 검색에서 누락된다
     * (2026-08-22 실측 확인). 계정 파기 배치가 하드 삭제를 하므로 주기적으로 정리한다.
     */
    public void optimizeFulltext() {
        try {
            jdbcTemplate.execute("SET GLOBAL innodb_optimize_fulltext_only = ON");
            jdbcTemplate.execute("OPTIMIZE TABLE places");
            jdbcTemplate.execute("OPTIMIZE TABLE posts");
            jdbcTemplate.execute("SET GLOBAL innodb_optimize_fulltext_only = OFF");
        } catch (Exception e) {
            // SUPER 권한이 없으면 SET GLOBAL 이 실패한다. 검색이 즉시 깨지는 것은 아니므로 경고만 남긴다.
            log.warn("FULLTEXT 최적화 실패(권한 문제일 수 있음): {}", e.getMessage());
        }
    }

    // ── 통합 실행 ─────────────────────────────────────────────

    public void runNightly() {
        SyncLog syncLog = syncLogRepository.save(SyncLog.start(SyncType.COUNTER_FIX, "nightly"));
        try {
            int counters = fixCounters();
            int posts = refreshPopularityScore();
            int users = refreshUserPopularity();
            int blinded = autoBlindReported();
            int purged = purgeWithdrawnAccounts();
            optimizeFulltext();

            syncLog.succeed(counters, posts,
                    "카운터보정=%d 게시물점수=%d 사용자등급=%d 자동블라인드=%d 계정파기=%d"
                            .formatted(counters, posts, users, blinded, purged));
            log.info("야간 유지보수 완료 — 카운터 {}건 보정, 계정 {}건 파기", counters, purged);
        } catch (Exception e) {
            syncLog.fail(e.getMessage());
            log.error("야간 유지보수 실패", e);
        }
        syncLogRepository.save(syncLog);
    }

    private static Object[] concat(Object[] a, Object[] b) {
        Object[] out = new Object[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}

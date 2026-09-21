package com.snaphere.api.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 조회 성능용 비정규화 카운터를 원본 관계에서 다시 계산한다. (SYS-015) */
@Service
public class CounterReconcileService {
    private final JdbcClient jdbc;
    public CounterReconcileService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public int reconcile() {
        int changed = 0;
        changed += jdbc.sql("""
                UPDATE users u SET
                  follower_count=(SELECT count(*) FROM follows f WHERE f.following_id=u.id),
                  following_count=(SELECT count(*) FROM follows f WHERE f.follower_id=u.id),
                  post_count=(SELECT count(*) FROM posts p WHERE p.user_id=u.id AND p.status='ACTIVE'
                    AND EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=p.post_id)
                    AND NOT EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=p.post_id
                      AND (i.image_hash IS NULL OR i.thumbnail_url IS NULL OR i.image_key LIKE 'originals/%'))),
                  badge_count=(SELECT count(*) FROM user_badges b WHERE b.user_id=u.id)
                """).update();
        changed += jdbc.sql("""
                UPDATE places p SET
                  post_count=(SELECT count(*) FROM posts x WHERE x.place_id=p.place_id AND x.status='ACTIVE'
                    AND EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=x.post_id)
                    AND NOT EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=x.post_id
                      AND (i.image_hash IS NULL OR i.thumbnail_url IS NULL OR i.image_key LIKE 'originals/%'))),
                  visit_count=(SELECT count(*) FROM visits v WHERE v.place_id=p.place_id)
                """).update();
        changed += jdbc.sql("""
                UPDATE posts p SET
                  like_count=(SELECT count(*) FROM likes l WHERE l.target_type='POST' AND l.target_id=p.post_id),
                  comment_count=(SELECT count(*) FROM comments c WHERE c.post_id=p.post_id AND c.status='ACTIVE')
                """).update();
        changed += jdbc.sql("""
                UPDATE comments c SET like_count=(SELECT count(*) FROM likes l
                  WHERE l.target_type='COMMENT' AND l.target_id=c.comment_id)
                """).update();
        changed += jdbc.sql("""
                UPDATE tags t SET usage_count=(SELECT count(*) FROM post_tags pt
                  JOIN posts p ON p.post_id=pt.post_id WHERE pt.tag_id=t.tag_id AND p.status='ACTIVE'
                  AND NOT EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=p.post_id
                    AND (i.image_hash IS NULL OR i.thumbnail_url IS NULL OR i.image_key LIKE 'originals/%')))
                """).update();
        changed += jdbc.sql("""
                UPDATE badges b SET earned_count=(SELECT count(*) FROM user_badges ub WHERE ub.badge_id=b.badge_id)
                """).update();
        changed += jdbc.sql("""
                UPDATE events e SET participant_count=(SELECT count(*) FROM posts p
                  WHERE p.event_id=e.event_id AND p.status='ACTIVE'
                  AND EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=p.post_id)
                  AND NOT EXISTS(SELECT 1 FROM post_images i WHERE i.post_id=p.post_id
                    AND (i.image_hash IS NULL OR i.thumbnail_url IS NULL OR i.image_key LIKE 'originals/%')))
                """).update();
        return changed;
    }
}

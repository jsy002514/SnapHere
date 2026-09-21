package com.snaphere.api.post.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** ERD를 늘리지 않고 이미지 처리 시도·상태·중복 실행 잠금을 보관한다. */
@Component
public class MediaProcessingStateStore {
    private static final Logger log=LoggerFactory.getLogger(MediaProcessingStateStore.class);
    private static final Duration STATE_TTL=Duration.ofDays(30);
    private final StringRedisTemplate redis;
    public MediaProcessingStateStore(StringRedisTemplate redis) { this.redis=redis; }

    /** @return 1~5 시도 번호, 0은 실행 중/소진, -1은 Redis 장애. */
    public int begin(long postId) {
        try {
            Boolean locked=redis.opsForValue().setIfAbsent(key(postId,"lock"),"1",Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(locked)) return 0;
            Long attempts=redis.opsForValue().increment(key(postId,"attempts"));
            redis.expire(key(postId,"attempts"),STATE_TTL);
            if (attempts == null || attempts > 5) { redis.delete(key(postId,"lock")); return 0; }
            setStatus(postId,"PROCESSING");
            return attempts.intValue();
        } catch (RuntimeException failure) {
            log.warn("미디어 처리 상태 저장소 장애. postId={}",postId,failure); return -1;
        }
    }
    public void complete(long postId,boolean ready,int attempt) {
        try {
            setStatus(postId,ready?"READY":attempt>=5?"FAILED":"RETRY");
            redis.delete(key(postId,"lock"));
        } catch (RuntimeException failure) { log.warn("미디어 처리 결과 저장 실패. postId={}",postId,failure); }
    }
    public String status(long postId) {
        try { String value=redis.opsForValue().get(key(postId,"status")); return value==null?"PROCESSING":value; }
        catch (RuntimeException failure) { return "PROCESSING"; }
    }
    private void setStatus(long id,String status) { redis.opsForValue().set(key(id,"status"),status,STATE_TTL); }
    private static String key(long id,String suffix) { return "media:post:"+id+':'+suffix; }
}

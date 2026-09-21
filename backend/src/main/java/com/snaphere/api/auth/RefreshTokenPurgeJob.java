package com.snaphere.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 만료된 리프레시 토큰 행을 정리한다.
 *
 * <p>리프레시 토큰은 일회용이다. {@link AuthService#refresh} 가 쓴 토큰을 폐기하고 새 행을
 * 만드는데, 앱은 켤 때마다 재발급을 호출한다. 폐기는 {@code revoked_at} 만 찍고 행을 남기므로
 * 하루 다섯 번 앱을 여는 사용자면 한 달에 150행이 쌓이고 줄지 않는다.
 *
 * <p><b>기준을 {@code revoked_at} 이 아니라 {@code expires_at} 으로 잡는다.</b> 폐기된 토큰이
 * 다시 오면 {@code AuthService.refresh} 가 탈취로 보고 그 사용자의 토큰을 전부 끊는다
 * ({@code AUTH_TOKEN_REUSED}). 그 탐지는 폐기 기록이 남아 있어야 동작하므로, 폐기됐다는
 * 이유로 지우면 재사용이 그냥 "없는 토큰"으로 처리돼 탈취를 놓친다. 만료된 뒤에는 폐기
 * 여부와 무관하게 거절되니 그때 지우는 것이 안전하다.
 *
 * <p>여유 7일을 둔다. 탈취 조사에서 "언제 어느 기기로 재발급됐는지"를 되짚을 창을 남긴다.
 * 리프레시 TTL 이 30일이라 행은 최대 37일 남는다.
 */
@Component
@ConditionalOnProperty(prefix = "snaphere.jobs", name = "enabled", matchIfMissing = true)
public class RefreshTokenPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeJob.class);

    /** 만료 시각이 이만큼 지난 행을 지운다. */
    static final Duration GRACE = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokens;

    RefreshTokenPurgeJob(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    // 계정 파기(05:00)·카운터 보정(05:10)·검색로그 정리(05:20)와 겹치지 않게 05:30 에 둔다.
    @Scheduled(cron = "${snaphere.jobs.refresh-token-purge-cron:0 30 5 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void purge() {
        int deleted = refreshTokens.deleteExpiredBefore(Instant.now().minus(GRACE));
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건 정리", deleted);
        }
    }
}

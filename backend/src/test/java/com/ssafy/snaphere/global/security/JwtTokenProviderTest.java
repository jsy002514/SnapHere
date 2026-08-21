package com.ssafy.snaphere.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 토큰 발급 회귀 테스트.
 *
 * 2026-08-22 실제 장애: jti 가 없어서 같은 초에 두 번 발급하면 토큰이 완전히 같아졌고,
 * refresh_tokens 의 UNIQUE(token_hash) 제약을 위반해 로그인이 500 이 됐다.
 * 이 테스트가 그 회귀를 막는다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-unit-test-at-least-32-bytes-long-0123456789";

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(SECRET, 7200, 2592000);
    }

    @Test
    @DisplayName("★ 같은 사용자에게 연속 발급해도 리프레시 토큰은 매번 달라야 한다")
    void refreshTokensMustBeUnique() {
        JwtTokenProvider p = provider();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(p.createRefreshToken(1L));
        }
        // 200번을 같은 초 안에 발급해도 전부 달라야 한다
        assertThat(tokens).hasSize(200);
    }

    @Test
    @DisplayName("해시도 매번 달라야 한다 — UNIQUE(token_hash) 를 위반하지 않는 근거")
    void hashesMustBeUnique() {
        JwtTokenProvider p = provider();
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            hashes.add(JwtTokenProvider.sha256(p.createRefreshToken(1L)));
        }
        assertThat(hashes).hasSize(200);
    }

    @Test
    @DisplayName("액세스 토큰도 발급마다 달라야 한다")
    void accessTokensMustBeUnique() {
        JwtTokenProvider p = provider();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(p.createAccessToken(1L, "USER"));
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    @DisplayName("리프레시 토큰에서 사용자 id 를 되읽을 수 있다")
    void parseRefreshUserId() {
        JwtTokenProvider p = provider();
        String token = p.createRefreshToken(42L);
        assertThat(p.parseRefreshTokenUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시로 쓰려 하면 거부된다 (typ 클레임 검사)")
    void accessTokenIsNotRefreshToken() {
        JwtTokenProvider p = provider();
        String access = p.createAccessToken(42L, "USER");
        assertThat(p.parseRefreshTokenUserId(access)).isNull();
    }

    @Test
    @DisplayName("비밀키가 32바이트 미만이면 기동 자체를 막는다")
    void shortSecretRejected() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", 7200, 2592000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }
}

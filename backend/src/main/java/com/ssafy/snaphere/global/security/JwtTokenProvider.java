package com.ssafy.snaphere.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";

    private final SecretKey key;
    @Getter private final long accessValiditySeconds;
    @Getter private final long refreshValiditySeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-seconds}") long accessValiditySeconds,
            @Value("${app.jwt.refresh-token-validity-seconds}") long refreshValiditySeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 이 너무 짧습니다. 최소 32바이트 이상으로 설정하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessValiditySeconds = accessValiditySeconds;
        this.refreshValiditySeconds = refreshValiditySeconds;
    }

    public String createAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())      // jti — 아래 주석 참고
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessValiditySeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * ⚠️ jti(UUID) 가 반드시 있어야 한다. 없으면 실제로 500 이 난다.
     *
     * JWT 의 iat·exp 는 **초 단위**다. jti 가 없으면 같은 사용자가 같은 초에 두 번 발급받을 때
     * 페이로드가 완전히 동일해져 토큰 문자열이 바이트 단위로 같아진다.
     * 그러면 sha256 해시도 같아지고 `uk_refresh_token_hash` UNIQUE 제약을 위반해 저장이 실패한다.
     *
     * 2026-08-22 실제로 발생: 회원가입 직후(같은 초) 로그인하면 500.
     * 사용자 눈에는 "가입은 됐는데 로그인이 안 된다" 로 보였다.
     *
     * 그리고 이건 단순한 오류 회피가 아니다. 토큰이 세션별로 구분되지 않으면
     * 로테이션·재사용 감지(한 기기에서 탈취된 토큰만 무효화)가 성립하지 않는다.
     */
    public String createRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())      // jti — 발급마다 반드시 달라야 한다
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshValiditySeconds)))
                .signWith(key)
                .compact();
    }

    /** 유효하면 LoginUser, 아니면 null. 필터는 조용히 통과시키고 @AuthUser 가 막는다. */
    public LoginUser parseAccessToken(String token) {
        Claims c = parse(token, "ACCESS");
        if (c == null) return null;
        return new LoginUser(Long.valueOf(c.getSubject()), c.get(CLAIM_ROLE, String.class));
    }

    public Long parseRefreshTokenUserId(String token) {
        Claims c = parse(token, "REFRESH");
        return c == null ? null : Long.valueOf(c.getSubject());
    }

    private Claims parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return expectedType.equals(claims.get(CLAIM_TYPE, String.class)) ? claims : null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("invalid token: {}", e.getMessage());
            return null;
        }
    }

    /** 리프레시 토큰은 원문을 저장하지 않는다. DB 유출 시 피해를 줄이기 위해 해시만 보관. */
    public static String sha256(String value) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

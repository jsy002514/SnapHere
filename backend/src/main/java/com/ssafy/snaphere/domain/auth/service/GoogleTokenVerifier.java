package com.ssafy.snaphere.domain.auth.service;

import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 구글 ID Token 검증.
 *
 * TODO(S3): 실제 구현
 *   1) https://www.googleapis.com/oauth2/v3/certs 에서 JWK 를 받아 서명 검증 (키는 캐싱)
 *   2) iss 가 accounts.google.com 또는 https://accounts.google.com 인지
 *   3) ★ aud 가 우리 앱의 클라이언트 ID 인지  ← 이걸 빼면 다른 앱 토큰으로도 로그인된다
 *   4) exp 만료 확인
 *   5) sub / email / name / picture 추출
 *
 * 지금은 구조만 잡힌 스텁이다. 아이디·비밀번호 로그인이 먼저 동작하므로
 * S3 단계에서 구글 클라이언트 ID 를 받은 뒤 채운다.
 */
@Component
public class GoogleTokenVerifier {

    public record GoogleProfile(String sub, String email, String name, String picture) {}

    public GoogleProfile verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
        throw new BusinessException(ErrorCode.AUTH_004);   // 미구현
    }
}

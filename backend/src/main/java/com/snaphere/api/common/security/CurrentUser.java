package com.snaphere.api.common.security;

import java.util.UUID;

/**
 * 요청을 보낸 로그인 사용자.
 *
 * <p>식별자는 UUID 다. {@code users.id} 가 uuid 로 정의돼 있다 (V1__auth_schema.sql).
 */
public record CurrentUser(UUID userId) {
}

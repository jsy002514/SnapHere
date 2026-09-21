package com.snaphere.api.auth;

import com.snaphere.api.user.AuthorSnapshot;
import com.snaphere.api.user.AuthorSnapshotReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link AuthorSnapshotReader} 의 JPA 구현.
 *
 * <p>{@code UserRepository} 가 패키지 전용이라 이 클래스만 {@code auth} 패키지에 둔다.
 * 게시글 도메인은 {@code com.snaphere.api.user} 의 포트만 보고, 인증 쪽 엔티티를 모른다.
 */
@Component
class AuthorSnapshotJpaReader implements AuthorSnapshotReader {

    private final UserRepository users;

    AuthorSnapshotJpaReader(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthorSnapshot> findById(UUID userId) {
        return users.findById(userId).map(AuthorSnapshotJpaReader::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AuthorSnapshot> findAllByIds(Collection<UUID> userIds) {
        Map<UUID, AuthorSnapshot> result = new LinkedHashMap<>();
        for (User user : users.findAllById(userIds)) {
            result.put(user.getId(), toSnapshot(user));
        }
        return result;
    }

    private static AuthorSnapshot toSnapshot(User user) {
        return new AuthorSnapshot(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}

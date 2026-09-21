package com.snaphere.api.user;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 작성자 조회 포트.
 *
 * <p>게시글 도메인이 {@code auth} 패키지의 엔티티에 직접 의존하지 않게 가른다. 인증 담당이
 * {@code users} 스키마를 바꿔도 게시글 응답 조립 코드는 이 인터페이스만 보면 된다.
 */
public interface AuthorSnapshotReader {

    Optional<AuthorSnapshot> findById(UUID userId);

    /** 목록 응답용. 카드마다 한 번씩 조회하면 N+1 이다 (SYS-018). */
    Map<UUID, AuthorSnapshot> findAllByIds(Collection<UUID> userIds);
}

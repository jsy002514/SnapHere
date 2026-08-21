package com.ssafy.snaphere.domain.user.repository;

import com.ssafy.snaphere.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** 탈퇴 계정 복구 매칭용 (개인정보는 이미 파기되어 해시로만 찾는다) */
    Optional<User> findByRestoreKey(String restoreKey);

    // ── 비정규화 카운터 (새벽 보정 배치가 최종 정합성을 맞춘다) ──

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE User u SET u.followerCount = CASE WHEN u.followerCount + :delta < 0 THEN 0 ELSE u.followerCount + :delta END WHERE u.id = :userId")
    void addFollowerCount(@org.springframework.data.repository.query.Param("userId") Long userId,
                          @org.springframework.data.repository.query.Param("delta") int delta);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE User u SET u.followingCount = CASE WHEN u.followingCount + :delta < 0 THEN 0 ELSE u.followingCount + :delta END WHERE u.id = :userId")
    void addFollowingCount(@org.springframework.data.repository.query.Param("userId") Long userId,
                           @org.springframework.data.repository.query.Param("delta") int delta);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE User u SET u.postCount = CASE WHEN u.postCount + :delta < 0 THEN 0 ELSE u.postCount + :delta END WHERE u.id = :userId")
    void addPostCount(@org.springframework.data.repository.query.Param("userId") Long userId,
                      @org.springframework.data.repository.query.Param("delta") int delta);

    /** 추천 사용자 fallback — 인기 지수 상위. 정렬에 PK 를 붙여 동점 구간을 고정한다. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT u.user_id FROM users u
            WHERE u.status = 'ACTIVE' AND u.role = 'USER'
            ORDER BY u.popularity_score DESC, u.user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    java.util.List<Long> findTopByPopularity(@org.springframework.data.repository.query.Param("limit") int limit);
}

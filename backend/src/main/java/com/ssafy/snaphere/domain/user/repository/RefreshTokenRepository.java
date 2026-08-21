package com.ssafy.snaphere.domain.user.repository;

import com.ssafy.snaphere.domain.user.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 전체 기기 로그아웃 · 비밀번호 변경 · 탈퇴 시 사용 */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP "
         + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId);
}

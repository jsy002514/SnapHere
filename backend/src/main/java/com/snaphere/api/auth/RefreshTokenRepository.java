package com.snaphere.api.auth;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Modifying; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.time.Instant; import java.util.*;
interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> { Optional<RefreshToken> findByTokenHash(String tokenHash); List<RefreshToken> findAllByUserId(UUID userId); List<RefreshToken> findAllByDeviceId(UUID deviceId);
 /** 만료된 행을 delete 한 번으로 지운다. 엔티티를 읽어 오지 않는다. */
 @Modifying @Query("delete from RefreshToken t where t.expiresAt < :cutoff") int deleteExpiredBefore(@Param("cutoff") Instant cutoff); }

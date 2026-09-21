package com.snaphere.api.post.repository;

import com.snaphere.api.post.entity.TierLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 등급 판정 감사 로그. (PST-028, PST-047) */
public interface TierLogRepository extends JpaRepository<TierLogEntity, Long> {

    /** 판정 이유는 최신 1행을 읽는다. {@code idx_tier_logs_post} 를 탄다. */
    Optional<TierLogEntity> findFirstByPostIdOrderByDecidedAtDesc(Long postId);
}

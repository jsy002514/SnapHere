package com.ssafy.snaphere.domain.follow.service;

import com.ssafy.snaphere.domain.follow.repository.FollowRepository;
import com.ssafy.snaphere.domain.notification.entity.NotificationTargetType;
import com.ssafy.snaphere.domain.notification.entity.NotificationType;
import com.ssafy.snaphere.domain.notification.service.NotificationService;
import com.ssafy.snaphere.domain.user.entity.User;
import com.ssafy.snaphere.domain.user.repository.UserRepository;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.follow.daily-limit}") private int dailyLimit;

    @Transactional
    public FollowResult follow(Long followerId, Long targetId) {
        // 자기 자신 팔로우는 DB 제약으로 막을 수 없어 애플리케이션에서 차단한다.
        if (followerId.equals(targetId)) throw new BusinessException(ErrorCode.FOLLOW_001);

        User target = userRepository.findById(targetId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_404, "userId"));

        // 하루 한도 — 팔로우 스팸(대량 팔로우 후 언팔로우로 노출 늘리기)을 막는다.
        if (followRepository.countFollowingToday(followerId) >= dailyLimit) {
            throw new BusinessException(ErrorCode.FOLLOW_002);
        }

        boolean added = followRepository.follow(followerId, targetId);
        if (added) {
            userRepository.addFollowerCount(targetId, 1);
            userRepository.addFollowingCount(followerId, 1);

            User me = userRepository.findById(followerId).orElse(null);
            notificationService.notifyAsync(targetId, followerId,
                    NotificationType.FOLLOW, NotificationTargetType.USER, followerId,
                    Map.of("nickname", me == null ? "" : me.getNickname()),
                    me == null ? null : me.getProfileImageUrl());
        }
        return new FollowResult(targetId, true, followRepository.countFollowers(targetId));
    }

    @Transactional
    public FollowResult unfollow(Long followerId, Long targetId) {
        boolean removed = followRepository.unfollow(followerId, targetId);
        if (removed) {
            userRepository.addFollowerCount(targetId, -1);
            userRepository.addFollowingCount(followerId, -1);
        }
        return new FollowResult(targetId, false, followRepository.countFollowers(targetId));
    }

    @Transactional(readOnly = true)
    public List<UserBrief> followers(Long userId, Long viewerId, int page, int size) {
        List<Long> ids = followRepository.findFollowerIds(userId, size, (page - 1) * size);
        return toBriefs(ids, viewerId);
    }

    @Transactional(readOnly = true)
    public List<UserBrief> followings(Long userId, Long viewerId, int page, int size) {
        List<Long> ids = followRepository.findFollowingIds(userId, size, (page - 1) * size);
        return toBriefs(ids, viewerId);
    }

    /**
     * 추천 사용자. "내가 팔로우한 사람이 팔로우하는 사람" 을 먼저 쓰고,
     * 데이터가 없으면(서비스 초기) 인기 사용자로 채운다.
     * 채우지 않으면 신규 사용자에게는 추천이 영원히 빈 목록이다.
     */
    @Transactional(readOnly = true)
    public List<UserBrief> suggestions(Long userId, int limit) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(followRepository.findSuggestions(userId, limit));

        if (ids.size() < limit) {
            Set<Long> exclude = new HashSet<>(followRepository.findAllFollowingIds(userId));
            exclude.add(userId);
            userRepository.findTopByPopularity(limit * 3).stream()
                    .filter(u -> !exclude.contains(u))
                    .forEach(u -> { if (ids.size() < limit) ids.add(u); });
        }
        return toBriefs(new ArrayList<>(ids), userId);
    }

    private List<UserBrief> toBriefs(List<Long> ids, Long viewerId) {
        if (ids.isEmpty()) return List.of();
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));

        Set<Long> following = viewerId == null ? Set.of()
                : new HashSet<>(followRepository.findFollowedAmong(viewerId, ids));

        List<UserBrief> out = new ArrayList<>();
        for (Long id : ids) {   // 조회 순서를 유지한다 (findAllById 는 순서를 보장하지 않는다)
            User u = users.get(id);
            if (u == null || !u.isActive()) continue;
            out.add(new UserBrief(u.getId(), u.getNickname(), u.getProfileImageUrl(),
                    u.getGrade() == null ? null : u.getGrade().name(),
                    u.getPopularityScore(), u.getPostCount(), u.getFollowerCount(),
                    following.contains(u.getId())));
        }
        return out;
    }

    public record FollowResult(Long userId, boolean following, long followerCount) {}

    public record UserBrief(Long userId, String nickname, String profileImageUrl, String grade,
                            int popularityScore, int postCount, int followerCount,
                            boolean isFollowing) {}
}

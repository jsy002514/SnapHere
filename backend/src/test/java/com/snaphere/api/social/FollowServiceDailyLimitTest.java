package com.snaphere.api.social;

import com.snaphere.api.auth.User;
import com.snaphere.api.auth.UserRepository;
import com.snaphere.api.auth.UserStatus;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.PagingProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 팔로우 일일 한도 회귀 테스트. (SOC-006, 하루 200회)
 *
 * <p>이 방어에는 그동안 테스트가 없었다. "특정 사용자를 하루에 200번 넘게 팔로우" 같은 남용을
 * {@link FollowService} 가 {@code SOC_DAILY_LIMIT} 로 막는지, 그리고 막을 때는 실제 삽입을
 * 시도하지 않는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceDailyLimitTest {

    @Mock private FollowRepository follows;
    @Mock private UserRepository users;
    @Mock private PagingProperties paging;
    @Mock private EntityManager em;

    private FollowService service;

    private final UUID actor = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FollowService(follows, users, paging, em);
    }

    @Test
    @DisplayName("오늘 이미 200명을 팔로우했으면 SOC_DAILY_LIMIT 로 막고 삽입하지 않는다")
    void 일일_200_한도() {
        User source = mock(User.class);
        when(source.getStatus()).thenReturn(UserStatus.ACTIVE);
        User targetUser = mock(User.class);
        when(targetUser.getStatus()).thenReturn(UserStatus.ACTIVE);

        when(users.findLockedById(actor)).thenReturn(Optional.of(source));
        when(users.findById(target)).thenReturn(Optional.of(targetUser));
        when(follows.existsById(any(FollowId.class))).thenReturn(false);
        when(follows.countByIdFollowerIdAndCreatedAtGreaterThanEqual(eq(actor), any(Instant.class)))
                .thenReturn(200L);

        assertThatThrownBy(() -> service.follow(actor, target))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).errorCode())
                        .isEqualTo(ErrorCode.SOC_DAILY_LIMIT));

        verify(follows, never()).insertIfAbsent(any(), any());
    }
}

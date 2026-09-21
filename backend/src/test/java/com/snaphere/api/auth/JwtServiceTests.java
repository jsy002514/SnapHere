package com.snaphere.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snaphere.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {
    private final JwtService jwt = new JwtService(
            new AuthProperties("test-client", "test-only-secret-that-is-at-least-thirty-two-bytes", "2026-08-01"),
            new ObjectMapper());

    @Test
    void signsAndVerifiesTheAuthenticatedUserAndRole() {
        User user = User.newGoogleUser("google-subject", "user@example.com", null);

        UserDevice device = UserDevice.create(user, "device-1", Platform.ANDROID, null);
        AuthPrincipal principal = jwt.verify(jwt.issue(user, device));

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.deviceId()).isEqualTo(device.getId());
        assertThat(principal.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsTamperedToken() {
        User user = User.newGoogleUser("google-subject", "user@example.com", null);
        String token = jwt.issue(user, UserDevice.create(user, "device-1", Platform.ANDROID, null));

        assertThatThrownBy(() -> jwt.verify(token + "tampered"))
                .isInstanceOf(ApiException.class);
    }
}

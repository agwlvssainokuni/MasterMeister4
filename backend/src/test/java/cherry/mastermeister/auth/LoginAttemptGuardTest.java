/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.auth;

import cherry.mastermeister.auth.entity.LoginAttemptState;
import cherry.mastermeister.auth.repository.LoginAttemptStateRepository;
import cherry.mastermeister.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BR-LOGIN-01〜03。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTest {

    @Mock
    private LoginAttemptStateRepository loginAttemptStateRepository;

    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        AppProperties.LoginAttempt loginAttempt = new AppProperties.LoginAttempt(5, Duration.ofMinutes(15));
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                loginAttempt,
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"));
        guard = new LoginAttemptGuard(loginAttemptStateRepository, appProperties);
    }

    @Test
    void isLocked_returnsFalse_whenNoState() {
        when(loginAttemptStateRepository.findById("taro@example.com")).thenReturn(Optional.empty());

        assertThat(guard.isLocked("taro@example.com")).isFalse();
    }

    @Test
    void isLocked_returnsTrue_whenLockedUntilInFuture() {
        LoginAttemptState state = new LoginAttemptState("taro@example.com");
        state.recordFailure(Instant.now(), 1, Duration.ofMinutes(15));
        when(loginAttemptStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        assertThat(guard.isLocked("taro@example.com")).isTrue();
    }

    @Test
    void recordFailure_doesNotLock_beforeReachingMaxFailures() {
        LoginAttemptState state = new LoginAttemptState("taro@example.com");
        state.recordFailure(Instant.now(), 5, Duration.ofMinutes(15));
        state.recordFailure(Instant.now(), 5, Duration.ofMinutes(15));
        state.recordFailure(Instant.now(), 5, Duration.ofMinutes(15));
        // 4回目の失敗（4/5、まだロックしない）
        when(loginAttemptStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        guard.recordFailure("taro@example.com");

        assertThat(state.getFailureCount()).isEqualTo(4);
        assertThat(state.isLocked(Instant.now())).isFalse();
    }

    @Test
    void recordFailure_locksAccount_uponReachingMaxFailures() {
        LoginAttemptState state = new LoginAttemptState("taro@example.com");
        for (int i = 0; i < 4; i++) {
            state.recordFailure(Instant.now(), 5, Duration.ofMinutes(15));
        }
        // 5回目の失敗（5/5、ロックされる）
        when(loginAttemptStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        ArgumentCaptor<LoginAttemptState> captor = ArgumentCaptor.forClass(LoginAttemptState.class);
        guard.recordFailure("taro@example.com");

        verify(loginAttemptStateRepository).save(captor.capture());
        assertThat(captor.getValue().isLocked(Instant.now())).isTrue();
    }

    @Test
    void reset_clearsFailureCount() {
        LoginAttemptState state = new LoginAttemptState("taro@example.com");
        state.recordFailure(Instant.now(), 5, Duration.ofMinutes(15));
        when(loginAttemptStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        guard.reset("taro@example.com");

        assertThat(state.getFailureCount()).isZero();
        assertThat(state.getLockedUntil()).isNull();
    }
}

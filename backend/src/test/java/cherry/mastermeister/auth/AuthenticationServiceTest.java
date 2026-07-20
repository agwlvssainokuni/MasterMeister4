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

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.AccountLockedException;
import cherry.mastermeister.common.exception.AuthenticationFailedException;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §5。BR-REG-03・04（列挙攻撃対策のため同一例外）、BR-LOGIN-01。
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptGuard loginAttemptGuard;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditEventPublisher auditEventPublisher;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, Duration.ofMinutes(15)),
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"));
        authenticationService = new AuthenticationService(userRepository, passwordEncoder, loginAttemptGuard,
                refreshTokenService, auditEventPublisher, jwtEncoder, appProperties);
    }

    private User approvedUser() {
        return new User("taro@example.com", "hashed", "Taro Yamada", Language.ja, UserStatus.APPROVED, Role.USER,
                Instant.now(), Instant.now(), null);
    }

    private void stubJwtEncoder() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("access-token-value");
        when(jwtEncoder.encode(any())).thenReturn(jwt);
    }

    @Test
    void login_succeeds_whenCredentialsAreValid() {
        User user = approvedUser();
        when(loginAttemptGuard.isLocked("taro@example.com")).thenReturn(false);
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(refreshTokenService.issue(any())).thenReturn(new IssuedRefreshToken(1L, "raw-refresh-token"));
        stubJwtEncoder();

        TokenPair result = authenticationService.login("taro@example.com", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token-value");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        verify(loginAttemptGuard).reset("taro@example.com");
        verify(loginAttemptGuard, never()).recordFailure(any());
    }

    @Test
    void login_throwsAccountLocked_whenLocked() {
        when(loginAttemptGuard.isLocked("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login("taro@example.com", "password123"))
                .isInstanceOf(AccountLockedException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_throwsAuthenticationFailed_whenUserNotFound() {
        when(loginAttemptGuard.isLocked("nobody@example.com")).thenReturn(false);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login("nobody@example.com", "password123"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(loginAttemptGuard).recordFailure("nobody@example.com");
    }

    @Test
    void login_throwsAuthenticationFailed_whenPasswordMismatch() {
        User user = approvedUser();
        when(loginAttemptGuard.isLocked("taro@example.com")).thenReturn(false);
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login("taro@example.com", "wrong"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(loginAttemptGuard).recordFailure("taro@example.com");
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED", "DISABLED"})
    void login_throwsAuthenticationFailed_whenUserNotApproved(UserStatus status) {
        User user = new User("taro@example.com", "hashed", "Taro Yamada", Language.ja, status, Role.USER,
                Instant.now(), Instant.now(), null);
        when(loginAttemptGuard.isLocked("taro@example.com")).thenReturn(false);
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.of(user));

        // BR-REG-03: 未承認ユーザはパスワード照合結果に関わらず同一の例外・同一のコードとする
        assertThatThrownBy(() -> authenticationService.login("taro@example.com", "password123"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refresh_issuesNewTokenPair() {
        User user = approvedUser();
        when(refreshTokenService.rotate("old-raw-token")).thenReturn(new IssuedRefreshToken(1L, "new-raw-token"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubJwtEncoder();

        TokenPair result = authenticationService.refresh("old-raw-token");

        assertThat(result.accessToken()).isEqualTo("access-token-value");
        assertThat(result.refreshToken()).isEqualTo("new-raw-token");
    }

    @Test
    void logout_publishesLogoutEvent_whenTokenFound() {
        when(refreshTokenService.revokeByRawToken("raw-token")).thenReturn(1L);

        authenticationService.logout("raw-token");

        verify(auditEventPublisher).publish(any());
    }

    @Test
    void logout_doesNotPublishEvent_whenTokenNotFound() {
        when(refreshTokenService.revokeByRawToken("unknown-token")).thenReturn(null);

        authenticationService.logout("unknown-token");

        verify(auditEventPublisher, never()).publish(any());
    }
}

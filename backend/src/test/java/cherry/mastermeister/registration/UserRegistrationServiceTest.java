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

package cherry.mastermeister.registration;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.auth.RefreshTokenService;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.InvalidUserStateTransitionException;
import cherry.mastermeister.common.exception.PasswordPolicyViolationException;
import cherry.mastermeister.common.exception.RegistrationTokenInvalidException;
import cherry.mastermeister.common.security.TokenGenerator;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.RegistrationToken;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.RegistrationTokenRepository;
import cherry.mastermeister.registration.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COMP-01。business-logic-model.md §1〜4。
 */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegistrationTokenRepository registrationTokenRepository;
    @Mock
    private TokenGenerator tokenGenerator;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private PasswordBreachChecker passwordBreachChecker;
    @Mock
    private RegistrationRateGuard registrationRateGuard;
    @Mock
    private EmailNotificationService emailNotificationService;
    @Mock
    private AuditEventPublisher auditEventPublisher;
    @Mock
    private RefreshTokenService refreshTokenService;

    private UserRegistrationService service;

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
        service = new UserRegistrationService(userRepository, registrationTokenRepository, tokenGenerator,
                passwordEncoder, passwordBreachChecker, registrationRateGuard, emailNotificationService,
                auditEventPublisher, refreshTokenService, appProperties);
    }

    // --- startRegistration (Step1) ---

    @Test
    void startRegistration_createsTokenAndSendsEmail_forNewEmail() {
        when(registrationRateGuard.tryAcquire("taro@example.com")).thenReturn(true);
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.empty());
        when(registrationTokenRepository.findAllByEmailAndUsedAtIsNull("taro@example.com")).thenReturn(List.of());
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");

        service.startRegistration("taro@example.com", Language.ja);

        verify(registrationTokenRepository).save(any(RegistrationToken.class));
        verify(emailNotificationService).sendRegistrationConfirmation(
                org.mockito.ArgumentMatchers.eq("taro@example.com"),
                org.mockito.ArgumentMatchers.contains("raw-token"),
                org.mockito.ArgumentMatchers.eq(Language.ja));
        verify(auditEventPublisher).publish(any());
    }

    @Test
    void startRegistration_doesNothing_whenRateLimited() {
        when(registrationRateGuard.tryAcquire("taro@example.com")).thenReturn(false);

        service.startRegistration("taro@example.com", Language.ja);

        verify(userRepository, never()).findByEmail(anyString());
        verify(registrationTokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendRegistrationConfirmation(any(), any(), any());
    }

    @Test
    void startRegistration_doesNothing_whenEmailAlreadyExists() {
        when(registrationRateGuard.tryAcquire("taro@example.com")).thenReturn(true);
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.of(approvedUser()));

        service.startRegistration("taro@example.com", Language.ja);

        verify(registrationTokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendRegistrationConfirmation(any(), any(), any());
    }

    @Test
    void startRegistration_invalidatesOldUnusedTokens() {
        RegistrationToken old = new RegistrationToken("taro@example.com", "old-hash",
                Instant.now().plus(Duration.ofHours(1)), Instant.now());
        when(registrationRateGuard.tryAcquire("taro@example.com")).thenReturn(true);
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.empty());
        when(registrationTokenRepository.findAllByEmailAndUsedAtIsNull("taro@example.com")).thenReturn(List.of(old));
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");

        service.startRegistration("taro@example.com", Language.ja);

        assertThat(old.isUsed()).isTrue();
    }

    // --- completeRegistration (Step2) ---

    @Test
    void completeRegistration_createsUser_whenTokenValid() {
        RegistrationToken token = new RegistrationToken("taro@example.com", "hashed-token",
                Instant.now().plus(Duration.ofHours(1)), Instant.now());
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");
        when(registrationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(passwordBreachChecker.isBreached("password123")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Long userId = service.completeRegistration("raw-token", "Taro Yamada", Language.ja, "password123");

        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(any(User.class));
        verify(auditEventPublisher).publish(any());
    }

    @Test
    void completeRegistration_throws_whenTokenNotFound() {
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");
        when(registrationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeRegistration("raw-token", "Taro Yamada", Language.ja, "password123"))
                .isInstanceOf(RegistrationTokenInvalidException.class);
    }

    @Test
    void completeRegistration_throws_whenTokenAlreadyUsed() {
        RegistrationToken token = new RegistrationToken("taro@example.com", "hashed-token",
                Instant.now().plus(Duration.ofHours(1)), Instant.now());
        token.markUsed(Instant.now());
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");
        when(registrationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.completeRegistration("raw-token", "Taro Yamada", Language.ja, "password123"))
                .isInstanceOf(RegistrationTokenInvalidException.class);
    }

    @Test
    void completeRegistration_throws_whenTokenExpired() {
        RegistrationToken token = new RegistrationToken("taro@example.com", "hashed-token",
                Instant.now().minus(Duration.ofHours(1)), Instant.now().minus(Duration.ofHours(4)));
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");
        when(registrationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.completeRegistration("raw-token", "Taro Yamada", Language.ja, "password123"))
                .isInstanceOf(RegistrationTokenInvalidException.class);
    }

    @Test
    void completeRegistration_throws_whenPasswordTooShort() {
        RegistrationToken token = new RegistrationToken("taro@example.com", "hashed-token",
                Instant.now().plus(Duration.ofHours(1)), Instant.now());
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");
        when(registrationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.completeRegistration("raw-token", "Taro Yamada", Language.ja, "short"))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void completeRegistration_throws_whenPasswordCompromised() {
        RegistrationToken token = new RegistrationToken("taro@example.com", "hashed-token",
                Instant.now().plus(Duration.ofHours(1)), Instant.now());
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");
        when(registrationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(passwordBreachChecker.isBreached("password123")).thenReturn(true);

        assertThatThrownBy(() -> service.completeRegistration("raw-token", "Taro Yamada", Language.ja, "password123"))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    // --- approveUser (§2, §2.1) ---

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED"})
    void approveUser_succeeds_fromPendingOrRejected(UserStatus initialStatus) {
        User user = userWithStatus(initialStatus);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.approveUser(1L, 99L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.APPROVED);
        verify(emailNotificationService).sendApprovalResult(any(), any(), any(), any());
        verify(auditEventPublisher).publish(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"APPROVED", "DISABLED"})
    void approveUser_throws_whenNotPendingOrRejected(UserStatus initialStatus) {
        User user = userWithStatus(initialStatus);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.approveUser(1L, 99L))
                .isInstanceOf(InvalidUserStateTransitionException.class);
    }

    // --- rejectUser (§2) ---

    @Test
    void rejectUser_succeeds_fromPending() {
        User user = userWithStatus(UserStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.rejectUser(1L, 99L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.REJECTED);
        verify(emailNotificationService).sendRejectionResult(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"APPROVED", "REJECTED", "DISABLED"})
    void rejectUser_throws_whenNotPending(UserStatus initialStatus) {
        User user = userWithStatus(initialStatus);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.rejectUser(1L, 99L))
                .isInstanceOf(InvalidUserStateTransitionException.class);
    }

    // --- disableUser / enableUser (§3) ---

    @Test
    void disableUser_succeeds_fromApproved_andRevokesRefreshTokens() {
        User user = userWithStatus(UserStatus.APPROVED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.disableUser(1L, 99L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(refreshTokenService).revokeAllForUser(user.getId());
        verify(emailNotificationService, never()).sendApprovalResult(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED", "DISABLED"})
    void disableUser_throws_whenNotApproved(UserStatus initialStatus) {
        User user = userWithStatus(initialStatus);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.disableUser(1L, 99L))
                .isInstanceOf(InvalidUserStateTransitionException.class);
    }

    @Test
    void enableUser_succeeds_fromDisabled() {
        User user = userWithStatus(UserStatus.DISABLED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.enableUser(1L, 99L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.APPROVED);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"PENDING", "APPROVED", "REJECTED"})
    void enableUser_throws_whenNotDisabled(UserStatus initialStatus) {
        User user = userWithStatus(initialStatus);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.enableUser(1L, 99L))
                .isInstanceOf(InvalidUserStateTransitionException.class);
    }

    // --- createApprovedAccount (§4, AdminBootstrap) ---

    @Test
    void createApprovedAccount_createsUser_whenNotExists() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("adminPass1")).thenReturn("encoded");

        service.createApprovedAccount("admin@example.com", "adminPass1", Role.ADMIN);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void createApprovedAccount_isIdempotent_whenAlreadyExists() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(approvedUser()));

        service.createApprovedAccount("admin@example.com", "adminPass1", Role.ADMIN);

        verify(userRepository, never()).save(any(User.class));
    }

    private User approvedUser() {
        return userWithStatus(UserStatus.APPROVED);
    }

    private User userWithStatus(UserStatus status) {
        return new User("taro@example.com", "hashed", "Taro Yamada", Language.ja, status, Role.USER,
                Instant.now(), Instant.now(), null);
    }
}

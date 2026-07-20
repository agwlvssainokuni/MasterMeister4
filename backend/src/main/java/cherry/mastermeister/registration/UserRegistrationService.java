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
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * COMP-01。business-logic-model.md §1〜3。
 */
@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RegistrationTokenRepository registrationTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final PasswordBreachChecker passwordBreachChecker;
    private final RegistrationRateGuard registrationRateGuard;
    private final EmailNotificationService emailNotificationService;
    private final AuditEventPublisher auditEventPublisher;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    public UserRegistrationService(UserRepository userRepository,
                                    RegistrationTokenRepository registrationTokenRepository,
                                    TokenGenerator tokenGenerator, PasswordEncoder passwordEncoder,
                                    PasswordBreachChecker passwordBreachChecker,
                                    RegistrationRateGuard registrationRateGuard,
                                    EmailNotificationService emailNotificationService,
                                    AuditEventPublisher auditEventPublisher,
                                    RefreshTokenService refreshTokenService, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.registrationTokenRepository = registrationTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.passwordBreachChecker = passwordBreachChecker;
        this.registrationRateGuard = registrationRateGuard;
        this.emailNotificationService = emailNotificationService;
        this.auditEventPublisher = auditEventPublisher;
        this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
    }

    /**
     * business-logic-model.md §1.1。BR-REG-04・06・07によりレスポンス自体は常に成功として扱うため、
     * このメソッドは例外を送出しない（レート制限・重複メールいずれの場合も静かに処理をスキップする）。
     */
    @Transactional
    public void startRegistration(String email, Language uiLanguage) {
        if (!registrationRateGuard.tryAcquire(email)) {
            return;
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        registrationTokenRepository.findAllByEmailAndUsedAtIsNull(email)
                .forEach(old -> old.markUsed(Instant.now()));

        String rawToken = tokenGenerator.generate();
        Instant now = Instant.now();
        RegistrationToken token = new RegistrationToken(email, tokenGenerator.hash(rawToken),
                now.plus(appProperties.userRegistration().tokenExpiry()), now);
        registrationTokenRepository.save(token);

        String confirmationUrl = appProperties.frontend().baseUrl() + "/register/complete?token=" + rawToken;
        emailNotificationService.sendRegistrationConfirmation(email, confirmationUrl, uiLanguage);

        auditEventPublisher.publish(new AuditEvent(now, null, null,
                AuditEventType.REGISTRATION_REQUESTED, email, ResultStatus.SUCCESS, null));
    }

    /**
     * business-logic-model.md §1.2。
     *
     * @throws RegistrationTokenInvalidException トークンが存在しない・使用済み・期限切れの場合
     * @throws PasswordPolicyViolationException  パスワードが最小文字数未満、または既知漏洩パスワードの場合
     */
    @Transactional
    public Long completeRegistration(String rawToken, String fullName, Language preferredLanguage,
                                      String rawPassword) {
        String tokenHash = tokenGenerator.hash(rawToken);
        RegistrationToken token = registrationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(RegistrationTokenInvalidException::invalid);

        Instant now = Instant.now();
        if (token.isUsed()) {
            throw RegistrationTokenInvalidException.invalid();
        }
        if (token.isExpired(now)) {
            throw RegistrationTokenInvalidException.expired();
        }

        int minLength = appProperties.password().minLength();
        if (rawPassword.length() < minLength) {
            throw PasswordPolicyViolationException.tooShort(minLength);
        }
        if (passwordBreachChecker.isBreached(rawPassword)) {
            throw PasswordPolicyViolationException.compromised();
        }

        User user = new User(token.getEmail(), passwordEncoder.encode(rawPassword), fullName, preferredLanguage,
                UserStatus.PENDING, Role.USER, now, now, null);
        userRepository.save(user);

        token.markUsed(now);
        registrationTokenRepository.save(token);

        auditEventPublisher.publish(new AuditEvent(now, user.getId(), null,
                AuditEventType.REGISTRATION_COMPLETED, user.getEmail(), ResultStatus.SUCCESS, null));

        return user.getId();
    }

    @Transactional(readOnly = true)
    public List<User> listUsers(Optional<UserStatus> status) {
        return status.map(userRepository::findAllByStatus).orElseGet(userRepository::findAll);
    }

    /**
     * business-logic-model.md §2, §2.1。{@code PENDING}からの初回承認、{@code REJECTED}からの却下取り消しの
     * いずれも同一メソッド・同一の{@code USER_APPROVED}イベントとして扱う。
     *
     * @throws InvalidUserStateTransitionException 遷移元が{@code PENDING}/{@code REJECTED}以外の場合
     */
    @Transactional
    public void approveUser(Long userId, Long approvedBy) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.REJECTED) {
            throw new InvalidUserStateTransitionException();
        }
        user.changeStatus(UserStatus.APPROVED, approvedBy, Instant.now());
        userRepository.save(user);

        String loginUrl = appProperties.frontend().baseUrl() + "/login";
        emailNotificationService.sendApprovalResult(user.getEmail(), user.getFullName(), loginUrl,
                user.getPreferredLanguage());

        auditEventPublisher.publish(new AuditEvent(Instant.now(), approvedBy, null,
                AuditEventType.USER_APPROVED, user.getEmail(), ResultStatus.SUCCESS, null));
    }

    /**
     * @throws InvalidUserStateTransitionException 遷移元が{@code PENDING}以外の場合
     */
    @Transactional
    public void rejectUser(Long userId, Long rejectedBy) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new InvalidUserStateTransitionException();
        }
        user.changeStatus(UserStatus.REJECTED, rejectedBy, Instant.now());
        userRepository.save(user);

        emailNotificationService.sendRejectionResult(user.getEmail(), user.getFullName(),
                user.getPreferredLanguage());

        auditEventPublisher.publish(new AuditEvent(Instant.now(), rejectedBy, null,
                AuditEventType.USER_REJECTED, user.getEmail(), ResultStatus.SUCCESS, null));
    }

    /**
     * business-logic-model.md §3。BR-TOKEN-04により、当該ユーザの有効な全リフレッシュトークンを失効させる。
     *
     * @throws InvalidUserStateTransitionException 遷移元が{@code APPROVED}以外の場合
     */
    @Transactional
    public void disableUser(Long userId, Long disabledBy) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() != UserStatus.APPROVED) {
            throw new InvalidUserStateTransitionException();
        }
        user.changeStatus(UserStatus.DISABLED, disabledBy, Instant.now());
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());

        auditEventPublisher.publish(new AuditEvent(Instant.now(), disabledBy, null,
                AuditEventType.USER_DISABLED, user.getEmail(), ResultStatus.SUCCESS, null));
    }

    /**
     * @throws InvalidUserStateTransitionException 遷移元が{@code DISABLED}以外の場合
     */
    @Transactional
    public void enableUser(Long userId, Long enabledBy) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() != UserStatus.DISABLED) {
            throw new InvalidUserStateTransitionException();
        }
        user.changeStatus(UserStatus.APPROVED, enabledBy, Instant.now());
        userRepository.save(user);

        auditEventPublisher.publish(new AuditEvent(Instant.now(), enabledBy, null,
                AuditEventType.USER_ENABLED, user.getEmail(), ResultStatus.SUCCESS, null));
    }

    /**
     * business-logic-model.md §4。AdminBootstrapService専用の内部エントリポイント。
     * 通常の登録フロー（トークン発行〜承認）を経ずに承認済みアカウントを直接作成する。冪等（既に存在する場合は何もしない）。
     */
    @Transactional
    public void createApprovedAccount(String email, String rawPassword, Role role) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        User user = new User(email, passwordEncoder.encode(rawPassword), email, Language.ja,
                UserStatus.APPROVED, role, now, now, null);
        userRepository.save(user);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId).orElseThrow(InvalidUserStateTransitionException::new);
    }
}

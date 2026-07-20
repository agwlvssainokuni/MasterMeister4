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
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.AccountLockedException;
import cherry.mastermeister.common.exception.AuthenticationFailedException;
import cherry.mastermeister.common.exception.RefreshTokenInvalidException;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * COMP-03。business-logic-model.md §5。
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard loginAttemptGuard;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventPublisher auditEventPublisher;
    private final JwtEncoder jwtEncoder;
    private final AppProperties.Jwt jwtProperties;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  LoginAttemptGuard loginAttemptGuard, RefreshTokenService refreshTokenService,
                                  AuditEventPublisher auditEventPublisher, JwtEncoder jwtEncoder,
                                  AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptGuard = loginAttemptGuard;
        this.refreshTokenService = refreshTokenService;
        this.auditEventPublisher = auditEventPublisher;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = appProperties.jwt();
    }

    /**
     * @throws AccountLockedException         BR-LOGIN-01のロック中
     * @throws AuthenticationFailedException  パスワード不一致・ユーザ不存在・未承認/却下/無効化のいずれか（BR-REG-03・04で同一コード）
     */
    @Transactional
    public TokenPair login(String email, String rawPassword) {
        if (loginAttemptGuard.isLocked(email)) {
            throw new AccountLockedException();
        }

        Optional<User> found = userRepository.findByEmail(email);
        boolean valid = found.isPresent()
                && found.get().getStatus() == UserStatus.APPROVED
                && passwordEncoder.matches(rawPassword, found.get().getPasswordHash());

        if (!valid) {
            loginAttemptGuard.recordFailure(email);
            auditEventPublisher.publish(new AuditEvent(Instant.now(), null, null,
                    AuditEventType.LOGIN_FAILURE, email, ResultStatus.FAILURE, null));
            throw new AuthenticationFailedException();
        }

        User user = found.get();
        loginAttemptGuard.reset(email);
        IssuedRefreshToken issued = refreshTokenService.issue(user.getId());
        String accessToken = generateAccessToken(user);

        auditEventPublisher.publish(new AuditEvent(Instant.now(), user.getId(), null,
                AuditEventType.LOGIN, user.getEmail(), ResultStatus.SUCCESS, null));

        return new TokenPair(accessToken, issued.rawToken());
    }

    /**
     * @throws RefreshTokenInvalidException トークンが存在しない・期限切れ・再利用検知のいずれか
     */
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        IssuedRefreshToken issued = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(issued.userId())
                .orElseThrow(RefreshTokenInvalidException::new);
        String accessToken = generateAccessToken(user);
        return new TokenPair(accessToken, issued.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        Long userId = refreshTokenService.revokeByRawToken(rawRefreshToken);
        if (userId != null) {
            auditEventPublisher.publish(new AuditEvent(Instant.now(), userId, null,
                    AuditEventType.LOGOUT, null, ResultStatus.SUCCESS, null));
        }
    }

    private String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mastermeister")
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenExpiry()))
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}

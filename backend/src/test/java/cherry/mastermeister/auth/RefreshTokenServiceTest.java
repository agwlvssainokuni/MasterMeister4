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
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.auth.entity.RefreshToken;
import cherry.mastermeister.auth.entity.RevokeReason;
import cherry.mastermeister.auth.repository.RefreshTokenRepository;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.RefreshTokenInvalidException;
import cherry.mastermeister.common.security.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §6。BR-TOKEN-01〜04。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenGenerator tokenGenerator;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, Duration.ofMinutes(15)),
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"));
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, tokenGenerator, auditEventPublisher,
                appProperties);
    }

    @Test
    void issue_generatesAndSavesNewToken() {
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenGenerator.hash("raw-token")).thenReturn("hashed-token");

        IssuedRefreshToken result = refreshTokenService.issue(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.rawToken()).isEqualTo("raw-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_revokesOldToken_andIssuesNewOneInSameFamily() {
        RefreshToken existing = new RefreshToken(1L, "family-1", "old-hash", Instant.now(),
                Instant.now().plus(Duration.ofDays(1)));
        when(tokenGenerator.hash("old-raw")).thenReturn("old-hash");
        when(tokenGenerator.hash("new-raw")).thenReturn("new-hash");
        when(tokenGenerator.generate()).thenReturn("new-raw");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(existing));

        IssuedRefreshToken result = refreshTokenService.rotate("old-raw");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getRevokedReason()).isEqualTo(RevokeReason.ROTATED);
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.rawToken()).isEqualTo("new-raw");
    }

    @Test
    void rotate_throws_whenTokenNotFound() {
        when(tokenGenerator.hash("unknown")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void rotate_throws_whenTokenExpired() {
        RefreshToken expired = new RefreshToken(1L, "family-1", "hash", Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(1)));
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void rotate_detectsReuse_revokesEntireFamily_andPublishesAuditEvent() {
        RefreshToken reused = new RefreshToken(1L, "family-1", "hash", Instant.now(),
                Instant.now().plus(Duration.ofDays(1)));
        reused.revoke(RevokeReason.ROTATED, Instant.now().minus(Duration.ofMinutes(5)));
        RefreshToken sibling = new RefreshToken(1L, "family-1", "sibling-hash", Instant.now(),
                Instant.now().plus(Duration.ofDays(1)));

        when(tokenGenerator.hash("stolen-raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findAllByTokenFamilyId("family-1")).thenReturn(List.of(reused, sibling));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen-raw"))
                .isInstanceOf(RefreshTokenInvalidException.class);

        assertThat(sibling.isRevoked()).isTrue();
        assertThat(sibling.getRevokedReason()).isEqualTo(RevokeReason.REUSE_DETECTED);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(AuditEventType.TOKEN_REUSE_DETECTED);
    }

    @Test
    void revokeByRawToken_returnsUserId_whenTokenFound() {
        RefreshToken existing = new RefreshToken(42L, "family-1", "hash", Instant.now(),
                Instant.now().plus(Duration.ofDays(1)));
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(existing));

        Long userId = refreshTokenService.revokeByRawToken("raw");

        assertThat(userId).isEqualTo(42L);
        assertThat(existing.getRevokedReason()).isEqualTo(RevokeReason.LOGOUT);
    }

    @Test
    void revokeByRawToken_returnsNull_whenTokenNotFound() {
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThat(refreshTokenService.revokeByRawToken("raw")).isNull();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeAllForUser_revokesOnlyActiveTokens() {
        RefreshToken active1 = new RefreshToken(1L, "family-1", "hash1", Instant.now(), Instant.now().plus(Duration.ofDays(1)));
        RefreshToken active2 = new RefreshToken(1L, "family-2", "hash2", Instant.now(), Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(1L)).thenReturn(List.of(active1, active2));

        refreshTokenService.revokeAllForUser(1L);

        assertThat(active1.getRevokedReason()).isEqualTo(RevokeReason.ADMIN_DISABLED);
        assertThat(active2.getRevokedReason()).isEqualTo(RevokeReason.ADMIN_DISABLED);
        verify(refreshTokenRepository, times(1)).saveAll(List.of(active1, active2));
    }
}

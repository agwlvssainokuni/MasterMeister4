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

package cherry.mastermeister.auth.repository;

import cherry.mastermeister.auth.entity.RefreshToken;
import cherry.mastermeister.auth.entity.RevokeReason;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private Long persistUser(String email) {
        Instant now = Instant.now();
        User user = new User(email, "hash", "Taro Yamada", Language.ja, UserStatus.APPROVED, Role.USER, now, now, null);
        return userRepository.saveAndFlush(user).getId();
    }

    @Test
    void findByTokenHash_returnsSavedToken() {
        Long userId = persistUser("taro@example.com");
        Instant now = Instant.now();
        String familyId = UUID.randomUUID().toString();
        refreshTokenRepository.saveAndFlush(
                new RefreshToken(userId, familyId, "hashed-refresh-token", now, now.plus(1, ChronoUnit.DAYS)));

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("hashed-refresh-token");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().isRevoked()).isFalse();
    }

    @Test
    void findAllByTokenFamilyId_returnsAllTokensInFamily() {
        Long userId = persistUser("taro@example.com");
        Instant now = Instant.now();
        String familyId = UUID.randomUUID().toString();
        RefreshToken first = new RefreshToken(userId, familyId, "token-1", now, now.plus(1, ChronoUnit.DAYS));
        first.revoke(RevokeReason.ROTATED, now);
        refreshTokenRepository.saveAndFlush(first);
        refreshTokenRepository.saveAndFlush(
                new RefreshToken(userId, familyId, "token-2", now, now.plus(1, ChronoUnit.DAYS)));

        List<RefreshToken> family = refreshTokenRepository.findAllByTokenFamilyId(familyId);

        assertThat(family).hasSize(2);
    }

    @Test
    void findAllByUserIdAndRevokedAtIsNull_excludesRevokedTokens() {
        Long userId1 = persistUser("taro@example.com");
        Long userId2 = persistUser("hanako@example.com");
        Instant now = Instant.now();
        RefreshToken revoked = new RefreshToken(userId1, UUID.randomUUID().toString(), "revoked-token",
                now, now.plus(1, ChronoUnit.DAYS));
        revoked.revoke(RevokeReason.ADMIN_DISABLED, now);
        refreshTokenRepository.saveAndFlush(revoked);
        refreshTokenRepository.saveAndFlush(
                new RefreshToken(userId1, UUID.randomUUID().toString(), "active-token", now, now.plus(1, ChronoUnit.DAYS)));
        refreshTokenRepository.saveAndFlush(
                new RefreshToken(userId2, UUID.randomUUID().toString(), "other-user-token", now, now.plus(1, ChronoUnit.DAYS)));

        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId1);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getTokenHash()).isEqualTo("active-token");
    }
}

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

package cherry.mastermeister.registration.repository;

import cherry.mastermeister.registration.entity.RegistrationToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RegistrationTokenRepositoryTest {

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    @Test
    void findByTokenHash_returnsSavedToken() {
        Instant now = Instant.now();
        registrationTokenRepository.saveAndFlush(
                new RegistrationToken("taro@example.com", "hashed-token", now.plus(3, ChronoUnit.HOURS), now));

        Optional<RegistrationToken> found = registrationTokenRepository.findByTokenHash("hashed-token");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("taro@example.com");
        assertThat(found.get().isUsed()).isFalse();
    }

    @Test
    void findAllByEmailAndUsedAtIsNull_excludesUsedTokens() {
        Instant now = Instant.now();
        RegistrationToken used = new RegistrationToken("taro@example.com", "used-token",
                now.plus(3, ChronoUnit.HOURS), now);
        used.markUsed(now);
        registrationTokenRepository.saveAndFlush(used);
        registrationTokenRepository.saveAndFlush(
                new RegistrationToken("taro@example.com", "unused-token", now.plus(3, ChronoUnit.HOURS), now));

        List<RegistrationToken> unused =
                registrationTokenRepository.findAllByEmailAndUsedAtIsNull("taro@example.com");

        assertThat(unused).hasSize(1);
        assertThat(unused.get(0).getTokenHash()).isEqualTo("unused-token");
    }

    @Test
    void isExpired_reflectsExpiryInstant() {
        Instant now = Instant.now();
        RegistrationToken token = new RegistrationToken("taro@example.com", "hash", now.minusSeconds(1), now);

        assertThat(token.isExpired(now)).isTrue();
    }
}

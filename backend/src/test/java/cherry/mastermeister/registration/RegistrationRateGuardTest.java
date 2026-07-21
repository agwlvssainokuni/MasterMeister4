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

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.registration.entity.RegistrationRateState;
import cherry.mastermeister.registration.repository.RegistrationRateStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BR-REG-07。
 */
@ExtendWith(MockitoExtension.class)
class RegistrationRateGuardTest {

    @Mock
    private RegistrationRateStateRepository registrationRateStateRepository;

    private RegistrationRateGuard guard;

    @BeforeEach
    void setUp() {
        AppProperties.UserRegistration userRegistration =
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1));
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, Duration.ofMinutes(15)),
                userRegistration,
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"),
                new AppProperties.Rdbms("1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="));
        guard = new RegistrationRateGuard(registrationRateStateRepository, appProperties);
    }

    @Test
    void tryAcquire_allowsFirstRequest_whenNoExistingState() {
        when(registrationRateStateRepository.findById("taro@example.com")).thenReturn(Optional.empty());

        assertThat(guard.tryAcquire("taro@example.com")).isTrue();
    }

    @Test
    void tryAcquire_allowsUpToThreshold_thenRejects() {
        RegistrationRateState state = new RegistrationRateState("taro@example.com", 2, Instant.now());
        when(registrationRateStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        assertThat(guard.tryAcquire("taro@example.com")).isTrue();
        assertThat(state.getRequestCount()).isEqualTo(3);
    }

    @Test
    void tryAcquire_rejects_whenThresholdAlreadyReached() {
        RegistrationRateState state = new RegistrationRateState("taro@example.com", 3, Instant.now());
        when(registrationRateStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        assertThat(guard.tryAcquire("taro@example.com")).isFalse();
    }

    @Test
    void tryAcquire_resetsWindow_whenWindowExpired() {
        RegistrationRateState state = new RegistrationRateState("taro@example.com", 3,
                Instant.now().minus(Duration.ofHours(2)));
        when(registrationRateStateRepository.findById("taro@example.com")).thenReturn(Optional.of(state));

        assertThat(guard.tryAcquire("taro@example.com")).isTrue();
        assertThat(state.getRequestCount()).isEqualTo(1);
    }
}

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

import cherry.mastermeister.auth.entity.LoginAttemptState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LoginAttemptStateRepositoryTest {

    @Autowired
    private LoginAttemptStateRepository loginAttemptStateRepository;

    @Test
    void save_andFindById_roundTrips() {
        LoginAttemptState state = new LoginAttemptState("taro@example.com");
        state.recordFailure(Instant.now(), 5, Duration.ofMinutes(15));
        loginAttemptStateRepository.saveAndFlush(state);

        Optional<LoginAttemptState> found = loginAttemptStateRepository.findById("taro@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFailureCount()).isEqualTo(1);
    }
}

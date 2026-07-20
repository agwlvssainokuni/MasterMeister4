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

import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User newUser(String email, UserStatus status) {
        Instant now = Instant.now();
        return new User(email, "hash", "Taro Yamada", Language.ja, status, Role.USER, now, now, null);
    }

    @Test
    void findByEmail_returnsSavedUser() {
        userRepository.saveAndFlush(newUser("taro@example.com", UserStatus.PENDING));

        Optional<User> found = userRepository.findByEmail("taro@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void findByEmail_returnsEmpty_whenNotFound() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void emailUniqueConstraint_rejectsDuplicateAcrossAnyStatus() {
        userRepository.saveAndFlush(newUser("dup@example.com", UserStatus.REJECTED));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dup@example.com", UserStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByStatus_filtersByStatus() {
        userRepository.saveAndFlush(newUser("pending1@example.com", UserStatus.PENDING));
        userRepository.saveAndFlush(newUser("pending2@example.com", UserStatus.PENDING));
        userRepository.saveAndFlush(newUser("approved1@example.com", UserStatus.APPROVED));

        List<User> pending = userRepository.findAllByStatus(UserStatus.PENDING);

        assertThat(pending).hasSize(2)
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder("pending1@example.com", "pending2@example.com");
    }
}

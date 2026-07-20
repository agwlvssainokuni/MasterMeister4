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

import cherry.mastermeister.auth.entity.LoginAttemptState;
import cherry.mastermeister.auth.repository.LoginAttemptStateRepository;
import cherry.mastermeister.common.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * COMP-05。business-rules.md BR-LOGIN-01〜03。
 */
@Component
public class LoginAttemptGuard {

    private final LoginAttemptStateRepository loginAttemptStateRepository;
    private final AppProperties.LoginAttempt properties;

    public LoginAttemptGuard(LoginAttemptStateRepository loginAttemptStateRepository, AppProperties appProperties) {
        this.loginAttemptStateRepository = loginAttemptStateRepository;
        this.properties = appProperties.loginAttempt();
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        return loginAttemptStateRepository.findById(email)
                .map(state -> state.isLocked(Instant.now()))
                .orElse(false);
    }

    @Transactional
    public void recordFailure(String email) {
        LoginAttemptState state = loginAttemptStateRepository.findById(email)
                .orElseGet(() -> new LoginAttemptState(email));
        state.recordFailure(Instant.now(), properties.maxFailures(), properties.lockDuration());
        loginAttemptStateRepository.save(state);
    }

    @Transactional
    public void reset(String email) {
        loginAttemptStateRepository.findById(email).ifPresent(state -> {
            state.reset();
            loginAttemptStateRepository.save(state);
        });
    }
}

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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * business-rules.md BR-REG-07。登録開始（Step1）エンドポイントのレート制限。
 */
@Component
public class RegistrationRateGuard {

    private final RegistrationRateStateRepository registrationRateStateRepository;
    private final AppProperties.UserRegistration properties;

    public RegistrationRateGuard(RegistrationRateStateRepository registrationRateStateRepository,
                                  AppProperties appProperties) {
        this.registrationRateStateRepository = registrationRateStateRepository;
        this.properties = appProperties.userRegistration();
    }

    /**
     * @param email 対象メールアドレス
     * @return 閾値内であれば送信回数をインクリメントしtrueを返す。閾値到達済みであればfalseを返す（後続処理はスキップされる）
     */
    @Transactional
    public boolean tryAcquire(String email) {
        Instant now = Instant.now();
        RegistrationRateState state = registrationRateStateRepository.findById(email)
                .orElseGet(() -> new RegistrationRateState(email, 0, now));

        if (now.isAfter(state.getWindowStartAt().plus(properties.rateLimitWindow()))) {
            state.resetWindow(now);
        }

        if (state.getRequestCount() >= properties.rateLimitMaxRequests()) {
            return false;
        }

        state.increment();
        registrationRateStateRepository.save(state);
        return true;
    }
}

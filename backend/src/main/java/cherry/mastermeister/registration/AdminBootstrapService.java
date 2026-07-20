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
import cherry.mastermeister.registration.entity.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * COMP-02。business-logic-model.md §4、FR-1.14。
 */
@Component
public class AdminBootstrapService implements ApplicationRunner {

    private final UserRegistrationService userRegistrationService;
    private final AppProperties.AdminBootstrap properties;

    public AdminBootstrapService(UserRegistrationService userRegistrationService, AppProperties appProperties) {
        this.userRegistrationService = userRegistrationService;
        this.properties = appProperties.adminBootstrap();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            return;
        }
        userRegistrationService.createApprovedAccount(properties.email(), properties.password(), Role.ADMIN);
    }
}

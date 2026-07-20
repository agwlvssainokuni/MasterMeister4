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

package cherry.mastermeister.registration.api;

import cherry.mastermeister.registration.UserRegistrationService;
import cherry.mastermeister.registration.dto.RegistrationCompleteRequest;
import cherry.mastermeister.registration.dto.RegistrationStartRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * frontend-components.md §2〜3。{@code permitAll()}（nfr-design-patterns.md §3.6）。
 */
@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final UserRegistrationService userRegistrationService;

    public RegistrationController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping
    public ResponseEntity<Void> startRegistration(@RequestBody @Valid RegistrationStartRequest request) {
        userRegistrationService.startRegistration(request.email(), request.language());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{token}/complete")
    public ResponseEntity<Void> completeRegistration(@PathVariable String token,
                                                       @RequestBody @Valid RegistrationCompleteRequest request) {
        userRegistrationService.completeRegistration(token, request.fullName(), request.preferredLanguage(),
                request.password());
        return ResponseEntity.ok().build();
    }
}

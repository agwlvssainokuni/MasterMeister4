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
import cherry.mastermeister.registration.dto.UserSummaryResponse;
import cherry.mastermeister.registration.entity.UserStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * frontend-components.md §4。{@code hasRole('ADMIN')}（nfr-design-patterns.md §3.6）。
 * 承認・却下・却下取消（REJECTED→APPROVEDも同一approveエンドポイント、business-logic-model.md §2.1）・
 * 無効化・再有効化を単一のユーザ管理画面から扱う（レビュー指摘の反映）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRegistrationService userRegistrationService;

    public AdminUserController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) UserStatus status) {
        List<UserSummaryResponse> users = userRegistrationService.listUsers(Optional.ofNullable(status)).stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id, @AuthenticationPrincipal Jwt principal) {
        userRegistrationService.approveUser(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @AuthenticationPrincipal Jwt principal) {
        userRegistrationService.rejectUser(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id, @AuthenticationPrincipal Jwt principal) {
        userRegistrationService.disableUser(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id, @AuthenticationPrincipal Jwt principal) {
        userRegistrationService.enableUser(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}

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

import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.registration.UserRegistrationService;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * frontend-components.md §4。承認/却下/無効化/再有効化を単一のユーザ管理画面から扱う。
 * 実際のSecurityFilterChain（{@link SecurityConfig}）を有効にし、ADMIN権限のJWTでアクセスする
 * （{@code addFilters=false}では{@code @AuthenticationPrincipal}にJWTのSecurityContextが渡らないため）。
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("99"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void listUsers_returnsSummaries() throws Exception {
        User user = new User("taro@example.com", "hashed", "Taro Yamada", Language.ja, UserStatus.PENDING,
                Role.USER, Instant.now(), Instant.now(), null);
        when(userRegistrationService.listUsers(Optional.of(UserStatus.PENDING))).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users").param("status", "PENDING").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("taro@example.com"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void approve_delegatesToService_withCurrentUserIdFromJwtSubject() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/approve").with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).approveUser(eq(1L), eq(99L));
    }

    @Test
    void reject_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/reject").with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).rejectUser(eq(1L), eq(99L));
    }

    @Test
    void disable_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/disable").with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).disableUser(eq(1L), eq(99L));
    }

    @Test
    void enable_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/enable").with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).enableUser(eq(1L), eq(99L));
    }
}

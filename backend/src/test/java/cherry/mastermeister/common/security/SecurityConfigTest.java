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

package cherry.mastermeister.common.security;

import cherry.mastermeister.auth.AuthenticationService;
import cherry.mastermeister.auth.api.AuthController;
import cherry.mastermeister.registration.UserRegistrationService;
import cherry.mastermeister.registration.api.AdminUserController;
import cherry.mastermeister.registration.api.RegistrationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * nfr-design-patterns.md §3.6。SecurityFilterChainの認可ルールを、実フィルタチェーンを有効にして検証する
 * （Step 11.4）。他のコントローラテスト（RegistrationControllerTest等）はaddFilters=falseで無効化しているため、
 * ここでのみ実際の401/403/permitAllの挙動を確認する。
 */
@WebMvcTest({AdminUserController.class, RegistrationController.class, AuthController.class})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    void adminEndpoint_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_returnsForbidden_whenAuthenticatedWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_returnsOk_whenAuthenticatedWithAdminRole() throws Exception {
        when(userRegistrationService.listUsers(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void registrationEndpoint_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"taro@example.com\",\"language\":\"ja\"}"))
                .andExpect(status().isOk());
    }
}

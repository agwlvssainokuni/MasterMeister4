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

package cherry.mastermeister.audit.api;

import cherry.mastermeister.audit.AuditLogQueryService;
import cherry.mastermeister.audit.dto.AuditLogEntryResponse;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * logical-components.md §1。BR-AUDITVIEW-03（管理者専用エンドポイント）。
 */
@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("99")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void listAuditLog_returnsOk() throws Exception {
        AuditLogEntryResponse entry = new AuditLogEntryResponse(1L, Instant.now(), 1L, "山田太郎", null, null,
                AuditEventType.LOGIN, null, cherry.mastermeister.audit.entity.ResultStatus.SUCCESS, null);
        when(auditLogQueryService.listAuditLog(any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/admin/audit-log").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userDisplayName").value("山田太郎"));
    }

    @Test
    void listAuditLog_returnsBadRequest_whenPageSizeExceedsMax() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log").param("pageSize", "500").with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_INVALID_PARAMETER"));
    }

    @Test
    void listAuditLog_returnsBadRequest_whenDateRangeIsInverted() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log")
                        .param("occurredAtFrom", "2026-07-27T00:00:00Z")
                        .param("occurredAtTo", "2026-07-26T00:00:00Z")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_INVALID_PARAMETER"));
    }

    @Test
    void listAuditLog_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAuditLog_returnsForbidden_whenAuthenticatedWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}

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

package cherry.mastermeister.queryhistory.api;

import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.queryhistory.QueryHistoryService;
import cherry.mastermeister.queryhistory.dto.QueryHistoryConnectionResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistoryRecordResponse;
import cherry.mastermeister.queryhistory.dto.QueryType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * logical-components.md §1。BR-QUERYHISTORY-03（実行者スコープのフェイルクローズ）を
 * 実フィルタチェーン経由で確認する。
 */
@WebMvcTest(QueryHistoryController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class QueryHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryHistoryService queryHistoryService;

    private static RequestPostProcessor generalUserJwt() {
        return jwt().jwt(builder -> builder.subject("42").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("1").claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void listConnections_accessibleByGeneralUser() throws Exception {
        when(queryHistoryService.listConnections(42L))
                .thenReturn(List.of(new QueryHistoryConnectionResponse(1L, "接続A")));

        mockMvc.perform(get("/api/query-history/connections").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("接続A"));
    }

    @Test
    void listConnections_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/query-history/connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listConnections_generalUserForcedToMine_evenWhenRequestingAll() throws Exception {
        when(queryHistoryService.listConnections(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/query-history/connections").param("executedByScope", "ALL")
                        .with(generalUserJwt()))
                .andExpect(status().isOk());

        verify(queryHistoryService).listConnections(42L);
    }

    @Test
    void listConnections_adminCanRequestAll() throws Exception {
        when(queryHistoryService.listConnections(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/query-history/connections").param("executedByScope", "ALL").with(adminJwt()))
                .andExpect(status().isOk());

        verify(queryHistoryService).listConnections(isNull());
    }

    @Test
    void listSchemas_returnsSchemaNames() throws Exception {
        when(queryHistoryService.listSchemas(1L, 42L)).thenReturn(List.of("public", "sales"));

        mockMvc.perform(get("/api/query-history/1/schemas").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("public"))
                .andExpect(jsonPath("$[1]").value("sales"));
    }

    @Test
    void listHistory_returnsHistoryPage() throws Exception {
        QueryHistoryRecordResponse record = new QueryHistoryRecordResponse(1L, 42L, "山田太郎", 1L, "public",
                "SELECT 1", null, null, QueryType.AD_HOC, 3L, 5L, Instant.now());
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "executedAt"));
        Page<QueryHistoryRecordResponse> page = new PageImpl<>(List.of(record), pageable, 1);
        when(queryHistoryService.listHistory(eq(1L), eq(42L), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/query-history/1").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sql").value("SELECT 1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listHistory_returnsBadRequest_whenPageSizeExceedsMax() throws Exception {
        mockMvc.perform(get("/api/query-history/1").param("pageSize", "500").with(generalUserJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_HISTORY_INVALID_PARAMETER"));
    }

    @Test
    void listHistory_returnsBadRequest_whenDateRangeInvalid() throws Exception {
        mockMvc.perform(get("/api/query-history/1")
                        .param("executedAtFrom", "2026-07-26T00:00:00Z")
                        .param("executedAtTo", "2026-07-01T00:00:00Z")
                        .with(generalUserJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_HISTORY_INVALID_PARAMETER"));
    }
}

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

package cherry.mastermeister.query.api;

import cherry.mastermeister.common.exception.NonReadOnlyQueryException;
import cherry.mastermeister.common.exception.QuerySchemaNotAccessibleException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.query.QueryExecutionService;
import cherry.mastermeister.query.model.AccessibleConnection;
import cherry.mastermeister.query.model.QueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * frontend-components.md Flow A-1/B-1/B-2。実フィルタチェーンを有効化し、一般ユーザ
 * （非ADMIN）でもアクセス可能なことを確認する（既存の{@code /api/**}→authenticated()
 * ルールがそのまま適用されることの実証、Step 9.1）。
 */
@WebMvcTest(QueryController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryExecutionService queryExecutionService;

    private static RequestPostProcessor generalUserJwt() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void listConnections_accessibleByGeneralUser_notJustAdmin() throws Exception {
        when(queryExecutionService.listAccessibleConnections(42L))
                .thenReturn(List.of(new AccessibleConnection(1L, "接続A")));

        mockMvc.perform(get("/api/queries/connections").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("接続A"));
    }

    @Test
    void listConnections_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/queries/connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSchemas_returnsAccessibleSchemas() throws Exception {
        when(queryExecutionService.listAccessibleSchemas(42L, 1L)).thenReturn(List.of("public", "sales"));

        mockMvc.perform(get("/api/queries/1/schemas").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schemaName").value("public"))
                .andExpect(jsonPath("$[1].schemaName").value("sales"));
    }

    @Test
    void execute_returnsQueryResult() throws Exception {
        QueryResult result = new QueryResult(List.of("id"), List.of(Map.of("id", "1")), null, null, null, 1L, 5L);
        when(queryExecutionService.execute(eq(42L), eq(1L), eq("SELECT * FROM t"), any(), eq("public"),
                anyBoolean(), anyInt(), anyInt())).thenReturn(result);

        mockMvc.perform(post("/api/queries/1/execute")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sql":"SELECT * FROM t","schemaName":"public","params":{},"pagingEnabled":false,
                                "page":0,"pageSize":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value("1"));
    }

    @Test
    void execute_returnsBadRequest_whenNonReadOnly() throws Exception {
        when(queryExecutionService.execute(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new NonReadOnlyQueryException());

        mockMvc.perform(post("/api/queries/1/execute")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sql":"DELETE FROM t","schemaName":"public","params":{},"pagingEnabled":false,
                                "page":0,"pageSize":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NON_READ_ONLY_QUERY"));
    }

    @Test
    void execute_returnsForbidden_whenSchemaNotAccessible() throws Exception {
        when(queryExecutionService.execute(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new QuerySchemaNotAccessibleException());

        mockMvc.perform(post("/api/queries/1/execute")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sql":"SELECT * FROM t","schemaName":"secret","params":{},"pagingEnabled":false,
                                "page":0,"pageSize":0}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUERY_SCHEMA_NOT_ACCESSIBLE"));
    }

    @Test
    void execute_returnsBadRequest_whenSqlBlank() throws Exception {
        mockMvc.perform(post("/api/queries/1/execute")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sql":"","schemaName":"public","params":{},"pagingEnabled":false,
                                "page":0,"pageSize":0}
                                """))
                .andExpect(status().isBadRequest());
    }
}

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

package cherry.mastermeister.rdbmsconnection.api;

import cherry.mastermeister.common.exception.RdbmsConnectionNotFoundException;
import cherry.mastermeister.common.exception.SchemaNotImportedException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.rdbmsconnection.ConnectionErrorCategory;
import cherry.mastermeister.rdbmsconnection.ConnectionTestOutcome;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionService;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * frontend-components.md §1〜2。BR-RDBMS-12（パスワード非公開）を含む。
 */
@WebMvcTest(RdbmsConnectionController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class RdbmsConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RdbmsConnectionService rdbmsConnectionService;
    @MockitoBean
    private SchemaIntrospectionService schemaIntrospectionService;

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("99")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RdbmsConnection sampleConnection() {
        Instant now = Instant.now();
        return new RdbmsConnection("接続1", DbType.MYSQL, "localhost", 3306, "mastermeister", null, "root",
                "encrypted", 1, null, now, now);
    }

    @Test
    void list_returnsConnections_withoutPasswordField() throws Exception {
        when(rdbmsConnectionService.listConnections()).thenReturn(List.of(sampleConnection()));
        when(schemaIntrospectionService.getSchema(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/rdbms-connections").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("接続1"))
                .andExpect(jsonPath("$[0].encryptedPassword").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].schemaImportedAt").doesNotExist());
    }

    @Test
    void register_delegatesToService() throws Exception {
        when(rdbmsConnectionService.registerConnection(any(), any(), any(), anyInt(), any(), any(), any(), any(),
                any(), eq(99L))).thenReturn(sampleConnection());
        when(schemaIntrospectionService.getSchema(any())).thenReturn(Optional.empty());

        String body = """
                {"displayName":"接続1","dbType":"MYSQL","host":"localhost","port":3306,
                 "databaseName":"mastermeister","username":"root","password":"s3cr3t"}
                """;

        mockMvc.perform(post("/api/admin/rdbms-connections").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(rdbmsConnectionService).registerConnection(eq("接続1"), eq(DbType.MYSQL), eq("localhost"), eq(3306),
                eq("mastermeister"), any(), eq("root"), eq("s3cr3t"), any(), eq(99L));
    }

    @Test
    void register_rejectsInvalidPort_withValidationError() throws Exception {
        String body = """
                {"displayName":"接続1","dbType":"MYSQL","host":"localhost","port":70000,
                 "databaseName":"mastermeister","username":"root","password":"s3cr3t"}
                """;

        mockMvc.perform(post("/api/admin/rdbms-connections").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/admin/rdbms-connections/1").with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(rdbmsConnectionService).deleteConnection(1L, 99L);
    }

    @Test
    void update_returnsNotFound_whenConnectionMissing() throws Exception {
        when(rdbmsConnectionService.updateConnection(eq(1L), any(), any(), any(), anyInt(), any(), any(), any(),
                any(), any(), eq(99L))).thenThrow(new RdbmsConnectionNotFoundException());
        String body = """
                {"displayName":"接続1","dbType":"MYSQL","host":"localhost","port":3306,
                 "databaseName":"mastermeister","username":"root"}
                """;

        mockMvc.perform(put("/api/admin/rdbms-connections/1").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RDBMS_CONNECTION_NOT_FOUND"));
    }

    @Test
    void testSaved_returnsClassifiedFailure() throws Exception {
        when(rdbmsConnectionService.testConnection(1L))
                .thenReturn(ConnectionTestOutcome.ofFailure(ConnectionErrorCategory.AUTH_ERROR));

        mockMvc.perform(post("/api/admin/rdbms-connections/1/test").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCategory").value("AUTH_ERROR"));
    }

    @Test
    void testUnsaved_delegatesToServiceWithoutPersisting() throws Exception {
        when(rdbmsConnectionService.testConnectionUnsaved(eq(DbType.MYSQL), eq("localhost"), eq(3306),
                eq("mastermeister"), any(), eq("root"), eq("s3cr3t"), any()))
                .thenReturn(ConnectionTestOutcome.ofSuccess());
        String body = """
                {"dbType":"MYSQL","host":"localhost","port":3306,"databaseName":"mastermeister",
                 "username":"root","password":"s3cr3t"}
                """;

        mockMvc.perform(post("/api/admin/rdbms-connections/test").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getSchema_returnsNotFound_whenNotYetImported() throws Exception {
        when(schemaIntrospectionService.getSchema(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/rdbms-connections/1/schema").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEMA_NOT_IMPORTED"));
    }

    @Test
    void adminEndpoint_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/rdbms-connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_returnsForbidden_whenAuthenticatedWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/rdbms-connections")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}

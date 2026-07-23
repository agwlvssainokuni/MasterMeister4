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

package cherry.mastermeister.permission.api;

import cherry.mastermeister.common.exception.PermissionYamlImportRejectedException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.permission.PermissionService;
import cherry.mastermeister.permission.PermissionYamlService;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PermissionController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private PermissionYamlService permissionYamlService;

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("99")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void list_returnsPermissions() throws Exception {
        when(permissionService.listPermissions(1L, PrincipalType.USER, 42L)).thenReturn(List.of(
                new AccessPermission(1L, PrincipalType.USER, 42L, "public", "products", null, PrimaryPermission.READ,
                        false, false, Instant.now(), 1L)));

        mockMvc.perform(get("/api/admin/permissions/1")
                        .param("principalType", "USER")
                        .param("principalId", "42")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("products"));
    }

    @Test
    void set_upsertsPermission() throws Exception {
        when(permissionService.setPermission(eq(1L), eq(PrincipalType.USER), eq(42L), eq("public"), eq("products"),
                any(), eq(PrimaryPermission.READ), eq(false), eq(false), anyLong())).thenReturn(
                new AccessPermission(1L, PrincipalType.USER, 42L, "public", "products", null, PrimaryPermission.READ,
                        false, false, Instant.now(), 1L));

        mockMvc.perform(put("/api/admin/permissions/1").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principalType":"USER","principalId":42,"schemaName":"public",
                                 "tableName":"products","primaryPermission":"READ",
                                 "createPermission":false,"deletePermission":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryPermission").value("READ"));
    }

    @Test
    void unset_requiresPrincipalAndSchemaQueryParams() throws Exception {
        mockMvc.perform(delete("/api/admin/permissions/1").with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unset_returnsNoContentWhenTargetKeyProvided() throws Exception {
        mockMvc.perform(delete("/api/admin/permissions/1")
                        .param("principalType", "USER")
                        .param("principalId", "42")
                        .param("schemaName", "public")
                        .param("tableName", "products")
                        .with(adminJwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void importYaml_rejectsOversizedPayload() throws Exception {
        String oversized = "x".repeat(1_048_577);

        mockMvc.perform(post("/api/admin/permissions/1/import").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yaml\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importYaml_returns422_whenRejectedByService() throws Exception {
        org.mockito.Mockito.doThrow(new PermissionYamlImportRejectedException("未解決のプリンシパル: USER:unknown@example.com"))
                .when(permissionYamlService).importFromYaml(eq(1L), any(), anyLong());

        mockMvc.perform(post("/api/admin/permissions/1/import").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yaml\":\"connectionId: 1\\npermissions: []\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERMISSION_YAML_IMPORT_REJECTED"));
    }

    @Test
    void adminEndpoint_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/permissions/1").param("principalType", "USER").param("principalId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_returnsForbidden_whenAuthenticatedWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/permissions/1").param("principalType", "USER").param("principalId", "1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}

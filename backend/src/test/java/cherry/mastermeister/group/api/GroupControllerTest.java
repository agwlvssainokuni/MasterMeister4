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

package cherry.mastermeister.group.api;

import cherry.mastermeister.common.exception.GroupNotFoundException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.group.GroupService;
import cherry.mastermeister.group.entity.Group;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("99")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void list_returnsGroups() throws Exception {
        when(groupService.listGroups()).thenReturn(List.of(new Group("営業", Instant.now())));

        mockMvc.perform(get("/api/admin/groups").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("営業"));
    }

    @Test
    void create_rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/admin/groups").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returnsCreatedGroup() throws Exception {
        when(groupService.createGroup(eq("経理"), anyLong())).thenReturn(new Group("経理", Instant.now()));

        mockMvc.perform(post("/api/admin/groups").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"経理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("経理"));
    }

    @Test
    void delete_returnsNotFound_whenGroupDoesNotExist() throws Exception {
        org.mockito.Mockito.doThrow(new GroupNotFoundException()).when(groupService).deleteGroup(eq(1L), anyLong());

        mockMvc.perform(delete("/api/admin/groups/1").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void delete_returnsNoContent_onSuccess() throws Exception {
        mockMvc.perform(delete("/api/admin/groups/1").with(adminJwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminEndpoint_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/groups")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_returnsForbidden_whenAuthenticatedWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/groups")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}

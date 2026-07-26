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

import cherry.mastermeister.common.exception.SavedQueryNotAccessibleException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.query.QueryExecutionService;
import cherry.mastermeister.query.SavedQueryService;
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.entity.Visibility;
import cherry.mastermeister.query.model.QueryResult;
import cherry.mastermeister.query.model.VisibilityFilter;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * frontend-components.md Flow A-2/A-3/A-4。実フィルタチェーンを有効化し、一般ユーザ
 * （非ADMIN）でもアクセス可能なことを確認する（Step 9.2）。
 */
@WebMvcTest(SavedQueryController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class SavedQueryControllerTest {

    private static final Long CONNECTION_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavedQueryService savedQueryService;

    @MockitoBean
    private QueryExecutionService queryExecutionService;

    private static RequestPostProcessor generalUserJwt() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static SavedQuery savedQuery(Long id, Long createdBy, Visibility visibility) {
        Instant now = Instant.now();
        SavedQuery savedQuery = new SavedQuery(CONNECTION_ID, "クエリ", "SELECT 1", visibility, createdBy, now);
        try {
            var field = SavedQuery.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(savedQuery, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return savedQuery;
    }

    @Test
    void list_accessibleByGeneralUser_notJustAdmin() throws Exception {
        when(savedQueryService.listSavedQueries(42L, CONNECTION_ID, VisibilityFilter.ALL, false))
                .thenReturn(List.of(savedQuery(1L, 42L, Visibility.PUBLIC)));

        mockMvc.perform(get("/api/queries/1/saved").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].own").value(true));
    }

    @Test
    void list_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/queries/1/saved"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returnsCreatedSavedQuery() throws Exception {
        when(savedQueryService.saveQuery(42L, CONNECTION_ID, "売上", "SELECT 1", Visibility.PUBLIC))
                .thenReturn(savedQuery(1L, 42L, Visibility.PUBLIC));

        mockMvc.perform(post("/api/queries/1/saved")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"売上","sql":"SELECT 1","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("クエリ"));
    }

    @Test
    void get_returnsNotFound_whenNotAccessible() throws Exception {
        when(savedQueryService.getSavedQuery(42L, CONNECTION_ID, 1L))
                .thenThrow(new SavedQueryNotAccessibleException());

        mockMvc.perform(get("/api/queries/1/saved/1").with(generalUserJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SAVED_QUERY_NOT_ACCESSIBLE"));
    }

    @Test
    void update_returnsUpdatedSavedQuery() throws Exception {
        when(savedQueryService.updateQuery(42L, CONNECTION_ID, 1L, "改名", "SELECT 2", Visibility.PRIVATE))
                .thenReturn(savedQuery(1L, 42L, Visibility.PRIVATE));

        mockMvc.perform(put("/api/queries/1/saved/1")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"改名","sql":"SELECT 2","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void update_returnsNotFound_whenNotOwner() throws Exception {
        when(savedQueryService.updateQuery(any(), any(), any(), any(), any(), any()))
                .thenThrow(new SavedQueryNotAccessibleException());

        mockMvc.perform(put("/api/queries/1/saved/1")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"改名","sql":"SELECT 2","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void execute_returnsQueryResult() throws Exception {
        QueryResult result = new QueryResult(List.of("id"), List.of(Map.of("id", "1")), null, null, null, 1L, 5L);
        when(queryExecutionService.executeSavedQuery(eq(42L), eq(CONNECTION_ID), eq(1L), any(), eq("public"),
                anyBoolean(), anyInt(), anyInt())).thenReturn(result);

        mockMvc.perform(post("/api/queries/1/saved/1/execute")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaName":"public","params":{},"pagingEnabled":false,"page":0,"pageSize":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value("1"));
    }

    @Test
    void retire_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/queries/1/saved/1/retire").with(generalUserJwt()))
                .andExpect(status().isOk());

        verify(savedQueryService).retireQuery(42L, CONNECTION_ID, 1L);
    }

    @Test
    void retire_returnsNotFound_whenNotOwner() throws Exception {
        org.mockito.Mockito.doThrow(new SavedQueryNotAccessibleException())
                .when(savedQueryService).retireQuery(any(), any(), any());

        mockMvc.perform(post("/api/queries/1/saved/1/retire").with(generalUserJwt()))
                .andExpect(status().isNotFound());
    }
}

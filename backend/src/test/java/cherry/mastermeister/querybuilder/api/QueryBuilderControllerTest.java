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

package cherry.mastermeister.querybuilder.api;

import cherry.mastermeister.common.exception.QueryBuilderInvalidGroupByException;
import cherry.mastermeister.common.exception.QueryBuilderReferenceNotAccessibleException;
import cherry.mastermeister.common.exception.QueryBuilderUnsupportedSqlException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.querybuilder.QueryBuilderAccessResolver;
import cherry.mastermeister.querybuilder.QueryBuilderService;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderColumnResponse;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderTableResponse;
import cherry.mastermeister.querybuilder.dto.ColumnDataTypeCategory;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateResponse;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * frontend-components.md 画面2。実フィルタチェーンを有効化し、一般ユーザ（非ADMIN）でも
 * アクセス可能なことを確認する（既存の{@code /api/**}→authenticated()ルールの実証）。
 */
@WebMvcTest(QueryBuilderController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class QueryBuilderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryBuilderAccessResolver accessResolver;

    @MockitoBean
    private QueryBuilderService queryBuilderService;

    private static RequestPostProcessor generalUserJwt() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void listTables_accessibleByGeneralUser_notJustAdmin() throws Exception {
        when(accessResolver.listAccessibleTables(42L, 1L, "public")).thenReturn(List.of(
                new AccessibleBuilderTableResponse("items", TableType.TABLE,
                        List.of(new AccessibleBuilderColumnResponse("id", ColumnDataTypeCategory.NUMERIC)))));

        mockMvc.perform(get("/api/query-builder/1/tables").param("schemaName", "public").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("items"));
    }

    @Test
    void listTables_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/query-builder/1/tables").param("schemaName", "public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generate_returnsGeneratedSql() throws Exception {
        when(queryBuilderService.generateSql(any())).thenReturn("SELECT t1.id FROM items AS t1");

        mockMvc.perform(post("/api/query-builder/1/generate")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":{"schemaName":"public","tableName":"items","alias":"t1"},
                                "joins":[],
                                "selectItems":[{"column":{"tableAlias":"t1","columnName":"id"},"aggregate":null,"alias":null}],
                                "whereConditions":[],"groupByColumns":[],"havingConditions":[],"orderByItems":[],
                                "limit":null,"offset":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT t1.id FROM items AS t1"));
    }

    @Test
    void generate_returnsBadRequest_whenSelectItemsEmpty() throws Exception {
        mockMvc.perform(post("/api/query-builder/1/generate")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":{"schemaName":"public","tableName":"items","alias":"t1"},
                                "joins":[], "selectItems":[], "whereConditions":[],"groupByColumns":[],
                                "havingConditions":[],"orderByItems":[],"limit":null,"offset":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_returnsBadRequest_whenGroupByInvalid() throws Exception {
        when(queryBuilderService.generateSql(any())).thenThrow(new QueryBuilderInvalidGroupByException());

        mockMvc.perform(post("/api/query-builder/1/generate")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":{"schemaName":"public","tableName":"items","alias":"t1"},
                                "joins":[],
                                "selectItems":[{"column":{"tableAlias":"t1","columnName":"id"},"aggregate":null,"alias":null}],
                                "whereConditions":[],"groupByColumns":[],"havingConditions":[],"orderByItems":[],
                                "limit":null,"offset":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_BUILDER_INVALID_GROUP_BY"));
    }

    @Test
    void parse_returnsBuilderState() throws Exception {
        QueryBuilderStateResponse response = new QueryBuilderStateResponse(null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, null);
        when(queryBuilderService.parseToBuilderState(42L, 1L, "public", "SELECT t1.id FROM items t1"))
                .thenReturn(response);

        mockMvc.perform(post("/api/query-builder/1/parse")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaName":"public","sql":"SELECT t1.id FROM items t1"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void parse_returnsUnprocessableEntity_whenUnsupportedSql() throws Exception {
        when(queryBuilderService.parseToBuilderState(anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new QueryBuilderUnsupportedSqlException());

        mockMvc.perform(post("/api/query-builder/1/parse")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaName":"public","sql":"SELECT id FROM t1 UNION SELECT id FROM t2"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("QUERY_BUILDER_UNSUPPORTED_SQL"));
    }

    @Test
    void parse_returnsForbidden_whenReferenceNotAccessible() throws Exception {
        when(queryBuilderService.parseToBuilderState(anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new QueryBuilderReferenceNotAccessibleException());

        mockMvc.perform(post("/api/query-builder/1/parse")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaName":"public","sql":"SELECT t1.secret FROM items t1"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUERY_BUILDER_REFERENCE_NOT_ACCESSIBLE"));
    }
}

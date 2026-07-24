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

package cherry.mastermeister.masterdata.api;

import cherry.mastermeister.common.exception.BatchSizeExceededException;
import cherry.mastermeister.common.exception.InvalidQueryConditionException;
import cherry.mastermeister.common.security.SecurityConfig;
import cherry.mastermeister.masterdata.MasterDataService;
import cherry.mastermeister.masterdata.model.AccessibleConnection;
import cherry.mastermeister.masterdata.model.AccessibleTable;
import cherry.mastermeister.masterdata.model.BatchOperationResult;
import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.masterdata.model.RecordFilterCondition;
import cherry.mastermeister.masterdata.model.RecordPage;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * frontend-components.md §1〜3。nfr-design-patterns.md §3.2。
 * 実フィルタチェーンを有効化し、一般ユーザ（非ADMIN）でもアクセス可能なことを確認する
 * （既存の{@code /api/**}→authenticated()ルールがそのまま適用されることの実証、Step 9.1）。
 */
@WebMvcTest(MasterDataController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(cherry.mastermeister.common.config.AppProperties.class)
@TestPropertySource(properties = {
        "mm.app.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "mm.app.rdbms.encryption-keys=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class MasterDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MasterDataService masterDataService;

    private static RequestPostProcessor generalUserJwt() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void listConnections_accessibleByGeneralUser_notJustAdmin() throws Exception {
        when(masterDataService.listAccessibleConnections(42L))
                .thenReturn(List.of(new AccessibleConnection(1L, "接続A")));

        mockMvc.perform(get("/api/master-data/connections").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("接続A"));
    }

    @Test
    void listConnections_returnsUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/master-data/connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTables_returnsAccessibleTables() throws Exception {
        when(masterDataService.listAccessibleTables(42L, 1L)).thenReturn(List.of(
                new AccessibleTable("public", "products", TableType.TABLE, true, false)));

        mockMvc.perform(get("/api/master-data/1/tables").with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("products"))
                .andExpect(jsonPath("$[0].creatable").value(true));
    }

    @Test
    void listRecords_returnsPageWithColumnsAndRows() throws Exception {
        RecordPage page = new RecordPage(
                List.of(new RecordColumn("id", ColumnDataTypeCategory.NUMERIC, true, false)),
                List.of(Map.of("id", "1")), 0, 50, 1, true, true);
        when(masterDataService.getRecords(eq(42L), eq(1L), eq("public"), eq("products"), anyList(), any(), any(),
                anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/master-data/1/tables/public/products/records")
                        .with(generalUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.rows[0].id").value("1"));
    }

    @Test
    void listRecords_parsesStructuredFilterQueryParam() throws Exception {
        when(masterDataService.getRecords(eq(42L), eq(1L), eq("public"), eq("products"),
                eq(List.of(new RecordFilterCondition("name", cherry.mastermeister.masterdata.model.FilterOperator.EQ,
                        "Alice", null))),
                any(), any(), anyInt(), anyInt()))
                .thenReturn(new RecordPage(List.of(), List.of(), 0, 50, 0, true, true));

        mockMvc.perform(get("/api/master-data/1/tables/public/products/records")
                        .param("filter", "[{\"columnName\":\"name\",\"operator\":\"EQ\",\"value\":\"Alice\"}]")
                        .with(generalUserJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void listRecords_returnsBadRequest_whenFilterParamIsMalformedJson() throws Exception {
        mockMvc.perform(get("/api/master-data/1/tables/public/products/records")
                        .param("filter", "not-json")
                        .with(generalUserJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_CONDITION"));
    }

    @Test
    void listRecords_returnsBadRequest_whenRawWhereRejected() throws Exception {
        when(masterDataService.getRecords(anyLong(), anyLong(), anyString(), anyString(), anyList(), any(), any(),
                anyInt(), anyInt())).thenThrow(new InvalidQueryConditionException());

        mockMvc.perform(get("/api/master-data/1/tables/public/products/records")
                        .param("where", "1=1; DROP TABLE x")
                        .with(generalUserJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_CONDITION"));
    }

    @Test
    void applyBatch_returnsResult_onSuccess() throws Exception {
        when(masterDataService.applyBatch(eq(42L), eq(1L), eq("public"), eq("products"), any()))
                .thenReturn(new BatchOperationResult(true, List.of()));

        mockMvc.perform(post("/api/master-data/1/tables/public/products/records/batch")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operations":[{"operationType":"DELETE","primaryKeyValues":{"id":"1"}}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void applyBatch_returnsBadRequest_whenOperationsEmpty() throws Exception {
        mockMvc.perform(post("/api/master-data/1/tables/public/products/records/batch")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operations\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyBatch_returnsBadRequest_whenBatchSizeExceeded() throws Exception {
        when(masterDataService.applyBatch(eq(42L), eq(1L), eq("public"), eq("products"), any()))
                .thenThrow(new BatchSizeExceededException(1000));

        mockMvc.perform(post("/api/master-data/1/tables/public/products/records/batch")
                        .with(generalUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operations":[{"operationType":"DELETE","primaryKeyValues":{"id":"1"}}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BATCH_SIZE_EXCEEDED"));
    }
}

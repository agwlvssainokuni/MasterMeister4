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

package cherry.mastermeister.querybuilder;

import cherry.mastermeister.common.exception.QuerySchemaNotAccessibleException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.query.QueryExecutionService;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderTableResponse;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §1。BR-QUERYBUILDER-01。
 */
class QueryBuilderAccessResolverTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;
    private static final String SCHEMA = "public";

    private SchemaIntrospectionService schemaIntrospectionService;
    private EffectivePermissionResolver effectivePermissionResolver;
    private QueryExecutionService queryExecutionService;
    private QueryBuilderAccessResolver resolver;

    @BeforeEach
    void setUp() {
        schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        effectivePermissionResolver = mock(EffectivePermissionResolver.class);
        queryExecutionService = mock(QueryExecutionService.class);
        when(queryExecutionService.listAccessibleSchemas(USER_ID, CONNECTION_ID)).thenReturn(List.of(SCHEMA));
        resolver = new QueryBuilderAccessResolver(schemaIntrospectionService, effectivePermissionResolver,
                queryExecutionService, new QueryBuilderColumnTypeMapper());
    }

    private SchemaTable tableWithColumns(String tableName, String... columnNames) {
        SchemaTable table = new SchemaTable(SCHEMA, tableName, TableType.TABLE, null);
        int i = 0;
        for (String columnName : columnNames) {
            table.addColumn(new SchemaColumn(columnName, i++, null, "VARCHAR", NormalizedType.STRING, true, null));
        }
        return table;
    }

    @Test
    void listAccessibleTables_rejectsSchemaNotInAllowList() {
        when(queryExecutionService.listAccessibleSchemas(USER_ID, CONNECTION_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> resolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA))
                .isInstanceOf(QuerySchemaNotAccessibleException.class);
    }

    @Test
    void listAccessibleTables_includesTableWithTableLevelPermission() {
        SchemaTable table = tableWithColumns("items", "id", "name");
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", null))
                .thenReturn(PrimaryPermission.READ);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", "id"))
                .thenReturn(PrimaryPermission.READ);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", "name"))
                .thenReturn(PrimaryPermission.READ);

        List<AccessibleBuilderTableResponse> result = resolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tableName()).isEqualTo("items");
        assertThat(result.get(0).columns()).hasSize(2);
    }

    @Test
    void listAccessibleTables_includesTableWithOnlyColumnLevelPermission_evenIfTableLevelIsNone() {
        // BR-QUERYBUILDER-01: テーブル単位・スキーマ単位がNONEでも、個別の列単位設定で
        // READ以上が付与されているケースを正しく拾う（UNIT-05 isTableVisible()と同じOR条件）
        SchemaTable table = tableWithColumns("items", "id", "secret");
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", null))
                .thenReturn(PrimaryPermission.NONE);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", "id"))
                .thenReturn(PrimaryPermission.READ);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", "secret"))
                .thenReturn(PrimaryPermission.NONE);

        List<AccessibleBuilderTableResponse> result = resolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).columns()).extracting("columnName").containsExactly("id");
    }

    @Test
    void listAccessibleTables_excludesTableWithNoAccessibleColumnsAtAll() {
        SchemaTable table = tableWithColumns("secrets", "value");
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        when(effectivePermissionResolver.resolvePrimary(any(), any(), anyString(), anyString(), any()))
                .thenReturn(PrimaryPermission.NONE);

        List<AccessibleBuilderTableResponse> result = resolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA);

        assertThat(result).isEmpty();
    }

    @Test
    void isColumnAccessible_trueWhenReadOrAbove() {
        SchemaTable table = tableWithColumns("items", "id");
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", "id"))
                .thenReturn(PrimaryPermission.UPDATE);

        assertThat(resolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "id")).isTrue();
    }

    @Test
    void isColumnAccessible_falseWhenColumnDoesNotExist() {
        SchemaTable table = tableWithColumns("items", "id");
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));

        assertThat(resolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "unknown")).isFalse();
    }

    @Test
    void isColumnAccessible_falseWhenPermissionIsNone() {
        SchemaTable table = tableWithColumns("items", "id");
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, SCHEMA, "items", "id"))
                .thenReturn(PrimaryPermission.NONE);

        assertThat(resolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "id")).isFalse();
    }
}

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

package cherry.mastermeister.masterdata;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.BatchSizeExceededException;
import cherry.mastermeister.common.exception.MasterDataTableNotAccessibleException;
import cherry.mastermeister.common.exception.SchemaNotImportedException;
import cherry.mastermeister.masterdata.model.AccessibleConnection;
import cherry.mastermeister.masterdata.model.AccessibleTable;
import cherry.mastermeister.masterdata.model.BatchOperationItem;
import cherry.mastermeister.masterdata.model.OperationType;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionService;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.dialect.H2DialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COMP-13。business-logic-model.md §1・§2。BR-MASTER-01〜03・13。
 */
class MasterDataServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;

    private RdbmsConnectionService rdbmsConnectionService;
    private SchemaIntrospectionService schemaIntrospectionService;
    private EffectivePermissionResolver effectivePermissionResolver;
    private RecordQueryService recordQueryService;
    private MasterDataService service;

    @BeforeEach
    void setUp() {
        rdbmsConnectionService = mock(RdbmsConnectionService.class);
        schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        effectivePermissionResolver = mock(EffectivePermissionResolver.class);
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", java.time.Duration.ofMinutes(10),
                        java.time.Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, java.time.Duration.ofMinutes(15)),
                new AppProperties.UserRegistration(java.time.Duration.ofHours(3), 3, java.time.Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"),
                new AppProperties.Rdbms("1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                new AppProperties.Masterdata(3),
                new AppProperties.Audit(100),
                new AppProperties.Query(30, 10000),
                new AppProperties.Trace(true, true, true, "ENTER", "EXIT", "EXCEPTION"));
        recordQueryService = mock(RecordQueryService.class);
        when(rdbmsConnectionService.getConnection(CONNECTION_ID)).thenReturn(connection(CONNECTION_ID, "接続A"));
        service = new MasterDataService(rdbmsConnectionService, schemaIntrospectionService,
                effectivePermissionResolver, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                new ColumnDataTypeMapper(), new RawQueryConditionValidator(), recordQueryService,
                new RecordBatchService(), mock(AuditEventPublisher.class), appProperties);
    }

    private static RdbmsConnection connection(Long id, String displayName) {
        RdbmsConnection connection = new RdbmsConnection(displayName, DbType.H2, "localhost", 9092, "mem:test",
                "sa", "enc", 1, null, Instant.now(), Instant.now());
        try {
            Field idField = RdbmsConnection.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(connection, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    private static SchemaTable tableWithColumns(String tableName, TableType tableType, List<String> columnNames) {
        SchemaTable table = new SchemaTable("public", tableName, tableType, null);
        for (String columnName : columnNames) {
            table.addColumn(new SchemaColumn(columnName, 1, null, "VARCHAR", NormalizedType.STRING, true, null));
        }
        table.addConstraint(new SchemaConstraint(ConstraintType.PRIMARY_KEY, "pk", List.of(columnNames.get(0)),
                null, null));
        return table;
    }

    private static SchemaSnapshot snapshotOf(SchemaTable... tables) {
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        for (SchemaTable table : tables) {
            snapshot.addTable(table);
        }
        return snapshot;
    }

    @Test
    void listAccessibleTables_throwsSchemaNotImported_whenNotImported() {
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAccessibleTables(USER_ID, CONNECTION_ID))
                .isInstanceOf(SchemaNotImportedException.class);
    }

    @Test
    void listAccessibleTables_includesTable_whenTableLevelReadPermission() {
        SchemaTable table = tableWithColumns("products", TableType.TABLE, List.of("id", "name"));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", null))
                .thenReturn(PrimaryPermission.READ);

        List<AccessibleTable> result = service.listAccessibleTables(USER_ID, CONNECTION_ID);

        assertThat(result).extracting(AccessibleTable::tableName).containsExactly("products");
    }

    @Test
    void listAccessibleTables_includesTable_whenOnlyColumnLevelReadPermission() {
        SchemaTable table = tableWithColumns("products", TableType.TABLE, List.of("id", "name"));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", null))
                .thenReturn(PrimaryPermission.NONE);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", "id"))
                .thenReturn(PrimaryPermission.NONE);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", "name"))
                .thenReturn(PrimaryPermission.READ);

        List<AccessibleTable> result = service.listAccessibleTables(USER_ID, CONNECTION_ID);

        assertThat(result).extracting(AccessibleTable::tableName).containsExactly("products");
    }

    @Test
    void listAccessibleTables_excludesTable_whenAllColumnsAndTableLevelNone() {
        SchemaTable table = tableWithColumns("products", TableType.TABLE, List.of("id", "name"));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(effectivePermissionResolver.resolvePrimary(eq(USER_ID), eq(CONNECTION_ID), anyString(), any(), any()))
                .thenReturn(PrimaryPermission.NONE);

        List<AccessibleTable> result = service.listAccessibleTables(USER_ID, CONNECTION_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void listAccessibleTables_viewIsAlwaysReadOnly_regardlessOfPermission() {
        SchemaTable view = tableWithColumns("v_products", TableType.VIEW, List.of("id", "name"));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(view)));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "v_products", null))
                .thenReturn(PrimaryPermission.UPDATE);
        when(effectivePermissionResolver.canCreate(USER_ID, CONNECTION_ID, "public", "v_products")).thenReturn(true);
        when(effectivePermissionResolver.canDelete(USER_ID, CONNECTION_ID, "public", "v_products")).thenReturn(true);

        List<AccessibleTable> result = service.listAccessibleTables(USER_ID, CONNECTION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).creatable()).isFalse();
        assertThat(result.get(0).deletable()).isFalse();
    }

    @Test
    void listAccessibleConnections_excludesConnection_whenSchemaNotImported() {
        when(rdbmsConnectionService.listConnections()).thenReturn(List.of(connection(CONNECTION_ID, "接続A")));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.empty());

        List<AccessibleConnection> result = service.listAccessibleConnections(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void listAccessibleConnections_excludesConnection_whenNoTableVisible() {
        SchemaTable table = tableWithColumns("products", TableType.TABLE, List.of("id"));
        when(rdbmsConnectionService.listConnections()).thenReturn(List.of(connection(CONNECTION_ID, "接続A")));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(effectivePermissionResolver.resolvePrimary(eq(USER_ID), eq(CONNECTION_ID), anyString(), any(), any()))
                .thenReturn(PrimaryPermission.NONE);

        List<AccessibleConnection> result = service.listAccessibleConnections(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void listAccessibleConnections_includesConnection_andOnlyDisplayName_whenAtLeastOneTableVisible() {
        SchemaTable table = tableWithColumns("products", TableType.TABLE, List.of("id"));
        when(rdbmsConnectionService.listConnections()).thenReturn(List.of(connection(CONNECTION_ID, "接続A")));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", null))
                .thenReturn(PrimaryPermission.READ);

        List<AccessibleConnection> result = service.listAccessibleConnections(USER_ID);

        assertThat(result).containsExactly(new AccessibleConnection(CONNECTION_ID, "接続A"));
    }

    @Test
    void getRecords_throwsMasterDataTableNotAccessible_whenTableNotVisible() {
        SchemaTable table = tableWithColumns("products", TableType.TABLE, List.of("id"));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(effectivePermissionResolver.resolvePrimary(eq(USER_ID), eq(CONNECTION_ID), anyString(), any(), any()))
                .thenReturn(PrimaryPermission.NONE);

        assertThatThrownBy(() -> service.getRecords(USER_ID, CONNECTION_ID, "public", "products", List.of(), null,
                null, 0, 10))
                .isInstanceOf(MasterDataTableNotAccessibleException.class);
    }

    @Test
    void getRecords_throwsMasterDataTableNotAccessible_whenTableDoesNotExist() {
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf()));

        assertThatThrownBy(() -> service.getRecords(USER_ID, CONNECTION_ID, "public", "unknown", List.of(), null,
                null, 0, 10))
                .isInstanceOf(MasterDataTableNotAccessibleException.class);
    }

    @Test
    void applyBatch_throwsBatchSizeExceeded_whenOverLimit() {
        List<BatchOperationItem> operations = List.of(
                new BatchOperationItem(OperationType.DELETE, java.util.Map.of("id", "1"), null),
                new BatchOperationItem(OperationType.DELETE, java.util.Map.of("id", "2"), null),
                new BatchOperationItem(OperationType.DELETE, java.util.Map.of("id", "3"), null),
                new BatchOperationItem(OperationType.DELETE, java.util.Map.of("id", "4"), null));

        assertThatThrownBy(() -> service.applyBatch(USER_ID, CONNECTION_ID, "public", "products", operations))
                .isInstanceOf(BatchSizeExceededException.class);
    }

}

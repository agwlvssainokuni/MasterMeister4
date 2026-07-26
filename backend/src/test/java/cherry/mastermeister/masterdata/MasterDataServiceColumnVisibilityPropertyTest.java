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
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.masterdata.model.RecordPage;
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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §7.3（PBT-01）。非表示の不変条件: 実効主権限がNONEのカラムは、
 * いかなる絞込条件・SQL手入力を用いても、レコード一覧のレスポンス（columns）に一切含まれない。
 * 各tryごとにモック・サービスを新規構築する（EffectivePermissionResolverPropertyTestと同じ方針、
 * @BeforeEachで共有するモックへのスタブ積み増しによる汚染を避けるため）。
 */
class MasterDataServiceColumnVisibilityPropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;

    @Provide
    Arbitrary<Map<String, PrimaryPermission>> permissionMap() {
        Arbitrary<PrimaryPermission> permission = Arbitraries.of(PrimaryPermission.class);
        return Arbitraries.maps(Arbitraries.of("col_a", "col_b", "col_c", "col_d"), permission)
                .ofMinSize(1).ofMaxSize(4);
    }

    @Property
    boolean columnVisibility_neverIncludesColumnsWithNonePermission(
            @ForAll("permissionMap") Map<String, PrimaryPermission> permissionsByColumn) {
        boolean anyVisible = permissionsByColumn.values().stream().anyMatch(p -> p != PrimaryPermission.NONE);
        if (!anyVisible) {
            // 全カラムNONEの場合、テーブル自体が非表示(MasterDataTableNotAccessibleException)となり
            // getRecords()を正常に呼び出せないため、本プロパティの対象外とする(別途Mockitoテストで確認済み)
            return true;
        }

        RdbmsConnectionService rdbmsConnectionService = mock(RdbmsConnectionService.class);
        SchemaIntrospectionService schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        EffectivePermissionResolver effectivePermissionResolver = mock(EffectivePermissionResolver.class);
        RecordQueryService recordQueryService = mock(RecordQueryService.class);

        List<String> columnNames = List.copyOf(permissionsByColumn.keySet());
        SchemaTable table = tableWithColumns("products", columnNames);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshotOf(table)));
        when(rdbmsConnectionService.getConnection(CONNECTION_ID)).thenReturn(connection(CONNECTION_ID, "接続A"));
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", null))
                .thenReturn(PrimaryPermission.NONE);
        for (var entry : permissionsByColumn.entrySet()) {
            when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                    entry.getKey())).thenReturn(entry.getValue());
        }
        when(recordQueryService.queryRecords(any(), any(), anyString(), anyString(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(new RecordPage(List.of(), List.of(), 0, 10, 0, false, false));

        MasterDataService service = new MasterDataService(rdbmsConnectionService, schemaIntrospectionService,
                effectivePermissionResolver, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                new ColumnDataTypeMapper(), new RawQueryConditionValidator(), recordQueryService,
                new RecordBatchService(), mock(AuditEventPublisher.class), appProperties());

        service.getRecords(USER_ID, CONNECTION_ID, "public", "products", List.of(), null, null, 0, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecordColumn>> columnsCaptor = ArgumentCaptor.forClass(List.class);
        verify(recordQueryService).queryRecords(any(), any(), anyString(), anyString(), columnsCaptor.capture(),
                any(), any(), any(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        List<String> passedColumnNames = columnsCaptor.getValue().stream().map(RecordColumn::columnName).toList();
        List<String> expectedVisible = permissionsByColumn.entrySet().stream()
                .filter(e -> e.getValue() != PrimaryPermission.NONE)
                .map(Map.Entry::getKey)
                .toList();

        return passedColumnNames.size() == expectedVisible.size()
                && passedColumnNames.containsAll(expectedVisible)
                && permissionsByColumn.entrySet().stream()
                        .filter(e -> e.getValue() == PrimaryPermission.NONE)
                        .noneMatch(e -> passedColumnNames.contains(e.getKey()));
    }

    private static AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10),
                        Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, Duration.ofMinutes(15)),
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"),
                new AppProperties.Rdbms("1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                new AppProperties.Masterdata(1000),
                new AppProperties.Audit(100),
                new AppProperties.Query(30, 10000));
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

    private static SchemaTable tableWithColumns(String tableName, List<String> columnNames) {
        SchemaTable table = new SchemaTable("public", tableName, TableType.TABLE, null);
        for (String columnName : columnNames) {
            table.addColumn(new SchemaColumn(columnName, 1, null, "VARCHAR", NormalizedType.STRING, true, null));
        }
        table.addConstraint(new SchemaConstraint(ConstraintType.PRIMARY_KEY, "pk", List.of(columnNames.get(0)),
                null, null));
        return table;
    }

    private static SchemaSnapshot snapshotOf(SchemaTable table) {
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        return snapshot;
    }
}

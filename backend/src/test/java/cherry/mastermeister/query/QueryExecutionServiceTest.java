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

package cherry.mastermeister.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.NonReadOnlyQueryException;
import cherry.mastermeister.common.exception.QueryResultSizeExceededException;
import cherry.mastermeister.common.exception.QuerySchemaNotAccessibleException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.query.entity.QueryExecutionRecord;
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.entity.Visibility;
import cherry.mastermeister.query.model.QueryResult;
import cherry.mastermeister.query.repository.QueryExecutionRecordRepository;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionService;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.dialect.H2DialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COMP-14。business-logic-model.md §3〜6。実際にH2（インメモリ、実テーブル）へ
 * SELECT/COUNTを発行して検証する。
 */
class QueryExecutionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;

    private RdbmsConnectionService rdbmsConnectionService;
    private SchemaIntrospectionService schemaIntrospectionService;
    private EffectivePermissionResolver effectivePermissionResolver;
    private SavedQueryService savedQueryService;
    private QueryExecutionRecordRepository queryExecutionRecordRepository;
    private AuditEventPublisher auditEventPublisher;
    private QueryExecutionService service;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:query_execution_test;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        // ユーザが入力する任意SQLは基本的に無クオート識別子であるため、テスト用テーブルも
        // 無クオートで作成する（H2は無クオート識別子を大文字に正規化するため、UNIT-05の
        // RecordQueryServiceTestのようなクオート付きlower-case作成とは異なる方針とする）
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS items (id INT PRIMARY KEY, name VARCHAR(100), amount INT)");
        jdbcTemplate.execute("DELETE FROM items");
        jdbcTemplate.update("INSERT INTO items VALUES (1, 'Apple', 100)");
        jdbcTemplate.update("INSERT INTO items VALUES (2, 'Banana', 200)");
        jdbcTemplate.update("INSERT INTO items VALUES (3, 'Cherry', 300)");

        rdbmsConnectionService = mock(RdbmsConnectionService.class);
        when(rdbmsConnectionService.getConnection(CONNECTION_ID)).thenReturn(connection());
        when(rdbmsConnectionService.getDataSource(CONNECTION_ID)).thenReturn(dataSource);

        schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot()));

        effectivePermissionResolver = mock(EffectivePermissionResolver.class);
        when(effectivePermissionResolver.resolvePrimary(USER_ID, CONNECTION_ID, "PUBLIC", "items", null))
                .thenReturn(PrimaryPermission.READ);

        savedQueryService = mock(SavedQueryService.class);
        queryExecutionRecordRepository = mock(QueryExecutionRecordRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);

        service = new QueryExecutionService(rdbmsConnectionService, schemaIntrospectionService,
                effectivePermissionResolver, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                savedQueryService, queryExecutionRecordRepository, auditEventPublisher,
                appProperties(10000), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).execute("DROP TABLE items");
    }

    private static RdbmsConnection connection() {
        Instant now = Instant.now();
        return new RdbmsConnection("接続A", DbType.H2, "localhost", 9092, "test", "sa", "encrypted", 1, null, now,
                now);
    }

    private static SchemaSnapshot snapshot() {
        SchemaTable table = new SchemaTable("PUBLIC", "items", TableType.TABLE, null);
        table.addColumn(new SchemaColumn("id", 1, null, "INT", NormalizedType.NUMBER, false, null));
        table.addColumn(new SchemaColumn("name", 2, null, "VARCHAR", NormalizedType.STRING, true, null));
        table.addColumn(new SchemaColumn("amount", 3, null, "INT", NormalizedType.NUMBER, true, null));
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        return snapshot;
    }

    private static AppProperties appProperties(int maxResultRows) {
        return new AppProperties(
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
                new AppProperties.Query(30, maxResultRows));
    }

    @Test
    void execute_returnsAllRows_whenPagingDisabled() {
        QueryResult result = service.execute(USER_ID, CONNECTION_ID, "SELECT * FROM items", Map.of(), "PUBLIC",
                false, 0, 0);

        assertThat(result.rows()).hasSize(3);
        assertThat(result.columns()).containsExactly("ID", "NAME", "AMOUNT");
        assertThat(result.page()).isNull();
        assertThat(result.totalCount()).isNull();
    }

    @Test
    void execute_appliesPagingWithCountAndLimitOffset() {
        QueryResult result = service.execute(USER_ID, CONNECTION_ID, "SELECT * FROM items ORDER BY id", Map.of(),
                "PUBLIC", true, 1, 2);

        assertThat(result.totalCount()).isEqualTo(3L);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(2);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("NAME")).isEqualTo("Cherry");
    }

    @Test
    void execute_bindsNamedParameters() {
        QueryResult result = service.execute(USER_ID, CONNECTION_ID, "SELECT * FROM items WHERE name = :name",
                Map.of("name", "Banana"), "PUBLIC", false, 0, 0);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("AMOUNT")).isEqualTo("200");
    }

    @Test
    void execute_rejectsNonReadOnlySql() {
        assertThatThrownBy(() -> service.execute(USER_ID, CONNECTION_ID, "DELETE FROM items", Map.of(), "PUBLIC",
                false, 0, 0))
                .isInstanceOf(NonReadOnlyQueryException.class);
    }

    @Test
    void execute_rejectsInaccessibleSchema() {
        assertThatThrownBy(() -> service.execute(USER_ID, CONNECTION_ID, "SELECT * FROM items", Map.of(),
                "OTHER_SCHEMA", false, 0, 0))
                .isInstanceOf(QuerySchemaNotAccessibleException.class);
    }

    @Test
    void execute_rejectsResultOverMaxRows_whenPagingDisabled() {
        QueryExecutionService smallCapService = new QueryExecutionService(rdbmsConnectionService,
                schemaIntrospectionService, effectivePermissionResolver,
                new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())), savedQueryService,
                queryExecutionRecordRepository, auditEventPublisher, appProperties(2), new ObjectMapper());

        assertThatThrownBy(() -> smallCapService.execute(USER_ID, CONNECTION_ID, "SELECT * FROM items", Map.of(),
                "PUBLIC", false, 0, 0))
                .isInstanceOf(QueryResultSizeExceededException.class);
    }

    @Test
    void execute_persistsExecutionRecordAndPublishesAuditEvent() {
        service.execute(USER_ID, CONNECTION_ID, "SELECT * FROM items", Map.of(), "PUBLIC", false, 0, 0);

        verify(queryExecutionRecordRepository).save(any(QueryExecutionRecord.class));
        verify(auditEventPublisher).publish(argThatEventType(AuditEventType.QUERY_EXECUTED));
    }

    @Test
    void executeSavedQuery_usesStoredSqlFromSavedQueryService() {
        Instant now = Instant.now();
        SavedQuery savedQuery = new SavedQuery(CONNECTION_ID, "全件", "SELECT * FROM items", Visibility.PUBLIC,
                USER_ID, now);
        when(savedQueryService.getSavedQuery(USER_ID, CONNECTION_ID, 42L)).thenReturn(savedQuery);

        QueryResult result = service.executeSavedQuery(USER_ID, CONNECTION_ID, 42L, Map.of(), "PUBLIC", false, 0, 0);

        assertThat(result.rows()).hasSize(3);
        verify(savedQueryService).getSavedQuery(USER_ID, CONNECTION_ID, 42L);
    }

    private static AuditEvent argThatEventType(AuditEventType eventType) {
        return org.mockito.ArgumentMatchers.argThat(event -> event != null && event.eventType() == eventType);
    }
}

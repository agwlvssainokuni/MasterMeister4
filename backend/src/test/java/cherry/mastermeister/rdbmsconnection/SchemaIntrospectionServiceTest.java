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

package cherry.mastermeister.rdbmsconnection;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.SchemaImportFailedException;
import cherry.mastermeister.rdbmsconnection.dialect.H2DialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import cherry.mastermeister.rdbmsconnection.repository.SchemaSnapshotRepository;
import org.h2.tools.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * COMP-08。business-logic-model.md §3。BR-RDBMS-06〜08。
 * 対象RDBMS役として、実際にH2をTCPサーバモードで起動し、本物のJDBC接続・
 * DatabaseMetaData読取を通じて検証する（モックだけでは検証しきれないJDBC相互作用のため）。
 */
@DataJpaTest
class SchemaIntrospectionServiceTest {

    private static Server h2Server;
    private static int h2Port;

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;
    @Autowired
    private SchemaSnapshotRepository schemaSnapshotRepository;
    @Autowired
    private TestEntityManager entityManager;

    @BeforeAll
    static void startH2Server() throws SQLException {
        h2Server = Server.createTcpServer("-tcpAllowOthers", "-tcpPort", "0", "-ifNotExists").start();
        h2Port = h2Server.getPort();
    }

    @AfterAll
    static void stopH2Server() {
        if (h2Server != null) {
            h2Server.stop();
        }
    }

    private String targetJdbcUrl(String dbName) {
        return "jdbc:h2:tcp://localhost:" + h2Port + "/mem:" + dbName + ";DB_CLOSE_DELAY=-1";
    }

    private void createSampleSchema(String dbName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(targetJdbcUrl(dbName), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE categories (" +
                    "category_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "category_name VARCHAR(100) NOT NULL)");
            statement.execute("CREATE TABLE products (" +
                    "product_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "product_name VARCHAR(200) NOT NULL, " +
                    "category_id INT NOT NULL, " +
                    "unit_price DECIMAL(10,2) NOT NULL, " +
                    "FOREIGN KEY (category_id) REFERENCES categories(category_id))");
        }
    }

    private ConnectionCredentialCipher realCipher() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10),
                        Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, Duration.ofMinutes(15)),
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"),
                new AppProperties.Rdbms("1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="));
        return new ConnectionCredentialCipher(appProperties);
    }

    private Long persistTargetConnection(String dbName, ConnectionCredentialCipher cipher) {
        Instant now = Instant.now();
        var encrypted = cipher.encrypt("");
        RdbmsConnection connection = new RdbmsConnection("動作確認用", DbType.H2, "localhost", h2Port, "mem:" + dbName,
                "sa", encrypted.encryptedValue(), encrypted.keyId(), "DB_CLOSE_DELAY=-1", now, now);
        return rdbmsConnectionRepository.saveAndFlush(connection).getId();
    }

    @Test
    void refreshSchema_capturesTablesColumnsAndConstraints() throws SQLException {
        createSampleSchema("introspect1");
        ConnectionCredentialCipher cipher = realCipher();
        Long connectionId = persistTargetConnection("introspect1", cipher);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        SchemaIntrospectionService service = new SchemaIntrospectionService(rdbmsConnectionRepository,
                schemaSnapshotRepository, cipher, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                auditEventPublisher);

        SchemaSnapshot snapshot = service.refreshSchema(connectionId, 1L);

        assertThat(snapshot.getTables()).hasSize(2);
        SchemaTable products = snapshot.getTables().stream()
                .filter(t -> t.getTableName().equalsIgnoreCase("products"))
                .findFirst().orElseThrow();
        assertThat(products.getColumns()).extracting(c -> c.getColumnName().toLowerCase())
                .containsExactlyInAnyOrder("product_id", "product_name", "category_id", "unit_price");
        SchemaTable categories = snapshot.getTables().stream()
                .filter(t -> t.getTableName().equalsIgnoreCase("categories"))
                .findFirst().orElseThrow();
        assertThat(categories.getConstraints())
                .anyMatch(c -> c.getConstraintType() == ConstraintType.PRIMARY_KEY);
        assertThat(products.getConstraints())
                .anyMatch(c -> c.getConstraintType() == ConstraintType.FOREIGN_KEY
                        && c.getReferencedTable().equalsIgnoreCase("categories"));
        var unitPriceColumn = products.getColumns().stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase("unit_price")).findFirst().orElseThrow();
        assertThat(unitPriceColumn.getNormalizedType()).isEqualTo(NormalizedType.NUMBER);
        // 主キー自身の自動生成インデックスがUNIQUE制約として重複登録されないこと（レビュー指摘の再発防止）
        assertThat(products.getConstraints())
                .noneMatch(c -> c.getConstraintType() == ConstraintType.UNIQUE
                        && c.getColumnNames().equals(List.of("PRODUCT_ID")));
        assertThat(products.getConstraints()).filteredOn(c -> c.getConstraintType() == ConstraintType.PRIMARY_KEY)
                .hasSize(1);

        verify(auditEventPublisher).publish(argThat(e -> e.eventType() == AuditEventType.SCHEMA_IMPORTED
                && e.resultStatus() == ResultStatus.SUCCESS));
    }

    @Test
    void refreshSchema_capturesMultipleSchemasWithinOneConnection_excludingSystemSchemas() throws SQLException {
        // UNIT-04 Functional Designにて訂正: 1接続内に複数スキーマが存在しうる前提のため、
        // PostgreSQL/H2ではシステムスキーマを除く全スキーマを自動検出して取り込む
        createSampleSchema("introspect4");
        try (Connection connection = DriverManager.getConnection(targetJdbcUrl("introspect4"), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA extra_schema");
            statement.execute("CREATE TABLE extra_schema.orders (order_id INT PRIMARY KEY)");
        }
        ConnectionCredentialCipher cipher = realCipher();
        Long connectionId = persistTargetConnection("introspect4", cipher);
        SchemaIntrospectionService service = new SchemaIntrospectionService(rdbmsConnectionRepository,
                schemaSnapshotRepository, cipher, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                mock(AuditEventPublisher.class));

        SchemaSnapshot snapshot = service.refreshSchema(connectionId, 1L);

        assertThat(snapshot.getTables()).hasSize(3);
        assertThat(snapshot.getTables()).extracting(SchemaTable::getSchemaName)
                .containsExactlyInAnyOrder("PUBLIC", "PUBLIC", "EXTRA_SCHEMA");
        SchemaTable orders = snapshot.getTables().stream()
                .filter(t -> t.getTableName().equalsIgnoreCase("orders"))
                .findFirst().orElseThrow();
        assertThat(orders.getSchemaName()).isEqualTo("EXTRA_SCHEMA");
        // システムスキーマ(INFORMATION_SCHEMA)は取込対象に含まれないこと
        assertThat(snapshot.getTables()).extracting(SchemaTable::getSchemaName)
                .noneMatch(schemaName -> schemaName.equalsIgnoreCase("INFORMATION_SCHEMA"));
    }

    @Test
    void refreshSchema_replacesExistingSnapshot_perBrRdbms08() throws SQLException {
        createSampleSchema("introspect2");
        ConnectionCredentialCipher cipher = realCipher();
        Long connectionId = persistTargetConnection("introspect2", cipher);
        SchemaIntrospectionService service = new SchemaIntrospectionService(rdbmsConnectionRepository,
                schemaSnapshotRepository, cipher, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                mock(AuditEventPublisher.class));

        service.refreshSchema(connectionId, 1L);
        entityManager.clear();

        // 対象RDBMS側にテーブルを追加してから再取込 -> 全置換されること
        try (Connection connection = DriverManager.getConnection(targetJdbcUrl("introspect2"), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (order_id INT PRIMARY KEY)");
        }
        service.refreshSchema(connectionId, 1L);
        entityManager.clear();

        Optional<SchemaSnapshot> reloaded = schemaSnapshotRepository.findById(connectionId);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getTables()).hasSize(3);
    }

    @Test
    void refreshSchema_throwsSchemaImportFailed_andKeepsOldSnapshot_whenTargetUnreachable() throws SQLException {
        createSampleSchema("introspect3");
        ConnectionCredentialCipher cipher = realCipher();
        Long connectionId = persistTargetConnection("introspect3", cipher);
        SchemaIntrospectionService service = new SchemaIntrospectionService(rdbmsConnectionRepository,
                schemaSnapshotRepository, cipher, new RdbmsDialectStrategyResolver(List.of(new H2DialectStrategy())),
                mock(AuditEventPublisher.class));
        service.refreshSchema(connectionId, 1L);
        entityManager.clear();

        // 接続先を存在しないポートに書き換えて再取込を試み、失敗することを確認する(BR-RDBMS-07)
        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId).orElseThrow();
        connection.update(connection.getDisplayName(), DbType.H2, "localhost", 1, connection.getDatabaseName(),
                "sa", connection.getEncryptedPassword(), connection.getEncryptionKeyId(),
                "DB_CLOSE_DELAY=-1", Instant.now());
        rdbmsConnectionRepository.saveAndFlush(connection);
        entityManager.clear();

        assertThatThrownBy(() -> service.refreshSchema(connectionId, 1L))
                .isInstanceOf(SchemaImportFailedException.class);

        Optional<SchemaSnapshot> stillOld = schemaSnapshotRepository.findById(connectionId);
        assertThat(stillOld).isPresent();
        assertThat(stillOld.get().getTables()).hasSize(2);
    }

    private static AuditEvent argThat(java.util.function.Predicate<AuditEvent> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}

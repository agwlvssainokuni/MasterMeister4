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
import cherry.mastermeister.common.exception.RdbmsConnectionNotFoundException;
import cherry.mastermeister.common.exception.SchemaImportFailedException;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
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
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import cherry.mastermeister.rdbmsconnection.repository.SchemaSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * COMP-08。business-logic-model.md §3。BR-RDBMS-06〜08。
 */
@Service
public class SchemaIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    private static final long OVERALL_TIMEOUT_SECONDS = 60L;
    private static final int DETAIL_MAX_LENGTH = 2000;

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final SchemaSnapshotRepository schemaSnapshotRepository;
    private final ConnectionCredentialCipher connectionCredentialCipher;
    private final RdbmsDialectStrategyResolver dialectStrategyResolver;
    private final AuditEventPublisher auditEventPublisher;
    private final ExecutorService introspectionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SchemaIntrospectionService(RdbmsConnectionRepository rdbmsConnectionRepository,
                                       SchemaSnapshotRepository schemaSnapshotRepository,
                                       ConnectionCredentialCipher connectionCredentialCipher,
                                       RdbmsDialectStrategyResolver dialectStrategyResolver,
                                       AuditEventPublisher auditEventPublisher) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.schemaSnapshotRepository = schemaSnapshotRepository;
        this.connectionCredentialCipher = connectionCredentialCipher;
        this.dialectStrategyResolver = dialectStrategyResolver;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public Optional<SchemaSnapshot> getSchema(Long connectionId) {
        return schemaSnapshotRepository.findById(connectionId);
    }

    /**
     * BR-RDBMS-06〜08。CompletableFuture.orTimeout(60秒)でタイムアウト制御し（NFR-03-03）、
     * タイムアウト時は実行中のConnectionを強制closeしてバックグラウンドスレッドを中断させる
     * （nfr-design-patterns.md §1.1、レビュー指摘の反映）。全テーブル読取成功時のみ
     * 既存スナップショットを全置換する（オールオアナッシング）。
     */
    @Transactional
    public SchemaSnapshot refreshSchema(Long connectionId, Long triggeredBy) {
        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(RdbmsConnectionNotFoundException::new);

        AtomicReference<Connection> activeJdbcConnection = new AtomicReference<>();
        CompletableFuture<SchemaSnapshot> future = CompletableFuture.supplyAsync(
                () -> introspect(connection, activeJdbcConnection), introspectionExecutor);

        SchemaSnapshot snapshot;
        try {
            snapshot = future.orTimeout(OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        } catch (CompletionException | CancellationException e) {
            forceCloseOnTimeout(activeJdbcConnection);
            auditEventPublisher.publish(new AuditEvent(Instant.now(), triggeredBy, connectionId,
                    AuditEventType.SCHEMA_IMPORTED, connection.getDisplayName(), ResultStatus.FAILURE,
                    summarize(e)));
            throw new SchemaImportFailedException();
        }

        // BR-RDBMS-08: 全置換。既存スナップショット(あれば)を削除してから新規保存する
        schemaSnapshotRepository.findById(connectionId).ifPresent(existing -> {
            schemaSnapshotRepository.delete(existing);
            schemaSnapshotRepository.flush();
        });
        SchemaSnapshot saved = schemaSnapshotRepository.save(snapshot);

        auditEventPublisher.publish(new AuditEvent(Instant.now(), triggeredBy, connectionId,
                AuditEventType.SCHEMA_IMPORTED, connection.getDisplayName(), ResultStatus.SUCCESS, null));
        return saved;
    }

    private void forceCloseOnTimeout(AtomicReference<Connection> activeRef) {
        Connection connection = activeRef.get();
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Failed to force-close JDBC connection after schema introspection timeout", e);
            }
        }
    }

    private String summarize(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.length() > DETAIL_MAX_LENGTH ? message.substring(0, DETAIL_MAX_LENGTH) : message;
    }

    private SchemaSnapshot introspect(RdbmsConnection connection, AtomicReference<Connection> activeRef) {
        RdbmsDialectStrategy dialect = dialectStrategyResolver.resolve(connection.getDbType());
        String jdbcUrl = dialect.buildJdbcUrl(connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getSchemaName(), connection.getAdditionalParams());
        String password = connectionCredentialCipher.decrypt(connection.getEncryptedPassword(),
                connection.getEncryptionKeyId());

        try (Connection jdbcConnection = DriverManager.getConnection(jdbcUrl, connection.getUsername(), password)) {
            activeRef.set(jdbcConnection);
            if (connection.getSchemaName() != null && dialect.requiresSchemaSwitch()) {
                dialect.applySchemaSwitch(jdbcConnection, connection.getSchemaName());
            }
            return readSchema(jdbcConnection, connection);
        } catch (SQLException e) {
            throw new RuntimeException("Schema introspection failed for connectionId=" + connection.getId(), e);
        } finally {
            activeRef.set(null);
        }
    }

    private SchemaSnapshot readSchema(Connection jdbcConnection, RdbmsConnection connection) throws SQLException {
        DatabaseMetaData metaData = jdbcConnection.getMetaData();
        boolean catalogBased = connection.getDbType() == DbType.MYSQL || connection.getDbType() == DbType.MARIADB;
        String catalog = catalogBased ? connection.getDatabaseName() : null;
        // schemaName未指定時、nullのままだと方言によってはINFORMATION_SCHEMA等の
        // システムスキーマまで対象に含まれてしまうため、方言ごとのデフォルトスキーマに解決する
        String schemaPattern = catalogBased ? null
                : Optional.ofNullable(connection.getSchemaName()).orElseGet(() -> defaultSchemaFor(connection.getDbType()));

        SchemaSnapshot snapshot = new SchemaSnapshot(connection.getId(), Instant.now());
        try (ResultSet tables = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                TableType tableType = "VIEW".equalsIgnoreCase(tables.getString("TABLE_TYPE"))
                        ? TableType.VIEW : TableType.TABLE;
                String comment = tables.getString("REMARKS");
                SchemaTable table = new SchemaTable(tableName, tableType, comment);
                readColumns(metaData, catalog, schemaPattern, tableName, table);
                readPrimaryKey(metaData, catalog, schemaPattern, tableName, table);
                readForeignKeys(metaData, catalog, schemaPattern, tableName, table);
                readIndexes(metaData, catalog, schemaPattern, tableName, table);
                snapshot.addTable(table);
            }
        }
        return snapshot;
    }

    private void readColumns(DatabaseMetaData metaData, String catalog, String schemaPattern, String tableName,
                              SchemaTable table) throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                int ordinalPosition = columns.getInt("ORDINAL_POSITION");
                String comment = columns.getString("REMARKS");
                String nativeType = columns.getString("TYPE_NAME");
                int dataType = columns.getInt("DATA_TYPE");
                boolean nullable = columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                String defaultValue = columns.getString("COLUMN_DEF");
                table.addColumn(new SchemaColumn(columnName, ordinalPosition, comment, nativeType,
                        normalize(dataType), nullable, defaultValue));
            }
        }
    }

    private void readPrimaryKey(DatabaseMetaData metaData, String catalog, String schemaPattern, String tableName,
                                 SchemaTable table) throws SQLException {
        Map<String, List<String>> columnsByPkName = new LinkedHashMap<>();
        try (ResultSet pk = metaData.getPrimaryKeys(catalog, schemaPattern, tableName)) {
            while (pk.next()) {
                String pkName = Optional.ofNullable(pk.getString("PK_NAME")).orElse(tableName + "_pk");
                columnsByPkName.computeIfAbsent(pkName, k -> new java.util.ArrayList<>())
                        .add(pk.getString("COLUMN_NAME"));
            }
        }
        columnsByPkName.forEach((pkName, columnNames) -> table.addConstraint(
                new SchemaConstraint(ConstraintType.PRIMARY_KEY, pkName, columnNames, null, null)));
    }

    private void readForeignKeys(DatabaseMetaData metaData, String catalog, String schemaPattern, String tableName,
                                  SchemaTable table) throws SQLException {
        record FkAccumulator(String referencedTable, List<String> columnNames, List<String> referencedColumns) {
        }
        Map<String, FkAccumulator> byFkName = new LinkedHashMap<>();
        try (ResultSet fk = metaData.getImportedKeys(catalog, schemaPattern, tableName)) {
            while (fk.next()) {
                String fkName = Optional.ofNullable(fk.getString("FK_NAME")).orElse(tableName + "_fk");
                String referencedTable = fk.getString("PKTABLE_NAME");
                FkAccumulator acc = byFkName.computeIfAbsent(fkName,
                        k -> new FkAccumulator(referencedTable, new java.util.ArrayList<>(), new java.util.ArrayList<>()));
                acc.columnNames().add(fk.getString("FKCOLUMN_NAME"));
                acc.referencedColumns().add(fk.getString("PKCOLUMN_NAME"));
            }
        }
        byFkName.forEach((fkName, acc) -> table.addConstraint(new SchemaConstraint(ConstraintType.FOREIGN_KEY,
                fkName, acc.columnNames(), acc.referencedTable(), acc.referencedColumns())));
    }

    private void readIndexes(DatabaseMetaData metaData, String catalog, String schemaPattern, String tableName,
                              SchemaTable table) throws SQLException {
        record IndexAccumulator(boolean unique, List<String> columnNames) {
        }
        Map<String, IndexAccumulator> byIndexName = new LinkedHashMap<>();
        try (ResultSet index = metaData.getIndexInfo(catalog, schemaPattern, tableName, false, true)) {
            while (index.next()) {
                if (index.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String indexName = index.getString("INDEX_NAME");
                String columnName = index.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                boolean unique = !index.getBoolean("NON_UNIQUE");
                IndexAccumulator acc = byIndexName.computeIfAbsent(indexName,
                        k -> new IndexAccumulator(unique, new java.util.ArrayList<>()));
                acc.columnNames().add(columnName);
            }
        }
        byIndexName.forEach((indexName, acc) -> table.addConstraint(new SchemaConstraint(
                acc.unique() ? ConstraintType.UNIQUE : ConstraintType.INDEX, indexName, acc.columnNames(), null,
                null)));
    }

    private String defaultSchemaFor(DbType dbType) {
        return switch (dbType) {
            case POSTGRESQL -> "public";
            case H2 -> "PUBLIC";
            case MYSQL, MARIADB -> null;
        };
    }

    private NormalizedType normalize(int jdbcType) {
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                 Types.CLOB, Types.NCLOB -> NormalizedType.STRING;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE,
                 Types.NUMERIC, Types.DECIMAL -> NormalizedType.NUMBER;
            case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIME_WITH_TIMEZONE,
                 Types.TIMESTAMP_WITH_TIMEZONE -> NormalizedType.DATE_TIME;
            case Types.BOOLEAN, Types.BIT -> NormalizedType.BOOLEAN;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> NormalizedType.BINARY;
            default -> NormalizedType.OTHER;
        };
    }
}

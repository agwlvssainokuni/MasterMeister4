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
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.NonReadOnlyQueryException;
import cherry.mastermeister.common.exception.QueryExecutionTimeoutException;
import cherry.mastermeister.common.exception.QueryResultSizeExceededException;
import cherry.mastermeister.common.exception.QuerySchemaNotAccessibleException;
import cherry.mastermeister.common.exception.SchemaNotImportedException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.query.entity.QueryExecutionRecord;
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.model.AccessibleConnection;
import cherry.mastermeister.query.model.QueryResult;
import cherry.mastermeister.query.repository.QueryExecutionRecordRepository;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionService;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * COMP-14。business-logic-model.md §3〜6。ad-hoc実行・保存クエリ実行の共通オーケストレーションを担う。
 */
@Service
public class QueryExecutionService {

    private final RdbmsConnectionService rdbmsConnectionService;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final RdbmsDialectStrategyResolver dialectStrategyResolver;
    private final SavedQueryService savedQueryService;
    private final QueryExecutionRecordRepository queryExecutionRecordRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final AppProperties appProperties;

    // UNIT-05のPermissionYamlService/MasterDataControllerと同じ理由（Jacksonの自動Bean登録は
    // 行われずDI注入するとアプリ起動に失敗するため）で、DIに頼らずインスタンスを直接保持する。
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryExecutionService(RdbmsConnectionService rdbmsConnectionService,
                                  SchemaIntrospectionService schemaIntrospectionService,
                                  EffectivePermissionResolver effectivePermissionResolver,
                                  RdbmsDialectStrategyResolver dialectStrategyResolver,
                                  SavedQueryService savedQueryService,
                                  QueryExecutionRecordRepository queryExecutionRecordRepository,
                                  AuditEventPublisher auditEventPublisher, AppProperties appProperties) {
        this.rdbmsConnectionService = rdbmsConnectionService;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.dialectStrategyResolver = dialectStrategyResolver;
        this.savedQueryService = savedQueryService;
        this.queryExecutionRecordRepository = queryExecutionRecordRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.appProperties = appProperties;
    }

    /**
     * UNIT-05のlistAccessibleConnectionsと同じ判定。スキーマ未取込の接続は
     * （resolveAccessibleSchemasのように例外を投げず）アクセス不可としてスキップする。
     */
    public List<AccessibleConnection> listAccessibleConnections(Long userId) {
        return rdbmsConnectionService.listConnections().stream()
                .filter(connection -> isConnectionAccessible(userId, connection.getId()))
                .map(connection -> new AccessibleConnection(connection.getId(), connection.getDisplayName()))
                .toList();
    }

    private boolean isConnectionAccessible(Long userId, Long connectionId) {
        return schemaIntrospectionService.getSchema(connectionId)
                .map(snapshot -> snapshot.getTables().stream()
                        .anyMatch(table -> isTableAccessible(userId, connectionId, table)))
                .orElse(false);
    }

    /**
     * BR-QUERY-02。対象接続について、実行者がいずれかのテーブル/カラムに実効主権限READ以上を
     * 持つスキーマの一覧を返す。
     */
    public List<String> listAccessibleSchemas(Long userId, Long connectionId) {
        return List.copyOf(resolveAccessibleSchemas(userId, connectionId));
    }

    /**
     * STORY-7.1〜7.3。ad-hoc実行。接続・スキーマの両方を呼び出し側が指定する（BR-QUERY-02）。
     */
    public QueryResult execute(Long userId, Long connectionId, String sql, Map<String, String> params,
                                String schemaName, boolean pagingEnabled, int page, int pageSize) {
        return runQuery(userId, connectionId, null, sql, params, schemaName, pagingEnabled, page, pageSize);
    }

    /**
     * STORY-6.2, 7.1〜7.3。保存クエリ実行。接続は保存時点で固定、スキーマは実行時点で選択（BR-QUERY-03）。
     * SQLはユーザ入力を受け付けず編集不可として扱う（FR-7.9）。
     */
    public QueryResult executeSavedQuery(Long userId, Long connectionId, Long savedQueryId,
                                          Map<String, String> params, String schemaName, boolean pagingEnabled,
                                          int page, int pageSize) {
        SavedQuery savedQuery = savedQueryService.getSavedQuery(userId, connectionId, savedQueryId);
        return runQuery(userId, connectionId, savedQueryId, savedQuery.getSql(), params, schemaName, pagingEnabled,
                page, pageSize);
    }

    private QueryResult runQuery(Long userId, Long connectionId, Long savedQueryId, String sql,
                                  Map<String, String> params, String schemaName, boolean pagingEnabled, int page,
                                  int pageSize) {
        QuerySqlAnalyzer analyzer = new QuerySqlAnalyzer(sql);
        if (!analyzer.isReadOnly()) {
            throw new NonReadOnlyQueryException();
        }
        if (!resolveAccessibleSchemas(userId, connectionId).contains(schemaName)) {
            throw new QuerySchemaNotAccessibleException();
        }

        RdbmsConnection connection = rdbmsConnectionService.getConnection(connectionId);
        RdbmsDialectStrategy dialect = dialectStrategyResolver.resolve(connection.getDbType());
        DataSource dataSource = rdbmsConnectionService.getDataSource(connectionId);

        long start = System.currentTimeMillis();
        QueryResult result = executeOnPhysicalConnection(dataSource, dialect, schemaName, sql,
                analyzer.detectParameters(), params, pagingEnabled, page, pageSize);
        long durationMillis = System.currentTimeMillis() - start;
        result = new QueryResult(result.columns(), result.rows(), result.page(), result.pageSize(),
                result.totalCount(), result.rowCount(), durationMillis);

        persistExecutionRecord(userId, connectionId, schemaName, sql, params, savedQueryId, result, connection);
        return result;
    }

    private QueryResult executeOnPhysicalConnection(DataSource dataSource, RdbmsDialectStrategy dialect,
                                                      String schemaName, String sql, List<String> parameterNames,
                                                      Map<String, String> params, boolean pagingEnabled, int page,
                                                      int pageSize) {
        Connection physicalConnection;
        try {
            physicalConnection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire JDBC connection", e);
        }
        try {
            if (dialect.requiresSchemaSwitch()) {
                dialect.applySchemaSwitch(physicalConnection, schemaName);
            }
            SingleConnectionDataSource wrapped = new SingleConnectionDataSource(physicalConnection, true);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(wrapped);
            jdbcTemplate.setQueryTimeout(appProperties.query().executionTimeoutSeconds());
            NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

            MapSqlParameterSource paramSource = new MapSqlParameterSource();
            for (String name : parameterNames) {
                paramSource.addValue(name, params.get(name));
            }
            return executeWithPaging(namedTemplate, sql, paramSource, pagingEnabled, page, pageSize);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to switch schema", e);
        } catch (QueryTimeoutException e) {
            throw new QueryExecutionTimeoutException();
        } finally {
            try {
                physicalConnection.close();
            } catch (SQLException ignored) {
                // best-effort close
            }
        }
    }

    private QueryResult executeWithPaging(NamedParameterJdbcTemplate jdbcTemplate, String sql,
                                           MapSqlParameterSource params, boolean pagingEnabled, int page,
                                           int pageSize) {
        if (pagingEnabled) {
            Long totalCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (" + sql + ") AS mm_count", params, Long.class);
            MapSqlParameterSource pagedParams = new MapSqlParameterSource(params.getValues());
            pagedParams.addValue("mmPageSize", pageSize);
            pagedParams.addValue("mmOffset", (long) page * pageSize);
            RowsAndColumns rows = queryRows(jdbcTemplate,
                    "SELECT * FROM (" + sql + ") AS mm_page LIMIT :mmPageSize OFFSET :mmOffset", pagedParams);
            return new QueryResult(rows.columns(), rows.rows(), page, pageSize, totalCount == null ? 0L : totalCount,
                    rows.rows().size(), 0L);
        }
        int maxResultRows = appProperties.query().maxResultRows();
        MapSqlParameterSource cappedParams = new MapSqlParameterSource(params.getValues());
        cappedParams.addValue("mmLimit", maxResultRows + 1);
        RowsAndColumns rows = queryRows(jdbcTemplate,
                "SELECT * FROM (" + sql + ") AS mm_page LIMIT :mmLimit", cappedParams);
        if (rows.rows().size() > maxResultRows) {
            throw new QueryResultSizeExceededException(maxResultRows);
        }
        return new QueryResult(rows.columns(), rows.rows(), null, null, null, rows.rows().size(), 0L);
    }

    private record RowsAndColumns(List<String> columns, List<Map<String, String>> rows) {
    }

    private RowsAndColumns queryRows(NamedParameterJdbcTemplate jdbcTemplate, String sql,
                                      MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, params, rs -> {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            List<Map<String, String>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), formatValue(rs.getObject(i)));
                }
                rows.add(row);
            }
            return new RowsAndColumns(columns, rows);
        });
    }

    private String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        return String.valueOf(value);
    }

    private void persistExecutionRecord(Long userId, Long connectionId, String schemaName, String sql,
                                         Map<String, String> params, Long savedQueryId, QueryResult result,
                                         RdbmsConnection connection) {
        Instant now = Instant.now();
        String paramsJson = toJson(params);
        QueryExecutionRecord record = new QueryExecutionRecord(userId, connectionId, schemaName, sql, paramsJson,
                savedQueryId, result.rowCount(), result.durationMillis(), now);
        queryExecutionRecordRepository.save(record);
        auditEventPublisher.publish(new AuditEvent(now, userId, connectionId, AuditEventType.QUERY_EXECUTED,
                connection.getDisplayName() + " / " + schemaName, ResultStatus.SUCCESS,
                "件数=" + result.rowCount()));
    }

    private String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize query parameters", e);
        }
    }

    /**
     * BR-QUERY-02〜03。実行者自身の実効権限を評価する（実行時点、キャッシュはUNIT-04の
     * effectivePermissionキャッシュを再利用。新規の集約キャッシュ層は追加しない）。
     */
    private Set<String> resolveAccessibleSchemas(Long userId, Long connectionId) {
        SchemaSnapshot snapshot = schemaIntrospectionService.getSchema(connectionId)
                .orElseThrow(SchemaNotImportedException::new);
        Set<String> schemas = new LinkedHashSet<>();
        for (SchemaTable table : snapshot.getTables()) {
            if (isTableAccessible(userId, connectionId, table)) {
                schemas.add(table.getSchemaName());
            }
        }
        return schemas;
    }

    private boolean isTableAccessible(Long userId, Long connectionId, SchemaTable table) {
        if (effectivePermissionResolver.resolvePrimary(userId, connectionId, table.getSchemaName(),
                table.getTableName(), null) != PrimaryPermission.NONE) {
            return true;
        }
        return table.getColumns().stream()
                .anyMatch(column -> effectivePermissionResolver.resolvePrimary(userId, connectionId,
                        table.getSchemaName(), table.getTableName(), column.getColumnName())
                        != PrimaryPermission.NONE);
    }
}

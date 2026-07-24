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
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.BatchSizeExceededException;
import cherry.mastermeister.common.exception.MasterDataTableNotAccessibleException;
import cherry.mastermeister.common.exception.SchemaNotImportedException;
import cherry.mastermeister.masterdata.model.AccessibleConnection;
import cherry.mastermeister.masterdata.model.AccessibleTable;
import cherry.mastermeister.masterdata.model.BatchOperationItem;
import cherry.mastermeister.masterdata.model.BatchOperationResult;
import cherry.mastermeister.masterdata.model.OperationType;
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.masterdata.model.RecordFilterCondition;
import cherry.mastermeister.masterdata.model.RecordPage;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionService;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * COMP-13。business-logic-model.md §1・§2・§4。
 * アクセス可能な接続・テーブル/ビュー一覧の取得（UNIT-04の{@link EffectivePermissionResolver}・
 * UNIT-03の{@link SchemaIntrospectionService}を直接呼び出し）、レコード一覧取得・一括反映の
 * オーケストレーション（{@link RecordQueryService}/{@link RecordBatchService}への委譲、
 * 監査イベントの発行判断）を担う。
 */
@Service
public class MasterDataService {

    private final RdbmsConnectionService rdbmsConnectionService;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final RdbmsDialectStrategyResolver dialectStrategyResolver;
    private final ColumnDataTypeMapper columnDataTypeMapper;
    private final RawQueryConditionValidator rawQueryConditionValidator;
    private final RecordQueryService recordQueryService;
    private final RecordBatchService recordBatchService;
    private final AuditEventPublisher auditEventPublisher;
    private final AppProperties appProperties;

    public MasterDataService(RdbmsConnectionService rdbmsConnectionService,
                              SchemaIntrospectionService schemaIntrospectionService,
                              EffectivePermissionResolver effectivePermissionResolver,
                              RdbmsDialectStrategyResolver dialectStrategyResolver,
                              ColumnDataTypeMapper columnDataTypeMapper,
                              RawQueryConditionValidator rawQueryConditionValidator,
                              RecordQueryService recordQueryService, RecordBatchService recordBatchService,
                              AuditEventPublisher auditEventPublisher, AppProperties appProperties) {
        this.rdbmsConnectionService = rdbmsConnectionService;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.dialectStrategyResolver = dialectStrategyResolver;
        this.columnDataTypeMapper = columnDataTypeMapper;
        this.rawQueryConditionValidator = rawQueryConditionValidator;
        this.recordQueryService = recordQueryService;
        this.recordBatchService = recordBatchService;
        this.auditEventPublisher = auditEventPublisher;
        this.appProperties = appProperties;
    }

    /**
     * BR-MASTER-13。スキーマ取込済みかつ、いずれかのテーブルがBR-MASTER-01の可視条件を満たす接続のみ返す。
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
                        .anyMatch(table -> isTableVisible(userId, connectionId, table)))
                .orElse(false);
    }

    /**
     * BR-MASTER-01〜03。テーブル自身またはいずれかのカラムの実効主権限がREAD以上のテーブル/ビューを返す。
     */
    public List<AccessibleTable> listAccessibleTables(Long userId, Long connectionId) {
        SchemaSnapshot snapshot = schemaIntrospectionService.getSchema(connectionId)
                .orElseThrow(SchemaNotImportedException::new);
        return snapshot.getTables().stream()
                .filter(table -> isTableVisible(userId, connectionId, table))
                .map(table -> toAccessibleTable(userId, connectionId, table))
                .toList();
    }

    private boolean isTableVisible(Long userId, Long connectionId, SchemaTable table) {
        if (effectivePermissionResolver.resolvePrimary(userId, connectionId, table.getSchemaName(),
                table.getTableName(), null) != PrimaryPermission.NONE) {
            return true;
        }
        return table.getColumns().stream()
                .anyMatch(column -> effectivePermissionResolver.resolvePrimary(userId, connectionId,
                        table.getSchemaName(), table.getTableName(), column.getColumnName())
                        != PrimaryPermission.NONE);
    }

    private AccessibleTable toAccessibleTable(Long userId, Long connectionId, SchemaTable table) {
        boolean creatable = table.getTableType() == TableType.TABLE
                && effectivePermissionResolver.canCreate(userId, connectionId, table.getSchemaName(),
                table.getTableName());
        boolean deletable = table.getTableType() == TableType.TABLE
                && effectivePermissionResolver.canDelete(userId, connectionId, table.getSchemaName(),
                table.getTableName());
        return new AccessibleTable(table.getSchemaName(), table.getTableName(), table.getTableType(), creatable,
                deletable);
    }

    /**
     * BR-MASTER-04〜05, 10, 14, 15。
     */
    public RecordPage getRecords(Long userId, Long connectionId, String schemaName, String tableName,
                                  List<RecordFilterCondition> filters, String rawWhere, String rawOrderBy, int page,
                                  int pageSize) {
        RdbmsConnection connection = rdbmsConnectionService.getConnection(connectionId);
        SchemaTable table = findVisibleTable(userId, connectionId, schemaName, tableName);
        List<RecordColumn> columns = resolveVisibleColumns(userId, connectionId, table);
        List<String> primaryKeyColumns = primaryKeyColumns(table);
        boolean creatable = table.getTableType() == TableType.TABLE
                && effectivePermissionResolver.canCreate(userId, connectionId, schemaName, tableName);
        boolean deletable = table.getTableType() == TableType.TABLE
                && effectivePermissionResolver.canDelete(userId, connectionId, schemaName, tableName);

        RdbmsDialectStrategy dialect = dialectStrategyResolver.resolve(connection.getDbType());
        DataSource dataSource = rdbmsConnectionService.getDataSource(connectionId);
        RawQueryConditionValidator.Validated validated = rawQueryConditionValidator.validate(dialect, rawWhere,
                rawOrderBy);

        RecordPage recordPage = recordQueryService.queryRecords(dataSource, dialect, schemaName, tableName, columns,
                primaryKeyColumns, filters, validated, page, pageSize, creatable, deletable);

        if (recordPage.rows().size() >= appProperties.audit().bulkAccessThreshold()) {
            auditEventPublisher.publish(new AuditEvent(Instant.now(), userId, connectionId,
                    AuditEventType.MASTER_DATA_BULK_ACCESSED, targetResource(connection, schemaName, tableName),
                    ResultStatus.SUCCESS, "件数=" + recordPage.rows().size()));
        }
        return recordPage;
    }

    /**
     * BR-MASTER-06〜09。
     */
    public BatchOperationResult applyBatch(Long userId, Long connectionId, String schemaName, String tableName,
                                            List<BatchOperationItem> operations) {
        int maxSize = appProperties.masterdata().batchMaxSize();
        if (operations.size() > maxSize) {
            throw new BatchSizeExceededException(maxSize);
        }
        RdbmsConnection connection = rdbmsConnectionService.getConnection(connectionId);
        SchemaTable table = findVisibleTable(userId, connectionId, schemaName, tableName);
        List<RecordColumn> columns = resolveVisibleColumns(userId, connectionId, table);
        List<String> primaryKeyColumns = primaryKeyColumns(table);
        boolean creatable = table.getTableType() == TableType.TABLE
                && effectivePermissionResolver.canCreate(userId, connectionId, schemaName, tableName);
        boolean deletable = table.getTableType() == TableType.TABLE
                && effectivePermissionResolver.canDelete(userId, connectionId, schemaName, tableName);

        RdbmsDialectStrategy dialect = dialectStrategyResolver.resolve(connection.getDbType());
        DataSource dataSource = rdbmsConnectionService.getDataSource(connectionId);

        BatchOperationResult result = recordBatchService.apply(dataSource, dialect, schemaName, tableName, columns,
                creatable, deletable, primaryKeyColumns, operations);

        if (result.success()) {
            auditEventPublisher.publish(new AuditEvent(Instant.now(), userId, connectionId,
                    AuditEventType.MASTER_DATA_BATCH_APPLIED, targetResource(connection, schemaName, tableName),
                    ResultStatus.SUCCESS, operationCountSummary(operations)));
        }
        return result;
    }

    private String operationCountSummary(List<BatchOperationItem> operations) {
        long created = operations.stream().filter(o -> o.operationType() == OperationType.CREATE).count();
        long updated = operations.stream().filter(o -> o.operationType() == OperationType.UPDATE).count();
        long deleted = operations.stream().filter(o -> o.operationType() == OperationType.DELETE).count();
        return "作成=" + created + ", 更新=" + updated + ", 削除=" + deleted;
    }

    private String targetResource(RdbmsConnection connection, String schemaName, String tableName) {
        return connection.getDisplayName() + " / " + schemaName + "." + tableName;
    }

    /**
     * BR-MASTER-01の可視条件を満たす対象テーブルを取得する。存在しない場合・可視でない場合は
     * 同一の404として扱う（{@link MasterDataTableNotAccessibleException}、フェイルクローズ）。
     */
    private SchemaTable findVisibleTable(Long userId, Long connectionId, String schemaName, String tableName) {
        SchemaSnapshot snapshot = schemaIntrospectionService.getSchema(connectionId)
                .orElseThrow(SchemaNotImportedException::new);
        SchemaTable table = snapshot.getTables().stream()
                .filter(t -> t.getSchemaName().equals(schemaName) && t.getTableName().equals(tableName))
                .findFirst()
                .orElseThrow(MasterDataTableNotAccessibleException::new);
        if (!isTableVisible(userId, connectionId, table)) {
            throw new MasterDataTableNotAccessibleException();
        }
        return table;
    }

    /**
     * BR-MASTER-14。実効主権限がNONEのカラムはRecordColumn自体を生成しない（完全除外）。
     */
    private List<RecordColumn> resolveVisibleColumns(Long userId, Long connectionId, SchemaTable table) {
        boolean readOnlyTable = table.getTableType() == TableType.VIEW;
        return table.getColumns().stream()
                .map(column -> toRecordColumn(userId, connectionId, table, column, readOnlyTable))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private RecordColumn toRecordColumn(Long userId, Long connectionId, SchemaTable table, SchemaColumn column,
                                         boolean readOnlyTable) {
        PrimaryPermission permission = effectivePermissionResolver.resolvePrimary(userId, connectionId,
                table.getSchemaName(), table.getTableName(), column.getColumnName());
        if (permission == PrimaryPermission.NONE) {
            return null;
        }
        boolean editable = !readOnlyTable && permission == PrimaryPermission.UPDATE;
        boolean primaryKey = isPrimaryKeyColumn(table, column.getColumnName());
        return new RecordColumn(column.getColumnName(), columnDataTypeMapper.toCategory(column.getNormalizedType()),
                primaryKey, editable);
    }

    private boolean isPrimaryKeyColumn(SchemaTable table, String columnName) {
        return primaryKeyColumns(table).contains(columnName);
    }

    private List<String> primaryKeyColumns(SchemaTable table) {
        Optional<SchemaConstraint> pk = table.getConstraints().stream()
                .filter(c -> c.getConstraintType() == ConstraintType.PRIMARY_KEY)
                .findFirst();
        return pk.map(SchemaConstraint::getColumnNames).orElse(List.of());
    }
}

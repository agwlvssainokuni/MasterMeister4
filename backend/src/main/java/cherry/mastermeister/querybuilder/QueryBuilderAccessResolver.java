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
import cherry.mastermeister.common.exception.SchemaNotImportedException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.query.QueryExecutionService;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderColumnResponse;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderTableResponse;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * business-logic-model.md §1。BR-QUERYBUILDER-01。nfr-design-patterns.md §1.3・3.1。
 * アクセス可能テーブル/カラム一覧取得、およびリバースエンジニアリング時の参照テーブル/カラムの
 * 存在・アクセス可否確認を担う。UNIT-05の{@code MasterDataService}には依存せず、UNIT-03の
 * {@link SchemaIntrospectionService}・UNIT-04の{@link EffectivePermissionResolver}を直接
 * 組み合わせて独自に実装する（unit-of-work.mdの前提ユニット定義どおり）。
 */
@Component
public class QueryBuilderAccessResolver {

    private final SchemaIntrospectionService schemaIntrospectionService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final QueryExecutionService queryExecutionService;
    private final QueryBuilderColumnTypeMapper columnTypeMapper;

    public QueryBuilderAccessResolver(SchemaIntrospectionService schemaIntrospectionService,
                                       EffectivePermissionResolver effectivePermissionResolver,
                                       QueryExecutionService queryExecutionService,
                                       QueryBuilderColumnTypeMapper columnTypeMapper) {
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.queryExecutionService = queryExecutionService;
        this.columnTypeMapper = columnTypeMapper;
    }

    /**
     * FROM/JOINタブのテーブル候補、他タブのカラム候補を返す。対象スキーマがUNIT-06の
     * スキーマ許可リスト（BR-QUERY-02）に含まれない場合は{@link QuerySchemaNotAccessibleException}。
     */
    public List<AccessibleBuilderTableResponse> listAccessibleTables(Long userId, Long connectionId,
                                                                       String schemaName) {
        if (!queryExecutionService.listAccessibleSchemas(userId, connectionId).contains(schemaName)) {
            throw new QuerySchemaNotAccessibleException();
        }
        SchemaSnapshot snapshot = schemaIntrospectionService.getSchema(connectionId)
                .orElseThrow(SchemaNotImportedException::new);
        return snapshot.getTables().stream()
                .filter(table -> table.getSchemaName().equals(schemaName))
                .filter(table -> isTableVisible(userId, connectionId, table))
                .map(table -> toResponse(userId, connectionId, table))
                .toList();
    }

    /**
     * BR-QUERYBUILDER-01・07。リバースエンジニアリング時、参照するテーブル/カラムが構造メタデータ上
     * 存在し、かつ実効権限READ以上を持つかを1メソッドで確認する（存在確認のみを示唆する命名は
     * 権限チェックの実施有無について誤解を招くため避ける、nfr-design/logical-components.md参照）。
     */
    public boolean isColumnAccessible(Long userId, Long connectionId, String schemaName, String tableName,
                                       String columnName) {
        return schemaIntrospectionService.getSchema(connectionId)
                .flatMap(snapshot -> snapshot.getTables().stream()
                        .filter(table -> table.getSchemaName().equals(schemaName)
                                && table.getTableName().equals(tableName))
                        .findFirst())
                .flatMap(table -> table.getColumns().stream()
                        .filter(column -> column.getColumnName().equals(columnName))
                        .findFirst())
                .map(column -> effectivePermissionResolver.resolvePrimary(userId, connectionId, schemaName,
                        tableName, columnName) != PrimaryPermission.NONE)
                .orElse(false);
    }

    /**
     * UNIT-05の{@code MasterDataService.isTableVisible()}と同じOR条件（BR-QUERYBUILDER-01）。
     * テーブル単位・スキーマ単位の設定がNONEでも、個別の列単位設定でREAD以上が付与されている
     * ケースを正しく拾うため、テーブル単位の判定のみで除外してはならない。
     */
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

    private AccessibleBuilderTableResponse toResponse(Long userId, Long connectionId, SchemaTable table) {
        List<AccessibleBuilderColumnResponse> columns = table.getColumns().stream()
                .filter(column -> effectivePermissionResolver.resolvePrimary(userId, connectionId,
                        table.getSchemaName(), table.getTableName(), column.getColumnName())
                        != PrimaryPermission.NONE)
                .map(this::toColumnResponse)
                .toList();
        return new AccessibleBuilderTableResponse(table.getTableName(), table.getTableType(), columns);
    }

    private AccessibleBuilderColumnResponse toColumnResponse(SchemaColumn column) {
        return new AccessibleBuilderColumnResponse(column.getColumnName(),
                columnTypeMapper.toCategory(column.getNormalizedType()));
    }
}

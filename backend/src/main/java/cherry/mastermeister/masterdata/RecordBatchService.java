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

import cherry.mastermeister.masterdata.model.BatchOperationItem;
import cherry.mastermeister.masterdata.model.BatchOperationItemResult;
import cherry.mastermeister.masterdata.model.BatchOperationResult;
import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * logical-components.md §1。business-rules.md BR-MASTER-06〜09。
 * 一括反映（作成・更新・削除混在バッチ）のオールオアナッシングを担う。
 * <p>
 * 重要な設計判断（nfr-design-patterns.md §1.1・§2.1、レビュー指摘の反映）: Spring Bootの
 * {@code @Transactional}は既定でアプリ内部DB（H2/JPA）のトランザクションマネージャに紐づき、
 * 動的に選択される対象RDBMSの{@code DataSource}とは無関係であるため、リクエストごとに
 * {@link DataSourceTransactionManager}を明示的に生成し{@link TransactionTemplate}でトランザクション
 * 制御する。{@link NamedParameterJdbcTemplate}は、同一の{@code DataSource}に対して
 * {@code TransactionTemplate}のコールバック内で操作される限り、Springの
 * {@code DataSourceUtils}によるコネクション同期を通じてこのトランザクションに正しく参加する。
 */
@Service
public class RecordBatchService {

    public record ValidationOutcome(boolean valid, String errorCode, String errorMessage) {

        static final ValidationOutcome OK = new ValidationOutcome(true, null, null);

        static ValidationOutcome fail(String errorCode, String errorMessage) {
            return new ValidationOutcome(false, errorCode, errorMessage);
        }
    }

    /**
     * @param columns           表示対象カラム（実効主権限READ以上。editable=trueがUPDATE可）
     * @param creatable         テーブルが作成可能か（{@code canCreate()}の結果）
     * @param deletable         テーブルが削除可能か（{@code canDelete()}の結果）
     * @param primaryKeyColumns 主キー構成列（更新・削除対象行の識別に使用、BR-MASTER-08）
     */
    public BatchOperationResult apply(DataSource dataSource, RdbmsDialectStrategy dialect, String schemaName,
                                       String tableName, List<RecordColumn> columns, boolean creatable,
                                       boolean deletable, List<String> primaryKeyColumns,
                                       List<BatchOperationItem> operations) {
        // BR-MASTER-07 Step 1: 権限チェック・値のパース可否をDB反映前に全件事前検証する
        List<BatchOperationItemResult> failures = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            ValidationOutcome outcome = preValidate(operations.get(i), columns, creatable, deletable,
                    primaryKeyColumns);
            if (!outcome.valid()) {
                failures.add(new BatchOperationItemResult(i, outcome.errorCode(), outcome.errorMessage()));
            }
        }
        if (!failures.isEmpty()) {
            return new BatchOperationResult(false, failures);
        }

        // BR-MASTER-07 Step 2: 事前検証を通過した場合のみ、実際のDB制約チェックを兼ねて個別SQLを実行する
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return transactionTemplate.execute(status -> {
            NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
            List<BatchOperationItemResult> executionFailures = new ArrayList<>();
            for (int i = 0; i < operations.size(); i++) {
                BatchOperationItemResult failure = executeOperation(jdbcTemplate, dialect, schemaName, tableName,
                        columns, primaryKeyColumns, operations.get(i), i);
                if (failure != null) {
                    executionFailures.add(failure);
                }
            }
            if (!executionFailures.isEmpty()) {
                status.setRollbackOnly();
                return new BatchOperationResult(false, executionFailures);
            }
            return new BatchOperationResult(true, List.of());
        });
    }

    private ValidationOutcome preValidate(BatchOperationItem item, List<RecordColumn> columns, boolean creatable,
                                           boolean deletable, List<String> primaryKeyColumns) {
        return switch (item.operationType()) {
            case CREATE -> preValidateCreate(item, columns, creatable);
            case UPDATE -> preValidateUpdate(item, columns, primaryKeyColumns);
            case DELETE -> preValidateDelete(item, deletable, primaryKeyColumns);
        };
    }

    private ValidationOutcome preValidateCreate(BatchOperationItem item, List<RecordColumn> columns,
                                                 boolean creatable) {
        if (!creatable) {
            return ValidationOutcome.fail("PERMISSION_DENIED", "作成権限がありません");
        }
        Map<String, String> columnValues = item.columnValues();
        if (columnValues == null || columnValues.isEmpty()) {
            return ValidationOutcome.fail("INVALID_VALUE", "作成する値が指定されていません");
        }
        return validateColumnValuesEditable(columnValues, columns);
    }

    private ValidationOutcome preValidateUpdate(BatchOperationItem item, List<RecordColumn> columns,
                                                 List<String> primaryKeyColumns) {
        ValidationOutcome pkOutcome = validatePrimaryKeyPresent(item, primaryKeyColumns);
        if (!pkOutcome.valid()) {
            return pkOutcome;
        }
        Map<String, String> columnValues = item.columnValues();
        if (columnValues == null || columnValues.isEmpty()) {
            return ValidationOutcome.fail("INVALID_VALUE", "更新する値が指定されていません");
        }
        return validateColumnValuesEditable(columnValues, columns);
    }

    private ValidationOutcome preValidateDelete(BatchOperationItem item, boolean deletable,
                                                 List<String> primaryKeyColumns) {
        if (!deletable) {
            return ValidationOutcome.fail("PERMISSION_DENIED", "削除権限がありません");
        }
        return validatePrimaryKeyPresent(item, primaryKeyColumns);
    }

    private ValidationOutcome validatePrimaryKeyPresent(BatchOperationItem item, List<String> primaryKeyColumns) {
        if (primaryKeyColumns.isEmpty()) {
            return ValidationOutcome.fail("PERMISSION_DENIED", "主キーを持たないテーブルは対象外です");
        }
        Map<String, String> primaryKeyValues = item.primaryKeyValues();
        if (primaryKeyValues == null || !primaryKeyValues.keySet().containsAll(primaryKeyColumns)) {
            return ValidationOutcome.fail("INVALID_VALUE", "対象行の主キー値が不足しています");
        }
        return ValidationOutcome.OK;
    }

    /**
     * CREATE/UPDATEで値を設定できるのは、実効主権限UPDATE（editable=true）のカラムに限る
     * （実装判断: 表示対象外・READ止まりのカラムへの値設定を許容しない、レビュー観点の反映）。
     */
    private ValidationOutcome validateColumnValuesEditable(Map<String, String> columnValues,
                                                            List<RecordColumn> columns) {
        Map<String, RecordColumn> editableByName = columns.stream()
                .filter(RecordColumn::editable)
                .collect(java.util.stream.Collectors.toMap(RecordColumn::columnName, c -> c));
        for (String columnName : columnValues.keySet()) {
            if (!editableByName.containsKey(columnName)) {
                return ValidationOutcome.fail("PERMISSION_DENIED", "編集権限のないカラムです: " + columnName);
            }
        }
        return ValidationOutcome.OK;
    }

    private BatchOperationItemResult executeOperation(NamedParameterJdbcTemplate jdbcTemplate,
                                                        RdbmsDialectStrategy dialect, String schemaName,
                                                        String tableName, List<RecordColumn> columns,
                                                        List<String> primaryKeyColumns, BatchOperationItem item,
                                                        int index) {
        String qualifiedTable = dialect.quoteIdentifier(schemaName) + "." + dialect.quoteIdentifier(tableName);
        try {
            return switch (item.operationType()) {
                case CREATE -> executeCreate(jdbcTemplate, dialect, qualifiedTable, columns, item, index);
                case UPDATE -> executeUpdate(jdbcTemplate, dialect, qualifiedTable, columns, primaryKeyColumns, item,
                        index);
                case DELETE -> executeDelete(jdbcTemplate, dialect, qualifiedTable, columns, primaryKeyColumns, item,
                        index);
            };
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return new BatchOperationItemResult(index, "INVALID_VALUE", e.getMessage());
        } catch (DataAccessException e) {
            return new BatchOperationItemResult(index, "CONSTRAINT_VIOLATION", rootMessage(e));
        }
    }

    private BatchOperationItemResult executeCreate(NamedParameterJdbcTemplate jdbcTemplate,
                                                    RdbmsDialectStrategy dialect, String qualifiedTable,
                                                    List<RecordColumn> columns, BatchOperationItem item, int index) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> columnNames = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> entry : item.columnValues().entrySet()) {
            String paramName = "p" + (i++);
            columnNames.add(dialect.quoteIdentifier(entry.getKey()));
            placeholders.add(":" + paramName);
            params.addValue(paramName, parseValue(entry.getValue(), categoryOf(columns, entry.getKey())));
        }
        String sql = "INSERT INTO " + qualifiedTable + " (" + String.join(", ", columnNames) + ") VALUES ("
                + String.join(", ", placeholders) + ")";
        int updated = jdbcTemplate.update(sql, params);
        return updated == 1 ? null : new BatchOperationItemResult(index, "CONSTRAINT_VIOLATION", "作成に失敗しました");
    }

    private BatchOperationItemResult executeUpdate(NamedParameterJdbcTemplate jdbcTemplate,
                                                    RdbmsDialectStrategy dialect, String qualifiedTable,
                                                    List<RecordColumn> columns, List<String> primaryKeyColumns,
                                                    BatchOperationItem item, int index) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> assignments = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> entry : item.columnValues().entrySet()) {
            String paramName = "p" + (i++);
            assignments.add(dialect.quoteIdentifier(entry.getKey()) + " = :" + paramName);
            params.addValue(paramName, parseValue(entry.getValue(), categoryOf(columns, entry.getKey())));
        }
        String whereClause = buildPrimaryKeyWhereClause(dialect, primaryKeyColumns, item.primaryKeyValues(), params,
                columns);
        String sql = "UPDATE " + qualifiedTable + " SET " + String.join(", ", assignments) + " WHERE " + whereClause;
        int updated = jdbcTemplate.update(sql, params);
        return updated == 1 ? null : new BatchOperationItemResult(index, "RECORD_NOT_FOUND", "対象行が見つかりません");
    }

    private BatchOperationItemResult executeDelete(NamedParameterJdbcTemplate jdbcTemplate,
                                                    RdbmsDialectStrategy dialect, String qualifiedTable,
                                                    List<RecordColumn> columns, List<String> primaryKeyColumns,
                                                    BatchOperationItem item, int index) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildPrimaryKeyWhereClause(dialect, primaryKeyColumns, item.primaryKeyValues(), params,
                columns);
        String sql = "DELETE FROM " + qualifiedTable + " WHERE " + whereClause;
        int updated = jdbcTemplate.update(sql, params);
        return updated == 1 ? null : new BatchOperationItemResult(index, "RECORD_NOT_FOUND", "対象行が見つかりません");
    }

    /**
     * 主キー列のデータ型カテゴリはcolumnsから解決する（実機E2E検証で判明したレビュー指摘の反映）。
     * 当初、DELETEではcolumnsを渡さずSTRINGにフォールバックしていたが、PostgreSQLはSTRING値と
     * INTEGER列の比較を暗黙変換せずエラーとするため、UPDATE同様に必ず実際の型で解決する必要がある
     * （BR-ACCESS-07によりcanDelete()がtrueの場合、全主キー列は実効主権限READ以上でありcolumnsに
     * 必ず含まれるため、常に解決可能）。
     */
    private String buildPrimaryKeyWhereClause(RdbmsDialectStrategy dialect, List<String> primaryKeyColumns,
                                               Map<String, String> primaryKeyValues, MapSqlParameterSource params,
                                               List<RecordColumn> columns) {
        List<String> conditions = new ArrayList<>();
        int i = 0;
        for (String pkColumn : primaryKeyColumns) {
            String paramName = "pk" + (i++);
            conditions.add(dialect.quoteIdentifier(pkColumn) + " = :" + paramName);
            params.addValue(paramName, parseValue(primaryKeyValues.get(pkColumn), categoryOf(columns, pkColumn)));
        }
        return String.join(" AND ", conditions);
    }

    private ColumnDataTypeCategory categoryOf(List<RecordColumn> columns, String columnName) {
        return columns.stream()
                .filter(c -> c.columnName().equals(columnName))
                .findFirst()
                .map(RecordColumn::dataTypeCategory)
                .orElse(ColumnDataTypeCategory.STRING);
    }

    private Object parseValue(String rawValue, ColumnDataTypeCategory category) {
        if (rawValue == null) {
            return null;
        }
        return switch (category) {
            case NUMERIC -> new BigDecimal(rawValue);
            case BOOLEAN -> parseBoolean(rawValue);
            case DATETIME -> parseDateTime(rawValue);
            case STRING -> rawValue;
        };
    }

    private Boolean parseBoolean(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("真偽値として解釈できません: " + rawValue);
    }

    private Object parseDateTime(String rawValue) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(rawValue));
        } catch (DateTimeParseException e) {
            return java.sql.Date.valueOf(LocalDate.parse(rawValue));
        }
    }

    private String rootMessage(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause.getMessage();
    }
}

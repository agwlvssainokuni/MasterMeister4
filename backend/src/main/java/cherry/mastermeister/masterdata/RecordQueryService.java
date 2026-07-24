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

import cherry.mastermeister.common.exception.InvalidQueryConditionException;
import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.masterdata.model.FilterOperator;
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.masterdata.model.RecordFilterCondition;
import cherry.mastermeister.masterdata.model.RecordPage;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * logical-components.md §1。business-logic-model.md §3。
 * {@code NamedParameterJdbcTemplate}による動的SELECT文の組み立て・実行を担う（読み取り専用のため
 * 明示的なトランザクション制御は不要、nfr-design-patterns.md §2.1）。
 */
@Service
public class RecordQueryService {

    private static final String LIKE_ESCAPE = "\\";

    /**
     * @param columns          表示対象カラム（実効主権限READ以上、BR-MASTER-14）。SELECT対象・応答マッピングに使用
     * @param defaultSortColumns ORDER BY未指定時の既定ソート対象列（通常は主キー列。安定したページングのため）
     */
    public RecordPage queryRecords(DataSource dataSource, RdbmsDialectStrategy dialect, String schemaName,
                                    String tableName, List<RecordColumn> columns, List<String> defaultSortColumns,
                                    List<RecordFilterCondition> filters,
                                    RawQueryConditionValidator.Validated rawQuery, int page, int pageSize,
                                    boolean creatable, boolean deletable) {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        String qualifiedTable = dialect.quoteIdentifier(schemaName) + "." + dialect.quoteIdentifier(tableName);

        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(dialect, columns, filters, rawQuery, params);

        long totalCount = queryCount(jdbcTemplate, qualifiedTable, whereClause, params);

        String orderByClause = rawQuery.orderBySql() != null ? rawQuery.orderBySql()
                : defaultOrderBy(dialect, defaultSortColumns);
        String selectColumns = columns.stream()
                .map(c -> dialect.quoteIdentifier(c.columnName()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("*");
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns).append(" FROM ")
                .append(qualifiedTable);
        if (whereClause != null) {
            sql.append(" WHERE ").append(whereClause);
        }
        if (orderByClause != null) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        sql.append(" LIMIT :pageSize OFFSET :offset");
        params.addValue("pageSize", pageSize);
        params.addValue("offset", (long) page * pageSize);

        List<Map<String, String>> rows;
        try {
            rows = jdbcTemplate.query(sql.toString(), params,
                    (rs, rowNum) -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (RecordColumn column : columns) {
                            Object value = rs.getObject(column.columnName());
                            row.put(column.columnName(), formatValue(value));
                        }
                        return row;
                    });
        } catch (DataAccessException e) {
            throw new InvalidQueryConditionException();
        }

        return new RecordPage(columns, rows, page, pageSize, totalCount, creatable, deletable);
    }

    private long queryCount(NamedParameterJdbcTemplate jdbcTemplate, String qualifiedTable, String whereClause,
                             MapSqlParameterSource params) {
        String countSql = "SELECT COUNT(*) FROM " + qualifiedTable
                + (whereClause != null ? " WHERE " + whereClause : "");
        try {
            Long count = jdbcTemplate.queryForObject(countSql, params, Long.class);
            return count == null ? 0L : count;
        } catch (DataAccessException e) {
            throw new InvalidQueryConditionException();
        }
    }

    private String buildWhereClause(RdbmsDialectStrategy dialect, List<RecordColumn> columns,
                                     List<RecordFilterCondition> filters,
                                     RawQueryConditionValidator.Validated rawQuery, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < filters.size(); i++) {
            conditions.add(buildFilterCondition(dialect, columns, filters.get(i), i, params));
        }
        if (rawQuery.whereSql() != null) {
            conditions.add(rawQuery.whereSql());
            params.addValues(rawQuery.whereParams());
        }
        if (conditions.isEmpty()) {
            return null;
        }
        return String.join(" AND ", conditions.stream().map(c -> "(" + c + ")").toList());
    }

    private String buildFilterCondition(RdbmsDialectStrategy dialect, List<RecordColumn> columns,
                                         RecordFilterCondition filter, int index, MapSqlParameterSource params) {
        RecordColumn column = columns.stream()
                .filter(c -> c.columnName().equals(filter.columnName()))
                .findFirst()
                .orElseThrow(InvalidQueryConditionException::new);
        String quotedColumn = dialect.quoteIdentifier(column.columnName());
        String paramName = "filter" + index;

        return switch (filter.operator()) {
            case EQ -> {
                params.addValue(paramName, parseValue(filter.value(), column.dataTypeCategory()));
                yield quotedColumn + " = :" + paramName;
            }
            case LT -> {
                params.addValue(paramName, parseValue(filter.value(), column.dataTypeCategory()));
                yield quotedColumn + " < :" + paramName;
            }
            case LE -> {
                params.addValue(paramName, parseValue(filter.value(), column.dataTypeCategory()));
                yield quotedColumn + " <= :" + paramName;
            }
            case GT -> {
                params.addValue(paramName, parseValue(filter.value(), column.dataTypeCategory()));
                yield quotedColumn + " > :" + paramName;
            }
            case GE -> {
                params.addValue(paramName, parseValue(filter.value(), column.dataTypeCategory()));
                yield quotedColumn + " >= :" + paramName;
            }
            case BETWEEN -> {
                if (filter.valueTo() == null) {
                    throw new InvalidQueryConditionException();
                }
                params.addValue(paramName, parseValue(filter.value(), column.dataTypeCategory()));
                params.addValue(paramName + "To", parseValue(filter.valueTo(), column.dataTypeCategory()));
                yield quotedColumn + " BETWEEN :" + paramName + " AND :" + paramName + "To";
            }
            case STARTS_WITH -> {
                params.addValue(paramName, escapeLike(filter.value()) + "%");
                yield quotedColumn + " LIKE :" + paramName + " ESCAPE '" + LIKE_ESCAPE + "'";
            }
            case CONTAINS -> {
                params.addValue(paramName, "%" + escapeLike(filter.value()) + "%");
                yield quotedColumn + " LIKE :" + paramName + " ESCAPE '" + LIKE_ESCAPE + "'";
            }
        };
    }

    private String escapeLike(String value) {
        return value.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    private Object parseValue(String rawValue, ColumnDataTypeCategory category) {
        if (rawValue == null) {
            return null;
        }
        try {
            return switch (category) {
                case NUMERIC -> new BigDecimal(rawValue);
                case BOOLEAN -> parseBoolean(rawValue);
                case DATETIME -> parseDateTime(rawValue);
                case STRING -> rawValue;
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new InvalidQueryConditionException();
        }
    }

    private Boolean parseBoolean(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return Boolean.FALSE;
        }
        throw new InvalidQueryConditionException();
    }

    private Object parseDateTime(String rawValue) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(rawValue));
        } catch (DateTimeParseException e) {
            return java.sql.Date.valueOf(LocalDate.parse(rawValue));
        }
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

    private String defaultOrderBy(RdbmsDialectStrategy dialect, List<String> defaultSortColumns) {
        if (defaultSortColumns == null || defaultSortColumns.isEmpty()) {
            return null;
        }
        return defaultSortColumns.stream().map(dialect::quoteIdentifier).reduce((a, b) -> a + ", " + b).orElse(null);
    }
}

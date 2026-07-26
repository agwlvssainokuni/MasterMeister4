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

import cherry.mastermeister.common.exception.QueryBuilderInvalidGroupByException;
import cherry.mastermeister.common.exception.QueryBuilderInvalidLiteralException;
import cherry.mastermeister.common.exception.QueryBuilderReferenceNotAccessibleException;
import cherry.mastermeister.common.exception.QueryBuilderUnsupportedSqlException;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderColumnResponse;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderTableResponse;
import cherry.mastermeister.querybuilder.dto.AggregateExpressionDto;
import cherry.mastermeister.querybuilder.dto.AggregateFunction;
import cherry.mastermeister.querybuilder.dto.ColumnDataTypeCategory;
import cherry.mastermeister.querybuilder.dto.ColumnRefDto;
import cherry.mastermeister.querybuilder.dto.ConditionDto;
import cherry.mastermeister.querybuilder.dto.ConditionOperator;
import cherry.mastermeister.querybuilder.dto.FromClauseDto;
import cherry.mastermeister.querybuilder.dto.JoinClauseDto;
import cherry.mastermeister.querybuilder.dto.JoinConditionDto;
import cherry.mastermeister.querybuilder.dto.JoinType;
import cherry.mastermeister.querybuilder.dto.OrderByItemDto;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateRequest;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateResponse;
import cherry.mastermeister.querybuilder.dto.SelectItemDto;
import cherry.mastermeister.querybuilder.dto.SortDirection;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COMP-16。business-logic-model.md §2〜7。
 */
class QueryBuilderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;
    private static final String SCHEMA = "public";

    private QueryBuilderAccessResolver accessResolver;
    private QueryBuilderService service;

    @BeforeEach
    void setUp() {
        accessResolver = mock(QueryBuilderAccessResolver.class);
        service = new QueryBuilderService(accessResolver);
    }

    private static ColumnRefDto col(String alias, String name) {
        return new ColumnRefDto(alias, name);
    }

    private static SelectItemDto selectCol(String alias, String name) {
        return new SelectItemDto(col(alias, name), null, null);
    }

    private static QueryBuilderStateRequest simpleState(List<SelectItemDto> selectItems,
                                                         List<ConditionDto> whereConditions) {
        return new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"), List.of(), selectItems,
                whereConditions, List.of(), List.of(), List.of(), null, null);
    }

    // ------------------------------------------------------------------
    // generateSql
    // ------------------------------------------------------------------

    @Test
    void generateSql_simpleSelect() {
        String sql = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of()));
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1");
    }

    @Test
    void generateSql_withAlias() {
        SelectItemDto item = new SelectItemDto(col("t1", "id"), null, "itemId");
        String sql = service.generateSql(simpleState(List.of(item), List.of()));
        assertThat(sql).isEqualTo("SELECT t1.id AS itemId FROM items AS t1");
    }

    @Test
    void generateSql_withJoin() {
        JoinClauseDto join = new JoinClauseDto(JoinType.LEFT, SCHEMA, "categories", "t2",
                List.of(new JoinConditionDto(col("t1", "category_id"), col("t2", "id"))));
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(join), List.of(selectCol("t1", "id"), selectCol("t2", "name")), List.of(), List.of(),
                List.of(), List.of(), null, null);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo("SELECT t1.id, t2.name FROM items AS t1 LEFT JOIN categories AS t2 "
                + "ON t1.category_id = t2.id");
    }

    @Test
    void generateSql_withMultipleJoinConditions() {
        JoinClauseDto join = new JoinClauseDto(JoinType.INNER, SCHEMA, "categories", "t2",
                List.of(new JoinConditionDto(col("t1", "category_id"), col("t2", "id")),
                        new JoinConditionDto(col("t1", "tenant_id"), col("t2", "tenant_id"))));
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(join), List.of(selectCol("t1", "id")), List.of(), List.of(), List.of(), List.of(), null,
                null);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 INNER JOIN categories AS t2 "
                + "ON t1.category_id = t2.id AND t1.tenant_id = t2.tenant_id");
    }

    @Test
    void generateSql_withWhereEquals() {
        ConditionDto condition = new ConditionDto(col("t1", "status"), null, ConditionOperator.EQ, "ACTIVE",
                ColumnDataTypeCategory.STRING);
        String sql = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(condition)));
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.status = 'ACTIVE'");
    }

    @Test
    void generateSql_withWhereNumericComparison() {
        ConditionDto condition = new ConditionDto(col("t1", "amount"), null, ConditionOperator.GT, "100",
                ColumnDataTypeCategory.NUMERIC);
        String sql = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(condition)));
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.amount > 100");
    }

    @Test
    void generateSql_withMultipleWhereConditionsAnd() {
        ConditionDto c1 = new ConditionDto(col("t1", "status"), null, ConditionOperator.EQ, "ACTIVE",
                ColumnDataTypeCategory.STRING);
        ConditionDto c2 = new ConditionDto(col("t1", "amount"), null, ConditionOperator.GE, "10",
                ColumnDataTypeCategory.NUMERIC);
        String sql = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(c1, c2)));
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.status = 'ACTIVE' AND t1.amount >= 10");
    }

    @Test
    void generateSql_withIsNullAndIsNotNull() {
        ConditionDto isNull = new ConditionDto(col("t1", "deleted_at"), null, ConditionOperator.IS_NULL, null,
                ColumnDataTypeCategory.DATETIME);
        String sql = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(isNull)));
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.deleted_at IS NULL");

        ConditionDto isNotNull = new ConditionDto(col("t1", "deleted_at"), null, ConditionOperator.IS_NOT_NULL, null,
                ColumnDataTypeCategory.DATETIME);
        String sql2 = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(isNotNull)));
        assertThat(sql2).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.deleted_at IS NOT NULL");
    }

    @Test
    void generateSql_withStartsWithAndContains() {
        ConditionDto startsWith = new ConditionDto(col("t1", "name"), null, ConditionOperator.STARTS_WITH, "foo",
                ColumnDataTypeCategory.STRING);
        String sql = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(startsWith)));
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.name LIKE 'foo%'");

        ConditionDto contains = new ConditionDto(col("t1", "name"), null, ConditionOperator.CONTAINS, "foo",
                ColumnDataTypeCategory.STRING);
        String sql2 = service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(contains)));
        assertThat(sql2).isEqualTo("SELECT t1.id FROM items AS t1 WHERE t1.name LIKE '%foo%'");
    }

    @Test
    void generateSql_withGroupByAndAggregate() {
        AggregateExpressionDto count = new AggregateExpressionDto(AggregateFunction.COUNT, col("t1", "id"), false);
        SelectItemDto countItem = new SelectItemDto(null, count, "cnt");
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(), List.of(selectCol("t1", "category_id"), countItem), List.of(),
                List.of(col("t1", "category_id")), List.of(), List.of(), null, null);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo(
                "SELECT t1.category_id, COUNT(t1.id) AS cnt FROM items AS t1 GROUP BY t1.category_id");
    }

    @Test
    void generateSql_withDistinctAggregate() {
        AggregateExpressionDto countDistinct = new AggregateExpressionDto(AggregateFunction.COUNT, col("t1", "id"),
                true);
        SelectItemDto item = new SelectItemDto(null, countDistinct, null);
        String sql = service.generateSql(simpleState(List.of(item), List.of()));
        assertThat(sql).isEqualTo("SELECT COUNT(DISTINCT t1.id) FROM items AS t1");
    }

    @Test
    void generateSql_withHaving() {
        AggregateExpressionDto count = new AggregateExpressionDto(AggregateFunction.COUNT, col("t1", "id"), false);
        ConditionDto having = new ConditionDto(null, count, ConditionOperator.GT, "5", ColumnDataTypeCategory.NUMERIC);
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(), List.of(selectCol("t1", "category_id"), new SelectItemDto(null, count, "cnt")), List.of(),
                List.of(col("t1", "category_id")), List.of(having), List.of(), null, null);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo("SELECT t1.category_id, COUNT(t1.id) AS cnt FROM items AS t1 "
                + "GROUP BY t1.category_id HAVING COUNT(t1.id) > 5");
    }

    @Test
    void generateSql_withOrderBy() {
        OrderByItemDto orderBy = new OrderByItemDto(col("t1", "id"), null, SortDirection.DESC);
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(), List.of(selectCol("t1", "id")), List.of(), List.of(), List.of(), List.of(orderBy), null,
                null);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 ORDER BY t1.id DESC");
    }

    @Test
    void generateSql_withLimitAndOffset() {
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(), List.of(selectCol("t1", "id")), List.of(), List.of(), List.of(), List.of(), 10, 20);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo("SELECT t1.id FROM items AS t1 LIMIT 10 OFFSET 20");
    }

    @Test
    void generateSql_rejectsGroupByViolation() {
        AggregateExpressionDto count = new AggregateExpressionDto(AggregateFunction.COUNT, col("t1", "id"), false);
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(), List.of(selectCol("t1", "category_id"), new SelectItemDto(null, count, "cnt")), List.of(),
                List.of(), List.of(), List.of(), null, null);
        assertThatThrownBy(() -> service.generateSql(state)).isInstanceOf(QueryBuilderInvalidGroupByException.class);
    }

    @Test
    void generateSql_allowsGroupByWithoutAggregate() {
        // 集計関数を含まない場合はGROUP BY整合性チェックの対象外
        QueryBuilderStateRequest state = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", "t1"),
                List.of(), List.of(selectCol("t1", "category_id")), List.of(), List.of(col("t1", "category_id")),
                List.of(), List.of(), null, null);
        String sql = service.generateSql(state);
        assertThat(sql).isEqualTo("SELECT t1.category_id FROM items AS t1 GROUP BY t1.category_id");
    }

    @Test
    void generateSql_rejectsInvalidNumericLiteral() {
        ConditionDto condition = new ConditionDto(col("t1", "amount"), null, ConditionOperator.GT, "not-a-number",
                ColumnDataTypeCategory.NUMERIC);
        assertThatThrownBy(() -> service.generateSql(simpleState(List.of(selectCol("t1", "id")), List.of(condition))))
                .isInstanceOf(QueryBuilderInvalidLiteralException.class);
    }

    // ------------------------------------------------------------------
    // parseToBuilderState
    // ------------------------------------------------------------------

    private void stubAccessible(String tableName, String columnName, ColumnDataTypeCategory category) {
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, tableName, columnName))
                .thenReturn(true);
        when(accessResolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA)).thenReturn(List.of(
                new AccessibleBuilderTableResponse(tableName, TableType.TABLE,
                        List.of(new AccessibleBuilderColumnResponse(columnName, category)))));
    }

    @Test
    void parseToBuilderState_simpleSelect() {
        stubAccessible("items", "id", ColumnDataTypeCategory.NUMERIC);
        QueryBuilderStateResponse result = service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT t1.id FROM items AS t1");
        assertThat(result.from()).isEqualTo(new FromClauseDto(SCHEMA, "items", "t1"));
        assertThat(result.selectItems()).containsExactly(new SelectItemDto(col("t1", "id"), null, null));
    }

    @Test
    void parseToBuilderState_withJoinAndWhere() {
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "id")).thenReturn(true);
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "category_id"))
                .thenReturn(true);
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "categories", "id")).thenReturn(true);
        when(accessResolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA)).thenReturn(List.of(
                new AccessibleBuilderTableResponse("items", TableType.TABLE, List.of(
                        new AccessibleBuilderColumnResponse("id", ColumnDataTypeCategory.NUMERIC),
                        new AccessibleBuilderColumnResponse("category_id", ColumnDataTypeCategory.NUMERIC))),
                new AccessibleBuilderTableResponse("categories", TableType.TABLE, List.of(
                        new AccessibleBuilderColumnResponse("id", ColumnDataTypeCategory.NUMERIC)))));

        QueryBuilderStateResponse result = service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT t1.id FROM items AS t1 LEFT JOIN categories AS t2 ON t1.category_id = t2.id "
                        + "WHERE t1.id > 10");

        assertThat(result.joins()).hasSize(1);
        assertThat(result.joins().get(0).joinType()).isEqualTo(JoinType.LEFT);
        assertThat(result.whereConditions()).containsExactly(
                new ConditionDto(col("t1", "id"), null, ConditionOperator.GT, "10", ColumnDataTypeCategory.NUMERIC));
    }

    @Test
    void parseToBuilderState_roundTripsGroupByAndAggregate() {
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "id")).thenReturn(true);
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "category_id"))
                .thenReturn(true);
        when(accessResolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA)).thenReturn(List.of(
                new AccessibleBuilderTableResponse("items", TableType.TABLE, List.of(
                        new AccessibleBuilderColumnResponse("id", ColumnDataTypeCategory.NUMERIC),
                        new AccessibleBuilderColumnResponse("category_id", ColumnDataTypeCategory.NUMERIC)))));

        QueryBuilderStateResponse result = service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT t1.category_id, COUNT(t1.id) AS cnt FROM items AS t1 GROUP BY t1.category_id "
                        + "HAVING COUNT(t1.id) > 5 ORDER BY t1.category_id ASC LIMIT 10 OFFSET 5");

        assertThat(result.groupByColumns()).containsExactly(col("t1", "category_id"));
        assertThat(result.havingConditions()).hasSize(1);
        assertThat(result.orderByItems()).hasSize(1);
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.offset()).isEqualTo(5);
    }

    @Test
    void parseToBuilderState_rejectsUnion() {
        assertThatThrownBy(() -> service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT id FROM t1 UNION SELECT id FROM t2"))
                .isInstanceOf(QueryBuilderUnsupportedSqlException.class);
    }

    @Test
    void parseToBuilderState_rejectsSubqueryInFrom() {
        assertThatThrownBy(() -> service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT id FROM (SELECT id FROM items) t1"))
                .isInstanceOf(QueryBuilderUnsupportedSqlException.class);
    }

    @Test
    void parseToBuilderState_rejectsFullJoin() {
        stubAccessible("items", "id", ColumnDataTypeCategory.NUMERIC);
        assertThatThrownBy(() -> service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT t1.id FROM items AS t1 FULL JOIN categories t2 ON t1.category_id = t2.id"))
                .isInstanceOf(QueryBuilderUnsupportedSqlException.class);
    }

    @Test
    void parseToBuilderState_rejectsUnqualifiedColumnReference() {
        stubAccessible("items", "id", ColumnDataTypeCategory.NUMERIC);
        assertThatThrownBy(() -> service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA, "SELECT id FROM items"))
                .isInstanceOf(QueryBuilderUnsupportedSqlException.class);
    }

    @Test
    void parseToBuilderState_rejectsInaccessibleColumnReference() {
        when(accessResolver.isColumnAccessible(USER_ID, CONNECTION_ID, SCHEMA, "items", "id")).thenReturn(false);
        when(accessResolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA)).thenReturn(List.of(
                new AccessibleBuilderTableResponse("items", TableType.TABLE, List.of())));
        assertThatThrownBy(() -> service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT t1.id FROM items AS t1"))
                .isInstanceOf(QueryBuilderReferenceNotAccessibleException.class);
    }

    @Test
    void parseToBuilderState_rejectsMultipleStatements() {
        assertThatThrownBy(() -> service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA,
                "SELECT t1.id FROM items AS t1; DELETE FROM items"))
                .isInstanceOf(QueryBuilderUnsupportedSqlException.class);
    }
}

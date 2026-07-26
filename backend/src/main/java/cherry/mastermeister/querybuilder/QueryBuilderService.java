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
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * COMP-16。business-logic-model.md §2〜7。SQL生成・リバースエンジニアリングの2責務に専念する
 * （アクセス可能テーブル/カラム一覧取得は{@link QueryBuilderAccessResolver}に分離、Q4=A）。
 */
@Service
public class QueryBuilderService {

    private final QueryBuilderAccessResolver accessResolver;

    public QueryBuilderService(QueryBuilderAccessResolver accessResolver) {
        this.accessResolver = accessResolver;
    }

    // ------------------------------------------------------------------
    // SQL生成（FR-5.5、business-logic-model.md §2〜6）
    // ------------------------------------------------------------------

    public String generateSql(QueryBuilderStateRequest state) {
        List<SelectItemDto> selectItems = state.selectItems();
        List<ColumnRefDto> groupByColumns = orEmpty(state.groupByColumns());
        validateGroupBy(selectItems, groupByColumns);

        PlainSelect select = new PlainSelect();
        select.setFromItem(buildTable(state.from()));

        List<JoinClauseDto> joinDtos = orEmpty(state.joins());
        if (!joinDtos.isEmpty()) {
            List<Join> joins = new ArrayList<>();
            for (JoinClauseDto joinDto : joinDtos) {
                joins.add(buildJoin(joinDto));
            }
            select.setJoins(joins);
        }

        List<SelectItem<?>> items = new ArrayList<>();
        for (SelectItemDto item : selectItems) {
            items.add(buildSelectItem(item));
        }
        select.setSelectItems(items);

        List<ConditionDto> whereConditions = orEmpty(state.whereConditions());
        if (!whereConditions.isEmpty()) {
            select.setWhere(combineConditions(whereConditions));
        }

        if (!groupByColumns.isEmpty()) {
            List<Expression> groupByExpressions = new ArrayList<>();
            for (ColumnRefDto ref : groupByColumns) {
                groupByExpressions.add(buildColumn(ref));
            }
            GroupByElement groupByElement = new GroupByElement();
            groupByElement.setGroupByExpressions(new ExpressionList<>(groupByExpressions));
            select.setGroupByElement(groupByElement);
        }

        List<ConditionDto> havingConditions = orEmpty(state.havingConditions());
        if (!havingConditions.isEmpty()) {
            select.setHaving(combineConditions(havingConditions));
        }

        List<OrderByItemDto> orderByItems = orEmpty(state.orderByItems());
        if (!orderByItems.isEmpty()) {
            List<OrderByElement> orderByElements = new ArrayList<>();
            for (OrderByItemDto item : orderByItems) {
                orderByElements.add(buildOrderByElement(item));
            }
            select.setOrderByElements(orderByElements);
        }

        if (state.limit() != null) {
            Limit limit = new Limit();
            limit.setRowCount(new LongValue(state.limit()));
            select.setLimit(limit);
        }
        if (state.offset() != null) {
            Offset offset = new Offset();
            offset.setOffset(new LongValue(state.offset()));
            select.setOffset(offset);
        }

        return select.toString();
    }

    /**
     * BR-QUERYBUILDER-11。SELECT句に集計関数を含む場合、GROUP BYに含まれない非集計列が
     * SELECT句に存在してはならない。
     */
    private void validateGroupBy(List<SelectItemDto> selectItems, List<ColumnRefDto> groupByColumns) {
        boolean hasAggregate = selectItems.stream().anyMatch(item -> item.aggregate() != null);
        if (!hasAggregate) {
            return;
        }
        for (SelectItemDto item : selectItems) {
            if (item.column() != null && !groupByColumns.contains(item.column())) {
                throw new QueryBuilderInvalidGroupByException();
            }
        }
    }

    private Table buildTable(FromClauseDto from) {
        Table table = new Table(from.tableName());
        table.setAlias(new Alias(from.alias()));
        return table;
    }

    private Join buildJoin(JoinClauseDto joinDto) {
        Join join = new Join();
        switch (joinDto.joinType()) {
            case INNER -> join.setInner(true);
            case LEFT -> join.setLeft(true);
            case RIGHT -> join.setRight(true);
        }
        Table rightTable = new Table(joinDto.tableName());
        rightTable.setAlias(new Alias(joinDto.alias()));
        join.setRightItem(rightTable);

        Expression onExpression = null;
        for (JoinConditionDto condition : joinDto.onConditions()) {
            Expression eq = new EqualsTo(buildColumn(condition.leftColumn()), buildColumn(condition.rightColumn()));
            onExpression = onExpression == null ? eq : new AndExpression(onExpression, eq);
        }
        join.addOnExpression(onExpression);
        return join;
    }

    private SelectItem<?> buildSelectItem(SelectItemDto item) {
        Expression expression = item.column() != null ? buildColumn(item.column()) : buildAggregate(item.aggregate());
        return item.alias() != null && !item.alias().isBlank()
                ? SelectItem.from(expression, new Alias(item.alias()))
                : SelectItem.from(expression);
    }

    private Column buildColumn(ColumnRefDto ref) {
        return new Column(new Table(ref.tableAlias()), ref.columnName());
    }

    private Function buildAggregate(AggregateExpressionDto aggregate) {
        Function function = new Function();
        function.setName(aggregate.function().name());
        function.setDistinct(aggregate.distinct());
        function.setParameters(new ExpressionList<>(List.of((Expression) buildColumn(aggregate.column()))));
        return function;
    }

    /**
     * WHERE/HAVING共通。フラットな条件リストをANDで連結した単一の{@link Expression}にまとめる
     * （BR-QUERYBUILDER-04）。
     */
    private Expression combineConditions(List<ConditionDto> conditions) {
        Expression combined = null;
        for (ConditionDto condition : conditions) {
            Expression current = buildCondition(condition);
            combined = combined == null ? current : new AndExpression(combined, current);
        }
        return combined;
    }

    private Expression buildCondition(ConditionDto condition) {
        Expression left = condition.column() != null
                ? buildColumn(condition.column())
                : buildAggregate(condition.aggregate());
        return switch (condition.operator()) {
            case IS_NULL -> new IsNullExpression().withLeftExpression(left);
            case IS_NOT_NULL -> new IsNullExpression().withLeftExpression(left).withNot(true);
            case EQ -> new EqualsTo(left, buildLiteral(condition));
            case NE -> new NotEqualsTo(left, buildLiteral(condition));
            case GT -> new GreaterThan(left, buildLiteral(condition));
            case GE -> new GreaterThanEquals(left, buildLiteral(condition));
            case LT -> new MinorThan(left, buildLiteral(condition));
            case LE -> new MinorThanEquals(left, buildLiteral(condition));
            case STARTS_WITH -> buildLike(left, condition.value() + "%");
            case CONTAINS -> buildLike(left, "%" + condition.value() + "%");
        };
    }

    private LikeExpression buildLike(Expression left, String pattern) {
        LikeExpression like = new LikeExpression();
        like.setLeftExpression(left);
        like.setRightExpression(new StringValue(pattern));
        return like;
    }

    /**
     * tech-stack-decisions.md §2、nfr-design-patterns.md §2.1。列のデータ型分類に応じた
     * 型安全なリテラルオブジェクトを構築する。文字列連結によるエスケープ漏れを構造的に防止する。
     * <p>
     * **実装時の発見**: JSqlParserの{@code LongValue(String)}・{@code BooleanValue(String)}は
     * 実際には値を検証しない（{@code LongValue}は文字列をそのまま保持し{@code getValue()}呼び出し時に
     * 遅延パースするのみ、{@code BooleanValue}は{@code Boolean.parseBoolean}を使うため不正な文字列も
     * 無条件に{@code false}になる）。型安全なリテラル変換を実現するには、コンストラクタ呼び出し前に
     * 明示的な検証が必要。また{@code DateValue}の{@code toString()}は{@code {d '...'}}というJDBC
     * escape構文でレンダリングされ、対象4方言へ直接実行する際の移植性に懸念があるため採用せず、
     * 単純な文字列リテラル（{@code '2026-01-01'}）として埋め込む方式に変更した（対象4方言はいずれも
     * 文字列リテラルから日時型への暗黙変換を受け付けるため問題ない）。
     */
    private Expression buildLiteral(ConditionDto condition) {
        String value = condition.value();
        try {
            return switch (condition.dataTypeCategory()) {
                case NUMERIC -> buildNumericLiteral(value);
                case DATETIME -> buildDateTimeLiteral(value);
                case BOOLEAN -> buildBooleanLiteral(value);
                case STRING -> new StringValue(value);
            };
        } catch (RuntimeException e) {
            throw new QueryBuilderInvalidLiteralException();
        }
    }

    private Expression buildNumericLiteral(String value) {
        if (value.contains(".")) {
            return new DoubleValue(value);
        }
        Long.parseLong(value);
        return new LongValue(value);
    }

    private Expression buildBooleanLiteral(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("not a boolean literal: " + value);
        }
        return new BooleanValue(Boolean.parseBoolean(value));
    }

    private Expression buildDateTimeLiteral(String value) {
        if (value.contains("T") || value.contains(":")) {
            java.time.LocalDateTime.parse(value.replace(' ', 'T'));
            return new StringValue(value.replace('T', ' '));
        }
        java.time.LocalDate.parse(value);
        return new StringValue(value);
    }

    private OrderByElement buildOrderByElement(OrderByItemDto item) {
        OrderByElement element = new OrderByElement();
        Expression expression = item.column() != null ? buildColumn(item.column()) : buildAggregate(item.aggregate());
        element.setExpression(expression);
        element.setAscDescPresent(true);
        element.setAsc(item.direction() == SortDirection.ASC);
        return element;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ------------------------------------------------------------------
    // リバースエンジニアリング（FR-5.7、business-logic-model.md §7）
    // ------------------------------------------------------------------

    /**
     * ユーザ・対象接続・対象スキーマ・エイリアス→実テーブル名マッピングを1つにまとめ、私用メソッド間の
     * 受け渡しを簡潔にする（実装時の判断）。{@code aliasToTable}は、列参照（例: {@code t1.id}）の
     * {@code t1}のようなSQL上のエイリアスを実効権限チェック用の実テーブル名へ解決するために必須
     * （実装中に発見した不具合: 当初はエイリアス文字列をそのまま実テーブル名として権限チェックに
     * 渡してしまっていた）。
     */
    private record ParseCtx(Long userId, Long connectionId, String schemaName, java.util.Map<String, String> aliasToTable) {

        String resolveTableName(String alias) {
            return aliasToTable.getOrDefault(alias, alias);
        }
    }

    public QueryBuilderStateResponse parseToBuilderState(Long userId, Long connectionId, String schemaName,
                                                          String sql) {
        PlainSelect select = parsePlainSelect(sql);

        FromClauseDto from = parseFromWithoutCtx(select.getFromItem(), schemaName);
        List<JoinClauseDto> joins = new ArrayList<>();
        for (Join join : orEmptyJoins(select.getJoins())) {
            joins.add(parseJoinWithoutCtx(join, schemaName));
        }

        java.util.Map<String, String> aliasToTable = new java.util.HashMap<>();
        aliasToTable.put(from.alias(), from.tableName());
        for (JoinClauseDto joinDto : joins) {
            aliasToTable.put(joinDto.alias(), joinDto.tableName());
        }
        ParseCtx ctx = new ParseCtx(userId, connectionId, schemaName, aliasToTable);

        checkAccessible(ctx, from.tableName(), null);
        for (JoinClauseDto joinDto : joins) {
            checkAccessible(ctx, joinDto.tableName(), null);
        }

        List<SelectItemDto> selectItems = new ArrayList<>();
        for (SelectItem<?> item : select.getSelectItems()) {
            selectItems.add(parseSelectItem(item, ctx));
        }

        List<ConditionDto> whereConditions = parseConditions(select.getWhere(), ctx);
        List<ColumnRefDto> groupByColumns = parseGroupBy(select.getGroupBy(), ctx);
        List<ConditionDto> havingConditions = parseConditions(select.getHaving(), ctx);
        List<OrderByItemDto> orderByItems = parseOrderBy(select.getOrderByElements(), ctx);

        Integer limit = parseLimit(select.getLimit());
        Integer offset = parseOffset(select.getOffset());

        return new QueryBuilderStateResponse(from, joins, selectItems, whereConditions, groupByColumns,
                havingConditions, orderByItems, limit, offset);
    }

    private PlainSelect parsePlainSelect(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements != null && statements.size() == 1 && statements.get(0) instanceof PlainSelect plainSelect) {
                return plainSelect;
            }
        } catch (JSQLParserException e) {
            throw new QueryBuilderUnsupportedSqlException();
        }
        throw new QueryBuilderUnsupportedSqlException();
    }

    private FromClauseDto parseFromWithoutCtx(FromItem fromItem, String schemaName) {
        if (!(fromItem instanceof Table table)) {
            throw new QueryBuilderUnsupportedSqlException();
        }
        return new FromClauseDto(schemaName, table.getName(),
                aliasOrTableName(table.getAlias(), table.getName()));
    }

    private JoinClauseDto parseJoinWithoutCtx(Join join, String schemaName) {
        JoinType joinType;
        if (join.isInner() && !join.isLeft() && !join.isRight()) {
            joinType = JoinType.INNER;
        } else if (join.isLeft() && !join.isRight()) {
            joinType = JoinType.LEFT;
        } else if (join.isRight() && !join.isLeft()) {
            joinType = JoinType.RIGHT;
        } else {
            // FULL/CROSS/NATURAL/OUTER単独/USING等はサポート対象外（BR-QUERYBUILDER-02・07）
            throw new QueryBuilderUnsupportedSqlException();
        }
        if (!(join.getRightItem() instanceof Table table)) {
            throw new QueryBuilderUnsupportedSqlException();
        }
        if (join.getOnExpressions().size() != 1) {
            throw new QueryBuilderUnsupportedSqlException();
        }
        List<JoinConditionDto> conditions = new ArrayList<>();
        flattenAnd(join.getOnExpressions().iterator().next()).forEach(expr -> {
            if (!(expr instanceof EqualsTo equalsTo)
                    || !(equalsTo.getLeftExpression() instanceof Column left)
                    || !(equalsTo.getRightExpression() instanceof Column right)) {
                throw new QueryBuilderUnsupportedSqlException();
            }
            conditions.add(new JoinConditionDto(toColumnRef(left), toColumnRef(right)));
        });
        return new JoinClauseDto(joinType, schemaName, table.getName(),
                aliasOrTableName(table.getAlias(), table.getName()), conditions);
    }

    private SelectItemDto parseSelectItem(SelectItem<?> item, ParseCtx ctx) {
        Expression expression = item.getExpression();
        String alias = item.getAlias() != null ? item.getAlias().getName() : null;
        if (expression instanceof Column column) {
            checkAccessible(ctx, ctx.resolveTableName(requireTableAlias(column)), column.getColumnName());
            return new SelectItemDto(toColumnRef(column), null, alias);
        }
        if (expression instanceof Function function) {
            return new SelectItemDto(null, toAggregate(function, ctx), alias);
        }
        throw new QueryBuilderUnsupportedSqlException();
    }

    private List<ConditionDto> parseConditions(Expression expression, ParseCtx ctx) {
        if (expression == null) {
            return List.of();
        }
        List<ConditionDto> conditions = new ArrayList<>();
        for (Expression leaf : flattenAnd(expression)) {
            conditions.add(parseCondition(leaf, ctx));
        }
        return conditions;
    }

    /**
     * WHERE句は常に単純な列参照が左辺だが、HAVING句は集計関数適用の結果
     * （例: {@code COUNT(t1.id) > 5}）が左辺になりうる（ConditionDto.column/aggregateの排他設計）。
     */
    private record LeftOperand(ColumnRefDto column, AggregateExpressionDto aggregate,
                                ColumnDataTypeCategory dataTypeCategory) {
    }

    private ConditionDto parseCondition(Expression expression, ParseCtx ctx) {
        if (expression instanceof IsNullExpression isNull) {
            LeftOperand operand = parseLeftOperand(isNull.getLeftExpression(), ctx);
            ConditionOperator operator = isNull.isNot() ? ConditionOperator.IS_NOT_NULL : ConditionOperator.IS_NULL;
            return new ConditionDto(operand.column(), operand.aggregate(), operator, null,
                    operand.dataTypeCategory());
        }
        if (expression instanceof LikeExpression like) {
            LeftOperand operand = parseLeftOperand(like.getLeftExpression(), ctx);
            if (!(like.getRightExpression() instanceof StringValue pattern)) {
                throw new QueryBuilderUnsupportedSqlException();
            }
            String raw = pattern.getValue();
            if (raw.startsWith("%") && raw.endsWith("%") && raw.length() >= 2) {
                return new ConditionDto(operand.column(), operand.aggregate(), ConditionOperator.CONTAINS,
                        raw.substring(1, raw.length() - 1), operand.dataTypeCategory());
            }
            if (raw.endsWith("%")) {
                return new ConditionDto(operand.column(), operand.aggregate(), ConditionOperator.STARTS_WITH,
                        raw.substring(0, raw.length() - 1), operand.dataTypeCategory());
            }
            throw new QueryBuilderUnsupportedSqlException();
        }
        ConditionOperator operator = switch (expression) {
            case EqualsTo ignored -> ConditionOperator.EQ;
            case NotEqualsTo ignored -> ConditionOperator.NE;
            case GreaterThan ignored -> ConditionOperator.GT;
            case GreaterThanEquals ignored -> ConditionOperator.GE;
            case MinorThan ignored -> ConditionOperator.LT;
            case MinorThanEquals ignored -> ConditionOperator.LE;
            default -> throw new QueryBuilderUnsupportedSqlException();
        };
        var comparison = (net.sf.jsqlparser.expression.operators.relational.ComparisonOperator) expression;
        LeftOperand operand = parseLeftOperand(comparison.getLeftExpression(), ctx);
        String value = literalToString(comparison.getRightExpression());
        return new ConditionDto(operand.column(), operand.aggregate(), operator, value, operand.dataTypeCategory());
    }

    private LeftOperand parseLeftOperand(Expression expression, ParseCtx ctx) {
        if (expression instanceof Column column) {
            ColumnRefDto ref = requireColumnRef(column, ctx);
            return new LeftOperand(ref, null, resolveCategory(ctx, ref));
        }
        if (expression instanceof Function function) {
            AggregateExpressionDto aggregate = toAggregate(function, ctx);
            // COUNTは対象列の型に関わらず常に整数を返すため、対象列の型ではなくNUMERICで固定する
            ColumnDataTypeCategory category = aggregate.function() == AggregateFunction.COUNT
                    ? ColumnDataTypeCategory.NUMERIC
                    : resolveCategory(ctx, aggregate.column());
            return new LeftOperand(null, aggregate, category);
        }
        throw new QueryBuilderUnsupportedSqlException();
    }

    private ColumnRefDto requireColumnRef(Column column, ParseCtx ctx) {
        checkAccessible(ctx, ctx.resolveTableName(requireTableAlias(column)), column.getColumnName());
        return toColumnRef(column);
    }

    private String literalToString(Expression expression) {
        return switch (expression) {
            case LongValue v -> v.getStringValue();
            case DoubleValue v -> String.valueOf(v.getValue());
            case StringValue v -> v.getValue();
            case DateValue v -> v.getValue().toString();
            case TimestampValue v -> v.getValue().toString();
            case BooleanValue v -> String.valueOf(v.getValue());
            default -> throw new QueryBuilderUnsupportedSqlException();
        };
    }

    private List<ColumnRefDto> parseGroupBy(GroupByElement groupBy, ParseCtx ctx) {
        if (groupBy == null || groupBy.getGroupByExpressionList() == null) {
            return List.of();
        }
        List<ColumnRefDto> result = new ArrayList<>();
        for (Object expr : groupBy.getGroupByExpressionList()) {
            if (!(expr instanceof Column column)) {
                throw new QueryBuilderUnsupportedSqlException();
            }
            checkAccessible(ctx, ctx.resolveTableName(requireTableAlias(column)), column.getColumnName());
            result.add(toColumnRef(column));
        }
        return result;
    }

    private List<OrderByItemDto> parseOrderBy(List<OrderByElement> orderByElements, ParseCtx ctx) {
        if (orderByElements == null) {
            return List.of();
        }
        List<OrderByItemDto> result = new ArrayList<>();
        for (OrderByElement element : orderByElements) {
            SortDirection direction = element.isAscDescPresent() && !element.isAsc()
                    ? SortDirection.DESC : SortDirection.ASC;
            Expression expression = element.getExpression();
            if (expression instanceof Column column) {
                checkAccessible(ctx, ctx.resolveTableName(requireTableAlias(column)), column.getColumnName());
                result.add(new OrderByItemDto(toColumnRef(column), null, direction));
            } else if (expression instanceof Function function) {
                result.add(new OrderByItemDto(null, toAggregate(function, ctx), direction));
            } else {
                throw new QueryBuilderUnsupportedSqlException();
            }
        }
        return result;
    }

    private Integer parseLimit(Limit limit) {
        if (limit == null || !(limit.getRowCount() instanceof LongValue longValue)) {
            return null;
        }
        return (int) longValue.getValue();
    }

    private Integer parseOffset(Offset offset) {
        if (offset == null || !(offset.getOffset() instanceof LongValue longValue)) {
            return null;
        }
        return (int) longValue.getValue();
    }

    private AggregateExpressionDto toAggregate(Function function, ParseCtx ctx) {
        AggregateFunction aggregateFunction;
        try {
            aggregateFunction = AggregateFunction.valueOf(function.getName().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new QueryBuilderUnsupportedSqlException();
        }
        if (function.getParameters() == null || function.getParameters().size() != 1
                || !(function.getParameters().get(0) instanceof Column column)) {
            throw new QueryBuilderUnsupportedSqlException();
        }
        checkAccessible(ctx, ctx.resolveTableName(requireTableAlias(column)), column.getColumnName());
        return new AggregateExpressionDto(aggregateFunction, toColumnRef(column), function.isDistinct());
    }

    private ColumnRefDto toColumnRef(Column column) {
        return new ColumnRefDto(requireTableAlias(column), column.getColumnName());
    }

    private String requireTableAlias(Column column) {
        Table table = column.getTable();
        if (table == null || table.getName() == null) {
            // テーブルエイリアス修飾のない列参照はJOIN併用時に曖昧となりうるため非対応とする
            // （BR-QUERYBUILDER-06、常にエイリアス修飾を前提とする設計と整合）
            throw new QueryBuilderUnsupportedSqlException();
        }
        return table.getName();
    }

    private String aliasOrTableName(Alias alias, String tableName) {
        return alias != null ? alias.getName() : tableName;
    }

    /**
     * {@code columnName}がnullの場合はFROM/JOINタブのテーブル自体の存在・アクセス可否
     * （{@link QueryBuilderAccessResolver#listAccessibleTables}にテーブル名が含まれるか）を確認する。
     * 非nullの場合は列単位の存在・アクセス可否（{@code isColumnAccessible}）を確認する。
     */
    private void checkAccessible(ParseCtx ctx, String tableName, String columnName) {
        if (columnName == null) {
            boolean tableAccessible = accessResolver.listAccessibleTables(ctx.userId(), ctx.connectionId(),
                            ctx.schemaName()).stream()
                    .anyMatch(table -> table.tableName().equals(tableName));
            if (!tableAccessible) {
                throw new QueryBuilderReferenceNotAccessibleException();
            }
            return;
        }
        if (!accessResolver.isColumnAccessible(ctx.userId(), ctx.connectionId(), ctx.schemaName(), tableName,
                columnName)) {
            throw new QueryBuilderReferenceNotAccessibleException();
        }
    }

    private ColumnDataTypeCategory resolveCategory(ParseCtx ctx, ColumnRefDto ref) {
        String tableName = ctx.resolveTableName(ref.tableAlias());
        return accessResolver.listAccessibleTables(ctx.userId(), ctx.connectionId(), ctx.schemaName()).stream()
                .filter(table -> table.tableName().equals(tableName))
                .flatMap(table -> table.columns().stream())
                .filter(column -> column.columnName().equals(ref.columnName()))
                .findFirst()
                .map(AccessibleBuilderColumnResponse::dataTypeCategory)
                .orElseThrow(QueryBuilderReferenceNotAccessibleException::new);
    }

    private List<Expression> flattenAnd(Expression expression) {
        List<Expression> result = new ArrayList<>();
        if (expression instanceof AndExpression and) {
            result.addAll(flattenAnd(and.getLeftExpression()));
            result.addAll(flattenAnd(and.getRightExpression()));
        } else {
            result.add(expression);
        }
        return result;
    }

    private List<Join> orEmptyJoins(List<Join> joins) {
        return joins == null ? List.of() : joins;
    }
}

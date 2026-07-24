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
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * business-rules.md BR-MASTER-04。nfr-design-patterns.md §3.1。
 * SQL手入力のWHERE/ORDER BY句を、ダミーSELECT文への埋め込み経由でJSqlParserによって構文解析し、
 * 許可された構文要素（比較演算子、AND/OR、カラム参照、リテラル値。ORDER BYはカラム参照＋ASC/DESCのみ）
 * のみで構成されているかを検証する。検証を通過した構文木は、対象RDBMS方言のクオート規約で
 * 識別子をエスケープし、リテラル値はバインドパラメータとして再構築する
 * （文字列としての直接連結は行わない）。
 * <p>
 * 実装判断: 完全な{@code ExpressionVisitor}インタフェース（数十個のvisitメソッド）を実装する代わりに、
 * Java 25のパターンマッチングswitchで許可構文要素を列挙し、それ以外はdefault節で一律拒否する
 * （フェイルクローズなアローリスト方式、設計意図は同一）。
 * <p>
 * FR-4.4により、WHERE/ORDER BYが参照するカラムは実効権限に関係なく利用可能なため
 * （結果に含まれる値の絞り込みはBR-MASTER-14が別途担う）、本クラスは表示可否を判定しない。
 */
@Component
public class RawQueryConditionValidator {

    private static final String DUMMY_TABLE = "dummy_table";

    public record Validated(String whereSql, Map<String, Object> whereParams, String orderBySql) {

        public static final Validated EMPTY = new Validated(null, Map.of(), null);
    }

    /**
     * @param dialect    識別子のクオート規約解決用（対象RDBMSの方言）
     * @param whereClause  手入力のWHERE句（nullまたは空白のみの場合は条件なしとして扱う）
     * @param orderByClause 手入力のORDER BY句（同上）
     */
    public Validated validate(RdbmsDialectStrategy dialect, String whereClause, String orderByClause) {
        boolean hasWhere = whereClause != null && !whereClause.isBlank();
        boolean hasOrderBy = orderByClause != null && !orderByClause.isBlank();
        if (!hasWhere && !hasOrderBy) {
            return Validated.EMPTY;
        }
        // BR-MASTER-04 Step 2: コメント記号・複数ステートメント区切りは、JSqlParserによる再構築後の
        // SQLには含まれえない（構文木から再構築するため）が、要件どおり明示的に入力全体を拒否する
        rejectDangerousMarkers(whereClause);
        rejectDangerousMarkers(orderByClause);

        StringBuilder dummySql = new StringBuilder("SELECT * FROM ").append(DUMMY_TABLE);
        if (hasWhere) {
            dummySql.append(" WHERE ").append(whereClause);
        }
        if (hasOrderBy) {
            dummySql.append(" ORDER BY ").append(orderByClause);
        }
        PlainSelect plainSelect = parse(dummySql.toString());

        String whereSql = null;
        Map<String, Object> params = new LinkedHashMap<>();
        if (hasWhere) {
            Expression where = plainSelect.getWhere();
            if (where == null) {
                throw new InvalidQueryConditionException();
            }
            StringBuilder buf = new StringBuilder();
            render(where, dialect, buf, params);
            whereSql = buf.toString();
        }

        String orderBySql = null;
        if (hasOrderBy) {
            List<OrderByElement> elements = plainSelect.getOrderByElements();
            if (elements == null || elements.isEmpty()) {
                throw new InvalidQueryConditionException();
            }
            orderBySql = renderOrderBy(elements, dialect);
        }
        return new Validated(whereSql, params, orderBySql);
    }

    private void rejectDangerousMarkers(String clause) {
        if (clause == null) {
            return;
        }
        if (clause.contains("--") || clause.contains("/*") || clause.contains("*/") || clause.contains(";")) {
            throw new InvalidQueryConditionException();
        }
    }

    private PlainSelect parse(String sql) {
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new InvalidQueryConditionException();
        }
        if (!(statement instanceof PlainSelect plainSelect)) {
            throw new InvalidQueryConditionException();
        }
        return plainSelect;
    }

    private void render(Expression expr, RdbmsDialectStrategy dialect, StringBuilder out,
                         Map<String, Object> params) {
        switch (expr) {
            case AndExpression e -> renderLogical(e.getLeftExpression(), e.getRightExpression(), "AND", dialect,
                    out, params);
            case OrExpression e -> renderLogical(e.getLeftExpression(), e.getRightExpression(), "OR", dialect,
                    out, params);
            case EqualsTo e -> renderComparison(e, "=", dialect, out, params);
            case NotEqualsTo e -> renderComparison(e, "<>", dialect, out, params);
            case GreaterThan e -> renderComparison(e, ">", dialect, out, params);
            case GreaterThanEquals e -> renderComparison(e, ">=", dialect, out, params);
            case MinorThan e -> renderComparison(e, "<", dialect, out, params);
            case MinorThanEquals e -> renderComparison(e, "<=", dialect, out, params);
            case ParenthesedExpressionList<?> e -> {
                // "(A OR B)"のような単一式の括弧グループ化は、JSqlParser 5.xでは
                // Parenthesis（非推奨）ではなくParenthesedExpressionList（要素数1）として表現される
                if (e.size() != 1) {
                    throw new InvalidQueryConditionException();
                }
                out.append("(");
                render(e.get(0), dialect, out, params);
                out.append(")");
            }
            default -> throw new InvalidQueryConditionException();
        }
    }

    private void renderLogical(Expression left, Expression right, String op, RdbmsDialectStrategy dialect,
                                StringBuilder out, Map<String, Object> params) {
        out.append("(");
        render(left, dialect, out, params);
        out.append(" ").append(op).append(" ");
        render(right, dialect, out, params);
        out.append(")");
    }

    private void renderComparison(ComparisonOperator e, String op, RdbmsDialectStrategy dialect, StringBuilder out,
                                   Map<String, Object> params) {
        if (!(e.getLeftExpression() instanceof Column column)) {
            throw new InvalidQueryConditionException();
        }
        Object value = literalValue(e.getRightExpression());
        String paramName = "rawParam" + params.size();
        out.append(dialect.quoteIdentifier(column.getColumnName())).append(" ").append(op).append(" :")
                .append(paramName);
        params.put(paramName, value);
    }

    private Object literalValue(Expression expr) {
        return switch (expr) {
            case LongValue v -> v.getValue();
            case DoubleValue v -> v.getValue();
            case StringValue v -> v.getValue();
            case DateValue v -> v.getValue();
            case TimestampValue v -> v.getValue();
            case NullValue v -> null;
            case SignedExpression v -> signedNumericValue(v);
            default -> throw new InvalidQueryConditionException();
        };
    }

    private Object signedNumericValue(SignedExpression signed) {
        boolean negative = signed.getSign() == '-';
        return switch (signed.getExpression()) {
            case LongValue v -> negative ? -v.getValue() : v.getValue();
            case DoubleValue v -> negative ? -v.getValue() : v.getValue();
            default -> throw new InvalidQueryConditionException();
        };
    }

    private String renderOrderBy(List<OrderByElement> elements, RdbmsDialectStrategy dialect) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            OrderByElement element = elements.get(i);
            if (!(element.getExpression() instanceof Column column)) {
                throw new InvalidQueryConditionException();
            }
            if (i > 0) {
                out.append(", ");
            }
            out.append(dialect.quoteIdentifier(column.getColumnName())).append(element.isAsc() ? " ASC" : " DESC");
        }
        return out.toString();
    }
}

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

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.List;

/**
 * business-logic-model.md §1〜2。BR-QUERY-01。nfr-design-patterns.md §3.1。
 * SQL文字列を1回JSqlParserで構文解析し、その解析結果を保持したうえで、読み取り専用検証と
 * パラメータ検出（{@code :name}形式、{@link JdbcNamedParameter}）の両方を提供する。
 * <p>
 * 単純に{@link CCJSqlParserUtil#parse(String)}だけでは"SELECT 1; DELETE FROM x"のような
 * 複数ステートメントの先頭のみが解析され後続部分が無視されてしまう（トレーリング文字列の
 * 検証がない）ため、{@link CCJSqlParserUtil#parseStatements(String)}でステートメント数が
 * 1件であることまで確認する。
 * <p>
 * パラメータ検出は、JSqlParserが公式に提供する専用のパラメータ収集ユーティリティが
 * 存在しないため、SELECT文全体（JOIN・サブクエリ・WHERE/HAVING等すべての句）を漏れなく
 * 走査する{@link TablesNamesFinder}を流用し、{@link JdbcNamedParameter}訪問時のみ追加の
 * 収集を行うサブクラスとして実装する（文字列リテラル内の{@code :}を誤検出しない、AST走査による方式）。
 */
public class QuerySqlAnalyzer {

    private final Statement statement;
    private final boolean readOnly;

    public QuerySqlAnalyzer(String sql) {
        Statement parsed = null;
        boolean ok = false;
        if (sql != null && !sql.isBlank()) {
            try {
                Statements statements = CCJSqlParserUtil.parseStatements(sql);
                if (statements != null && statements.size() == 1 && statements.get(0) instanceof Select) {
                    parsed = statements.get(0);
                    ok = true;
                }
            } catch (JSQLParserException e) {
                ok = false;
            }
        }
        this.statement = parsed;
        this.readOnly = ok;
    }

    /**
     * BR-QUERY-01: パース結果が単一のSelect文であることのみを検証する。式レベルでの
     * 許可リスト検証（UNIT-05のWHERE/ORDER BY句構文検証とは異なる）は行わない。
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * §2: SQL文字列から{@code :name}形式のパラメータ名を検出する。読み取り専用でない場合は空リスト。
     */
    public List<String> detectParameters() {
        if (!readOnly) {
            return List.of();
        }
        ParameterCollector collector = new ParameterCollector();
        collector.getTables(statement);
        return collector.parameterNames;
    }

    private static final class ParameterCollector extends TablesNamesFinder<Void> {

        private final List<String> parameterNames = new ArrayList<>();

        @Override
        public <S> Void visit(JdbcNamedParameter jdbcNamedParameter, S context) {
            parameterNames.add(jdbcNamedParameter.getName());
            return super.visit(jdbcNamedParameter, context);
        }
    }
}

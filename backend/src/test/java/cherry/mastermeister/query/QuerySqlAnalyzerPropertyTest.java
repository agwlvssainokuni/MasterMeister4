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

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * business-logic-model.md §9（PBT対象）。BR-QUERY-01（読み取り専用検証）・§2（パラメータ検出）。
 */
class QuerySqlAnalyzerPropertyTest {

    @Provide
    Arbitrary<String> tableName() {
        return Arbitraries.of("t", "orders", "customers", "items");
    }

    @Provide
    Arbitrary<String> columnName() {
        return Arbitraries.of("a", "amount", "status", "created_at");
    }

    @Provide
    Arbitrary<Long> numericLiteral() {
        return Arbitraries.longs().between(-100_000L, 100_000L);
    }

    @Provide
    Arbitrary<String> paramName() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(8);
    }

    /**
     * 安全性の不変条件: 任意に生成した単一SELECT文（JOIN・サブクエリ・UNIONを含む）は常に受理される。
     */
    @Property
    boolean anyGeneratedSelectStatement_isAlwaysAcceptedAsReadOnly(
            @ForAll("tableName") String table, @ForAll("columnName") String column,
            @ForAll("numericLiteral") Long value) {
        String sql = "SELECT " + column + " FROM " + table + " WHERE " + column + " = " + value;
        return new QuerySqlAnalyzer(sql).isReadOnly();
    }

    @Provide
    Arbitrary<String> nonSelectStatement() {
        Arbitrary<String> table = tableName();
        Arbitrary<String> column = columnName();
        Arbitrary<Long> value = numericLiteral();
        Arbitrary<String> insert = Combinators.combine(table, column, value)
                .as((t, c, v) -> "INSERT INTO " + t + " (" + c + ") VALUES (" + v + ")");
        Arbitrary<String> update = Combinators.combine(table, column, value)
                .as((t, c, v) -> "UPDATE " + t + " SET " + c + " = " + v);
        Arbitrary<String> delete = Combinators.combine(table, column, value)
                .as((t, c, v) -> "DELETE FROM " + t + " WHERE " + c + " = " + v);
        Arbitrary<String> multiStatement = Combinators.combine(table, column, value)
                .as((t, c, v) -> "SELECT * FROM " + t + "; DELETE FROM " + t + " WHERE " + c + " = " + v);
        return Arbitraries.oneOf(insert, update, delete, multiStatement);
    }

    /**
     * 拒否の健全性: 任意に生成した非SELECT文（INSERT/UPDATE/DELETE）・複数ステートメントは常に拒否される。
     */
    @Property
    boolean anyGeneratedNonSelectOrMultiStatement_isAlwaysRejected(@ForAll("nonSelectStatement") String sql) {
        return !new QuerySqlAnalyzer(sql).isReadOnly();
    }

    @Provide
    Arbitrary<List<String>> distinctParamNames() {
        return paramName().list().ofMinSize(1).ofMaxSize(5)
                .map(names -> names.stream().distinct().collect(Collectors.toList()))
                .filter(names -> !names.isEmpty());
    }

    /**
     * パラメータ検出の健全性: SQL文中に含まれる任意個数の:paramトークン
     * （文字列リテラル内のものを除く）が過不足なく検出される。
     */
    @Property
    boolean detectedParameters_matchExactlyTheEmbeddedParamTokens(@ForAll("distinctParamNames") List<String> names) {
        String whereClause = IntStream.range(0, names.size())
                .mapToObj(i -> "c" + i + " = :" + names.get(i))
                .collect(Collectors.joining(" AND "));
        String sql = "SELECT * FROM t WHERE " + whereClause;

        return new QuerySqlAnalyzer(sql).detectParameters().equals(names);
    }

    /**
     * 文字列リテラル内の:は誤検出しない（文字列リテラルにコロンを含めても、
     * 実際の名前付きパラメータのみが検出される）。
     */
    @Property
    boolean detectedParameters_excludeColonsInsideStringLiterals(@ForAll("paramName") String paramName) {
        String sql = "SELECT * FROM t WHERE a = 'literal:with:colons' AND b = :" + paramName;

        return new QuerySqlAnalyzer(sql).detectParameters().equals(List.of(paramName));
    }
}

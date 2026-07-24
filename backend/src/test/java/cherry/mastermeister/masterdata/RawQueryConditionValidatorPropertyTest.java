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
import cherry.mastermeister.rdbmsconnection.dialect.H2DialectStrategy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * business-logic-model.md §7.1（PBT-01）。BR-MASTER-04。
 */
class RawQueryConditionValidatorPropertyTest {

    private final RawQueryConditionValidator validator = new RawQueryConditionValidator();
    private final H2DialectStrategy dialect = new H2DialectStrategy();

    @Provide
    Arbitrary<String> columnName() {
        return Arbitraries.of("amount", "status", "created_at", "quantity");
    }

    @Provide
    Arbitrary<Long> numericLiteral() {
        return Arbitraries.longs().between(-100_000L, 100_000L);
    }

    @Provide
    Arbitrary<String> stringLiteral() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10);
    }

    /**
     * 安全性の不変条件: 許可構文のみからなる任意のWHERE句は、構文検証を通過した結果として、
     * リテラル値が直接文字列連結されず、必ずバインドパラメータ（:rawParamN）として渡される
     * （whereSqlにリテラル値自体の文字列表現が出現しない）。
     */
    @Property
    boolean validComparison_neverInlinesLiteralValue_alwaysUsesBindParameter(
            @ForAll("columnName") String column, @ForAll("numericLiteral") Long value) {
        var result = validator.validate(dialect, column + " = " + value, null);

        // whereSql自体の構造が「クオート済みカラム名 = :rawParam0」ちょうどであることを検証する
        // （リテラル値はwhereSql中のいかなる位置にも出現せず、必ずwhereParams経由でのみ渡される）
        String expectedSql = "\"" + column + "\" = :rawParam0";
        return result.whereSql().equals(expectedSql)
                && result.whereParams().get("rawParam0").equals(value);
    }

    /**
     * 安全性の不変条件（文字列リテラル版）: 任意の英小文字列を用いたWHERE句も、
     * whereSqlには元のリテラル文字列がそのまま出現せず、バインドパラメータ経由で渡される。
     */
    @Property
    boolean validStringComparison_neverInlinesLiteralValue(
            @ForAll("columnName") String column, @ForAll("stringLiteral") String value) {
        var result = validator.validate(dialect, column + " = '" + value + "'", null);

        String expectedSql = "\"" + column + "\" = :rawParam0";
        return result.whereSql().equals(expectedSql)
                && result.whereParams().get("rawParam0").equals(value);
    }

    @Provide
    Arbitrary<String> forbiddenClause() {
        Arbitrary<String> column = columnName();
        Arbitrary<String> forbiddenFragment = Arbitraries.of(
                "id IN (SELECT id FROM other_table)",
                "UPPER(status) = 'A'",
                "LOWER(status) = 'a'",
                "status = 'A' -- trailing comment",
                "status = 'A'; DROP TABLE items",
                "status = (SELECT status FROM other LIMIT 1)");
        return Combinators.combine(column, forbiddenFragment).as((c, f) -> f);
    }

    /**
     * 拒否の健全性: サブクエリ・関数呼び出し・コメント記号・複数ステートメント区切りのいずれかを
     * 含む入力は、必ず{@link InvalidQueryConditionException}で拒否される。
     */
    @Property
    boolean forbiddenConstructs_areAlwaysRejected(@ForAll("forbiddenClause") String clause) {
        try {
            validator.validate(dialect, clause, null);
            return false;
        } catch (InvalidQueryConditionException e) {
            return true;
        }
    }
}

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuerySqlAnalyzerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM t",
            "SELECT a, b FROM t WHERE a = 1",
            "SELECT t1.a, t2.b FROM t1 JOIN t2 ON t1.id = t2.id",
            "SELECT * FROM (SELECT * FROM u WHERE id = 1) sub",
            "SELECT COUNT(*) FROM t GROUP BY a",
            "SELECT 1 UNION SELECT 2",
            "WITH cte AS (SELECT 1) SELECT * FROM cte",
            "SELECT * FROM t;",
            "SELECT * FROM t; -- trailing comment",
    })
    void isReadOnly_acceptsSingleSelectStatement(String sql) {
        assertThat(new QuerySqlAnalyzer(sql).isReadOnly()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO t (a) VALUES (1)",
            "UPDATE t SET a = 1",
            "DELETE FROM t",
            "DROP TABLE t",
            "SELECT 1; DELETE FROM t",
            "not a valid sql at all",
    })
    void isReadOnly_rejectsNonSelectOrMultiStatement(String sql) {
        assertThat(new QuerySqlAnalyzer(sql).isReadOnly()).isFalse();
    }

    @Test
    void isReadOnly_rejectsNullOrBlank() {
        assertThat(new QuerySqlAnalyzer(null).isReadOnly()).isFalse();
        assertThat(new QuerySqlAnalyzer("").isReadOnly()).isFalse();
        assertThat(new QuerySqlAnalyzer("   ").isReadOnly()).isFalse();
    }

    @Test
    void detectParameters_findsNamedParameters_excludingStringLiterals() {
        QuerySqlAnalyzer analyzer = new QuerySqlAnalyzer(
                "SELECT * FROM t WHERE a = :p1 AND b = 'x:y' AND c = :p2");

        assertThat(analyzer.detectParameters()).containsExactly("p1", "p2");
    }

    @Test
    void detectParameters_findsParametersAcrossJoinsAndSubqueries() {
        QuerySqlAnalyzer analyzer = new QuerySqlAnalyzer(
                "SELECT * FROM (SELECT * FROM u WHERE id = :innerParam) sub "
                        + "JOIN t ON sub.id = t.id WHERE t.a = :outerParam");

        assertThat(analyzer.detectParameters()).containsExactly("innerParam", "outerParam");
    }

    @Test
    void detectParameters_returnsEmptyList_whenNoParameters() {
        assertThat(new QuerySqlAnalyzer("SELECT * FROM t").detectParameters()).isEmpty();
    }

    @Test
    void detectParameters_returnsEmptyList_whenNotReadOnly() {
        assertThat(new QuerySqlAnalyzer("DELETE FROM t WHERE a = :p1").detectParameters()).isEmpty();
    }
}

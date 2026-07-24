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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * business-rules.md BR-MASTER-04。business-logic-model.md §7.1（PBT-01）。
 */
class RawQueryConditionValidatorTest {

    private final RawQueryConditionValidator validator = new RawQueryConditionValidator();
    private final H2DialectStrategy dialect = new H2DialectStrategy();

    @Test
    void validate_returnsEmpty_whenBothClausesBlank() {
        var result = validator.validate(dialect, null, "  ");
        assertThat(result.whereSql()).isNull();
        assertThat(result.orderBySql()).isNull();
        assertThat(result.whereParams()).isEmpty();
    }

    @Test
    void validate_acceptsComparisonAndLogicalOperators() {
        var result = validator.validate(dialect, "amount > 100 AND status = 'active'", null);
        assertThat(result.whereSql()).isEqualTo("(\"amount\" > :rawParam0 AND \"status\" = :rawParam1)");
        assertThat(result.whereParams()).containsEntry("rawParam0", 100L).containsEntry("rawParam1", "active");
    }

    @Test
    void validate_acceptsOrAndParentheses() {
        var result = validator.validate(dialect, "(status = 'A' OR status = 'B') AND amount <= 50", null);
        assertThat(result.whereSql()).isEqualTo(
                "(((\"status\" = :rawParam0 OR \"status\" = :rawParam1)) AND \"amount\" <= :rawParam2)");
    }

    @Test
    void validate_acceptsOrderByColumnAscDesc() {
        var result = validator.validate(dialect, null, "amount DESC, status ASC");
        assertThat(result.orderBySql()).isEqualTo("\"amount\" DESC, \"status\" ASC");
    }

    @Test
    void validate_rejectsSubquery() {
        assertThatThrownBy(() -> validator.validate(dialect, "id IN (SELECT id FROM other)", null))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void validate_rejectsFunctionCall() {
        assertThatThrownBy(() -> validator.validate(dialect, "UPPER(status) = 'A'", null))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void validate_rejectsCommentMarker() {
        assertThatThrownBy(() -> validator.validate(dialect, "status = 'A' -- comment", null))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void validate_rejectsMultipleStatements() {
        assertThatThrownBy(() -> validator.validate(dialect, "1=1; DROP TABLE users", null))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void validate_rejectsNonColumnOrderByExpression() {
        assertThatThrownBy(() -> validator.validate(dialect, null, "UPPER(status) ASC"))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void validate_rejectsSyntaxError() {
        assertThatThrownBy(() -> validator.validate(dialect, "amount >", null))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void validate_acceptsNegativeNumberLiteral() {
        var result = validator.validate(dialect, "balance >= -100", null);
        assertThat(result.whereParams()).containsEntry("rawParam0", -100L);
    }
}

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

package cherry.mastermeister.querybuilder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * domain-entities.md §4。SELECT項目の1件。単純な列参照（{@code column}）と集計関数適用
 * （{@code aggregate}）は排他（BR-QUERYBUILDER-09）。{@code alias}は任意（AS別名）。
 */
public record SelectItemDto(
        @Valid ColumnRefDto column,
        @Valid AggregateExpressionDto aggregate,
        String alias
) {

    @AssertTrue(message = "exactly one of column or aggregate must be set")
    public boolean isColumnOrAggregateExclusive() {
        return (column != null) ^ (aggregate != null);
    }
}

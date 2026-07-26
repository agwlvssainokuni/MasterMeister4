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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * domain-entities.md §6。ORDER BY項目の1件。並び替え対象は単純な列参照または集計関数適用の
 * 結果のいずれか排他。
 */
public record OrderByItemDto(
        @Valid ColumnRefDto column,
        @Valid AggregateExpressionDto aggregate,
        @NotNull SortDirection direction
) {

    // 実機E2E検証で発見: SelectItemDtoと同じ理由でJacksonへのシリアライズを除外する。
    @JsonIgnore
    @AssertTrue(message = "exactly one of column or aggregate must be set")
    public boolean isColumnOrAggregateExclusive() {
        return (column != null) ^ (aggregate != null);
    }
}

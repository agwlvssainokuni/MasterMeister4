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
import jakarta.validation.constraints.NotNull;

/**
 * domain-entities.md §4。BR-QUERYBUILDER-09。{@code distinct}はCOUNT/SUM/AVGへの適用を主眼とするが、
 * MIN/MAXへの指定も標準SQL上は構文的に有効（意味的にはMIN/MAXの結果に影響しない）であるため、
 * 関数種別による組み合わせ制限は設けない。
 */
public record AggregateExpressionDto(
        @NotNull AggregateFunction function,
        @NotNull @Valid ColumnRefDto column,
        boolean distinct
) {
}

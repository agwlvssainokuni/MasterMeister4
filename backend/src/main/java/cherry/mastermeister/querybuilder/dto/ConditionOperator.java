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

/**
 * domain-entities.md §5。BR-QUERYBUILDER-05。UNIT-05の{@code FilterOperator}と同じ設計思想
 * （列のデータ型分類ごとに使用可能な演算子が異なる）を踏襲した独自定義（UNIT-05への依存はしない）。
 * {@code IS_NULL}/{@code IS_NOT_NULL}は比較値を必要としない（{@link ConditionDto#value()}はnull）。
 */
public enum ConditionOperator {
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE,
    STARTS_WITH,
    CONTAINS,
    IS_NULL,
    IS_NOT_NULL
}

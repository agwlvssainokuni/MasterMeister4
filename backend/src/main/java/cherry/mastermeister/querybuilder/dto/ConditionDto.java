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
import jakarta.validation.constraints.NotNull;

/**
 * domain-entities.md §5。WHERE/HAVING共通の条件1件。比較対象は単純な列参照（{@code column}）
 * または集計関数適用の結果（{@code aggregate}、HAVINGでのみ使用）のいずれか排他（BR-QUERYBUILDER-04）。
 * {@code value}は{@code IS_NULL}/{@code IS_NOT_NULL}では不要（null）。
 * <p>
 * {@code dataTypeCategory}は実装時に追加（Part 2実装で発見）: {@code generateSql}は
 * DBアクセスを伴わない純粋な変換であるため、比較値を型安全なSQLリテラルへ変換する
 * （tech-stack-decisions.md §2）には列のデータ型分類が別途必要になる。フロントエンドは
 * テーブル/カラム一覧取得APIで既に各列の{@code dataTypeCategory}を取得済みのため、
 * WHERE/HAVING条件の指定時にその値をそのまま送信する。
 */
public record ConditionDto(
        @Valid ColumnRefDto column,
        @Valid AggregateExpressionDto aggregate,
        @NotNull ConditionOperator operator,
        String value,
        @NotNull ColumnDataTypeCategory dataTypeCategory
) {

    @AssertTrue(message = "exactly one of column or aggregate must be set")
    public boolean isColumnOrAggregateExclusive() {
        return (column != null) ^ (aggregate != null);
    }

    @AssertTrue(message = "value is required unless operator is IS_NULL or IS_NOT_NULL")
    public boolean isValuePresentWhenRequired() {
        boolean valueless = operator == ConditionOperator.IS_NULL || operator == ConditionOperator.IS_NOT_NULL;
        return valueless || (value != null && !value.isBlank());
    }
}

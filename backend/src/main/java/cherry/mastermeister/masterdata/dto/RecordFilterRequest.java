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

package cherry.mastermeister.masterdata.dto;

import cherry.mastermeister.masterdata.model.FilterOperator;
import cherry.mastermeister.masterdata.model.RecordFilterCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * business-rules.md BR-MASTER-05。レコード一覧取得のクエリパラメータ{@code filter}に
 * JSON配列としてエンコードして渡す（frontend-components.md §3、Code Generation時点の実装判断）。
 */
public record RecordFilterRequest(
        @NotBlank String columnName,
        @NotNull FilterOperator operator,
        String value,
        String valueTo
) {

    public RecordFilterCondition toModel() {
        return new RecordFilterCondition(columnName, operator, value, valueTo);
    }
}

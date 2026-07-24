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

package cherry.mastermeister.masterdata.model;

import java.util.Map;

/**
 * domain-entities.md §7。BR-MASTER-06〜09。一括反映バッチ内の1行操作。
 * {@code primaryKeyValues}は{@code UPDATE}/{@code DELETE}で必須（BR-MASTER-08）、
 * {@code columnValues}は{@code CREATE}/{@code UPDATE}で必須。値はBR-MASTER-09により文字列表現。
 */
public record BatchOperationItem(
        OperationType operationType,
        Map<String, String> primaryKeyValues,
        Map<String, String> columnValues
) {
}

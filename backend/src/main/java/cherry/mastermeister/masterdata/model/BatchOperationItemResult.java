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

/**
 * domain-entities.md §8。BR-MASTER-07。バッチ内の失敗した1行操作の結果。
 * {@code errorCode}は{@code PERMISSION_DENIED}/{@code CONSTRAINT_VIOLATION}/{@code INVALID_VALUE}
 * に加え、対象行が存在しない場合の{@code RECORD_NOT_FOUND}を使用する（Code Generation時点の追加）。
 */
public record BatchOperationItemResult(
        int index,
        String errorCode,
        String errorMessage
) {
}

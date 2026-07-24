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
 * domain-entities.md §4。BR-MASTER-05。フィルタUIで指定する絞込条件。
 * {@code value}/{@code valueTo}はBR-MASTER-09により文字列表現のまま受け渡す
 * （カラムのデータ型に応じたパース・変換はRecordQueryServiceが行う）。
 */
public record RecordFilterCondition(
        String columnName,
        FilterOperator operator,
        String value,
        String valueTo
) {
}

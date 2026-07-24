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

import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import org.springframework.stereotype.Component;

/**
 * logical-components.md §1（Q4=A）。
 * 実装判断（訂正）: 当初案はJDBC型情報（{@code java.sql.Types}相当）から直接導出する想定だったが、
 * UNIT-03の{@code SchemaColumn.normalizedType}が既にJDBC型情報を正規化した分類
 * （{@link NormalizedType}: STRING/NUMBER/DATE_TIME/BOOLEAN/BINARY/OTHER）として保持していることが
 * 判明したため、生のJDBC型情報を再解析せず、この既存の正規化結果を単純にマッピングする
 * （UNIT-03のエンティティ自体には変更を加えない、モジュール境界を維持）。
 */
@Component
public class ColumnDataTypeMapper {

    public ColumnDataTypeCategory toCategory(NormalizedType normalizedType) {
        return switch (normalizedType) {
            case NUMBER -> ColumnDataTypeCategory.NUMERIC;
            case DATE_TIME -> ColumnDataTypeCategory.DATETIME;
            case BOOLEAN -> ColumnDataTypeCategory.BOOLEAN;
            // BINARY/OTHERはBR-MASTER-05の分類に存在しないため、文字列型と同等の
            // 最も制約の緩い演算子集合（=、前方一致、部分一致）にフォールバックする
            case STRING, BINARY, OTHER -> ColumnDataTypeCategory.STRING;
        };
    }
}

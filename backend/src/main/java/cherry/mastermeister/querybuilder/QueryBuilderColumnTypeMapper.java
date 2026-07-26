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

package cherry.mastermeister.querybuilder;

import cherry.mastermeister.querybuilder.dto.ColumnDataTypeCategory;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import org.springframework.stereotype.Component;

/**
 * logical-components.md §1（Q4=A）。tech-stack-decisions.md §4。
 * UNIT-05の{@code ColumnDataTypeMapper}と同じ設計思想（UNIT-03の{@code SchemaColumn.normalizedType}
 * からのマッピング）を、UNIT-05へは依存せず独自クラスとして再実装する。
 */
@Component
public class QueryBuilderColumnTypeMapper {

    public ColumnDataTypeCategory toCategory(NormalizedType normalizedType) {
        return switch (normalizedType) {
            case NUMBER -> ColumnDataTypeCategory.NUMERIC;
            case DATE_TIME -> ColumnDataTypeCategory.DATETIME;
            case BOOLEAN -> ColumnDataTypeCategory.BOOLEAN;
            // BINARY/OTHERはWHERE/HAVING演算子の絞り込み対象外のため、最も制約の緩い
            // 文字列型と同等に扱う（UNIT-05のColumnDataTypeMapperと同じフォールバック方針）
            case STRING, BINARY, OTHER -> ColumnDataTypeCategory.STRING;
        };
    }
}

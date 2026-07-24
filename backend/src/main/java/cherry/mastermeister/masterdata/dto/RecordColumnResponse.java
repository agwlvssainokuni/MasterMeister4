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

import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.masterdata.model.RecordColumn;

public record RecordColumnResponse(
        String columnName,
        ColumnDataTypeCategory dataTypeCategory,
        boolean primaryKey,
        boolean editable
) {

    public static RecordColumnResponse from(RecordColumn column) {
        return new RecordColumnResponse(column.columnName(), column.dataTypeCategory(), column.primaryKey(),
                column.editable());
    }
}

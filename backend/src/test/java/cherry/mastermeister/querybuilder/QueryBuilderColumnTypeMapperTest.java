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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryBuilderColumnTypeMapperTest {

    private final QueryBuilderColumnTypeMapper mapper = new QueryBuilderColumnTypeMapper();

    @Test
    void mapsNumberToNumeric() {
        assertThat(mapper.toCategory(NormalizedType.NUMBER)).isEqualTo(ColumnDataTypeCategory.NUMERIC);
    }

    @Test
    void mapsDateTimeToDatetime() {
        assertThat(mapper.toCategory(NormalizedType.DATE_TIME)).isEqualTo(ColumnDataTypeCategory.DATETIME);
    }

    @Test
    void mapsBooleanToBoolean() {
        assertThat(mapper.toCategory(NormalizedType.BOOLEAN)).isEqualTo(ColumnDataTypeCategory.BOOLEAN);
    }

    @Test
    void mapsStringToString() {
        assertThat(mapper.toCategory(NormalizedType.STRING)).isEqualTo(ColumnDataTypeCategory.STRING);
    }

    @Test
    void mapsBinaryAndOtherToStringAsFallback() {
        assertThat(mapper.toCategory(NormalizedType.BINARY)).isEqualTo(ColumnDataTypeCategory.STRING);
        assertThat(mapper.toCategory(NormalizedType.OTHER)).isEqualTo(ColumnDataTypeCategory.STRING);
    }
}

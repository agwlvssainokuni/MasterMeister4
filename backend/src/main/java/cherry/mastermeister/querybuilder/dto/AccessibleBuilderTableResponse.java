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

import cherry.mastermeister.rdbmsconnection.entity.TableType;

import java.util.List;

/**
 * domain-entities.md §7。BR-QUERYBUILDER-01。テーブル単位または列単位いずれかの実効主権限が
 * 非NONEのテーブルのみが含まれる（UNIT-05の{@code isTableVisible}と同じOR条件）。
 */
public record AccessibleBuilderTableResponse(
        String tableName,
        TableType tableType,
        List<AccessibleBuilderColumnResponse> columns
) {
}

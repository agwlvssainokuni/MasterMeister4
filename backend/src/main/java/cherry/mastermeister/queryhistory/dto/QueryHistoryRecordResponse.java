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

package cherry.mastermeister.queryhistory.dto;

import java.time.Instant;

/**
 * domain-entities.md §3。QueryExecutionRecordの表示用ビュー。executorDisplayName・savedQueryNameは
 * QueryHistoryServiceが一括解決する（BR-QUERYHISTORY-06、参照整合性の扱いはbusiness-logic-model.md §6）。
 */
public record QueryHistoryRecordResponse(
        Long id,
        Long executedBy,
        String executorDisplayName,
        Long connectionId,
        String schemaName,
        String sql,
        Long savedQueryId,
        String savedQueryName,
        QueryType queryType,
        long rowCount,
        long durationMillis,
        Instant executedAt
) {
}

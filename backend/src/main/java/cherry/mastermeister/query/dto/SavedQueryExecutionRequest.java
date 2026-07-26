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

package cherry.mastermeister.query.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * frontend-components.md A-4。保存クエリ実行（{@code POST .../saved/{savedQueryId}/execute}）の
 * リクエストボディ。SQLは含まない（FR-7.9、保存クエリのSQLは編集不可のまま実行する）。
 */
public record SavedQueryExecutionRequest(
        @NotBlank String schemaName,
        Map<String, String> params,
        boolean pagingEnabled,
        int page,
        int pageSize
) {
}

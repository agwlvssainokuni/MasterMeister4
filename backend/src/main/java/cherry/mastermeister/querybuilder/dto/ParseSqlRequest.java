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

import jakarta.validation.constraints.NotBlank;

/**
 * リバースエンジニアリングAPI（{@code POST /api/query-builder/{connectionId}/parse}）のリクエスト。
 * FR-5.7。参照テーブル/カラムのアクセス可否確認（BR-QUERYBUILDER-01）に対象スキーマが必要なため、
 * SQL文字列と併せて{@code schemaName}を受け取る。
 */
public record ParseSqlRequest(
        @NotBlank String schemaName,
        @NotBlank String sql
) {
}

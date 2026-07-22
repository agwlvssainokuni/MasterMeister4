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

package cherry.mastermeister.rdbmsconnection.dto;

import cherry.mastermeister.rdbmsconnection.entity.DbType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * frontend-components.md §1.1。登録・更新共通。BR-RDBMS-01（形式チェックのみ）。
 * 更新時、{@code password}が空欄の場合は既存の暗号化パスワードを保持する（BR-RDBMS-12、
 * サービス層で判定）。
 */
public record RdbmsConnectionRequest(
        @NotBlank String displayName,
        @NotNull DbType dbType,
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotBlank String databaseName,
        @NotBlank String username,
        String password,
        String additionalParams
) {
}

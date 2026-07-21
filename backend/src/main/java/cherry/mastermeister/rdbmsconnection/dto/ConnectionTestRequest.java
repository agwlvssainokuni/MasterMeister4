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
 * BR-RDBMS-11。フォーム入力中の未保存の値に対する接続テスト。保存済み接続の更新とは異なり、
 * passwordは常に必須とする（「変更しない」概念が存在しないため）。
 */
public record ConnectionTestRequest(
        @NotNull DbType dbType,
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotBlank String databaseName,
        String schemaName,
        @NotBlank String username,
        @NotBlank String password,
        String additionalParams
) {
}

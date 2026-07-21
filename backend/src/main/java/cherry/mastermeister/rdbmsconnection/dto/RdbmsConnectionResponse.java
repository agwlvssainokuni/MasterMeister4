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
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;

import java.time.Instant;

/**
 * frontend-components.md §1。BR-RDBMS-12: パスワードフィールドを一切含めない。
 * {@code schemaImportedAt}は未取込の場合null（一覧の「スキーマ取込状態」列に使用）。
 */
public record RdbmsConnectionResponse(
        Long id,
        String displayName,
        DbType dbType,
        String host,
        int port,
        String databaseName,
        String schemaName,
        String username,
        String additionalParams,
        Instant schemaImportedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static RdbmsConnectionResponse from(RdbmsConnection connection, Instant schemaImportedAt) {
        return new RdbmsConnectionResponse(connection.getId(), connection.getDisplayName(), connection.getDbType(),
                connection.getHost(), connection.getPort(), connection.getDatabaseName(),
                connection.getSchemaName(), connection.getUsername(), connection.getAdditionalParams(),
                schemaImportedAt, connection.getCreatedAt(), connection.getUpdatedAt());
    }
}

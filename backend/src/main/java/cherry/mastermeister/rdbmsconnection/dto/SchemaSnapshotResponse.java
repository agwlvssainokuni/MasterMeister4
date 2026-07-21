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

import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * frontend-components.md §2。スキーマ詳細画面用。
 */
public record SchemaSnapshotResponse(Long connectionId, Instant importedAt, List<SchemaTableResponse> tables) {

    public static SchemaSnapshotResponse from(SchemaSnapshot snapshot) {
        return new SchemaSnapshotResponse(snapshot.getConnectionId(), snapshot.getImportedAt(),
                snapshot.getTables().stream().map(SchemaTableResponse::from).toList());
    }
}

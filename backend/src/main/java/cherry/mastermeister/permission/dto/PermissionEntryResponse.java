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

package cherry.mastermeister.permission.dto;

import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;

import java.time.Instant;

public record PermissionEntryResponse(
        PrincipalType principalType,
        Long principalId,
        String schemaName,
        String tableName,
        String columnName,
        PrimaryPermission primaryPermission,
        boolean createPermission,
        boolean deletePermission,
        Instant updatedAt,
        Long updatedBy
) {

    public static PermissionEntryResponse from(AccessPermission permission) {
        return new PermissionEntryResponse(permission.getPrincipalType(), permission.getPrincipalId(),
                permission.getSchemaName(), permission.getTableName(), permission.getColumnName(),
                permission.getPrimaryPermission(), permission.isCreatePermission(), permission.isDeletePermission(),
                permission.getUpdatedAt(), permission.getUpdatedBy());
    }
}

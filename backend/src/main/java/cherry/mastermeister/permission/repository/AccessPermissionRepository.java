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

package cherry.mastermeister.permission.repository;

import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessPermissionRepository extends JpaRepository<AccessPermission, Long> {

    List<AccessPermission> findAllByConnectionIdAndPrincipalTypeAndPrincipalId(
            Long connectionId, PrincipalType principalType, Long principalId);

    List<AccessPermission> findAllByConnectionId(Long connectionId);

    /**
     * BR-ACCESS-11: グループ削除時のカスケード削除（principalType=GROUPかつprincipalId=当該グループ）。
     */
    void deleteAllByPrincipalTypeAndPrincipalId(PrincipalType principalType, Long principalId);

    /**
     * BR-ACCESS-01: upsert対象の既存行検索。tableName/columnNameは呼び出し側で
     * センチネル値（空文字列）に変換済みの値を渡す。
     */
    Optional<AccessPermission> findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
            Long connectionId, PrincipalType principalType, Long principalId, String schemaName,
            String tableNameRaw, String columnNameRaw);
}

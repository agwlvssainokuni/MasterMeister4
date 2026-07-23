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

package cherry.mastermeister.permission;

import cherry.mastermeister.group.entity.GroupMembership;
import cherry.mastermeister.group.repository.GroupMembershipRepository;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * COMP-11。business-logic-model.md §2。BR-ACCESS-04〜08。
 * 実効権限（主権限・補助権限）の判定・合成。判定結果は{@code effectivePermission}キャッシュへ
 * 格納する（無効化は各mutationメソッド側の{@code @CacheEvict}で行う、nfr-design-patterns.md §2.1）。
 * canCreate/canDeleteが必要とする主キー列情報は、AccessPermissionからUNIT-03のエンティティへ
 * 外部キーを張らず、SchemaIntrospectionService.getSchema()をスキーマ名・テーブル名で検索して
 * 取得する（domain-entities.md §1の設計と整合、unit-04-code-generation-plan.md参照）。
 */
@Service
public class EffectivePermissionResolver {

    private final AccessPermissionRepository accessPermissionRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final SchemaIntrospectionService schemaIntrospectionService;

    public EffectivePermissionResolver(AccessPermissionRepository accessPermissionRepository,
                                        GroupMembershipRepository groupMembershipRepository,
                                        SchemaIntrospectionService schemaIntrospectionService) {
        this.accessPermissionRepository = accessPermissionRepository;
        this.groupMembershipRepository = groupMembershipRepository;
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    /**
     * BR-ACCESS-04: schemaName必須、tableName/columnNameは問い合わせ対象の階層に応じてnull可
     * （カラムを問い合わせる場合は両方指定、テーブルを問い合わせる場合はtableNameのみ、
     * スキーマを問い合わせる場合は両方null）。
     */
    @Cacheable(cacheNames = "effectivePermission")
    public PrimaryPermission resolvePrimary(Long userId, Long connectionId, String schemaName, String tableName,
                                             String columnName) {
        List<AccessPermission> own = accessPermissionRepository
                .findAllByConnectionIdAndPrincipalTypeAndPrincipalId(connectionId, PrincipalType.USER, userId);
        Optional<PrimaryPermission> ownResolved = resolvePrimaryFromList(own, schemaName, tableName, columnName);
        if (ownResolved.isPresent()) {
            return ownResolved.get();
        }

        PrimaryPermission combined = PrimaryPermission.NONE;
        boolean anyGroupSetting = false;
        for (Long groupId : groupIdsOf(userId)) {
            List<AccessPermission> groupPermissions = accessPermissionRepository
                    .findAllByConnectionIdAndPrincipalTypeAndPrincipalId(connectionId, PrincipalType.GROUP, groupId);
            Optional<PrimaryPermission> groupResolved = resolvePrimaryFromList(groupPermissions, schemaName,
                    tableName, columnName);
            if (groupResolved.isPresent()) {
                anyGroupSetting = true;
                if (groupResolved.get().ordinal() > combined.ordinal()) {
                    combined = groupResolved.get();
                }
            }
        }
        return anyGroupSetting ? combined : PrimaryPermission.NONE;
    }

    /**
     * BR-ACCESS-06: CREATE補助権限がtrueであり、かつ（主キーなし、または全主キー列の実効主権限がUPDATE）。
     */
    @Cacheable(cacheNames = "effectivePermission")
    public boolean canCreate(Long userId, Long connectionId, String schemaName, String tableName) {
        if (!resolveAuxiliary(userId, connectionId, schemaName, tableName, AccessPermission::isCreatePermission)) {
            return false;
        }
        List<String> primaryKeyColumns = primaryKeyColumns(connectionId, schemaName, tableName);
        if (primaryKeyColumns.isEmpty()) {
            return true;
        }
        return primaryKeyColumns.stream()
                .allMatch(column -> resolvePrimary(userId, connectionId, schemaName, tableName, column)
                        == PrimaryPermission.UPDATE);
    }

    /**
     * BR-ACCESS-07: DELETE補助権限がtrueであり、かつ主キーを持ち、かつ全主キー列の実効主権限がREAD以上。
     * 主キーを持たないテーブルは常に削除不可。
     */
    @Cacheable(cacheNames = "effectivePermission")
    public boolean canDelete(Long userId, Long connectionId, String schemaName, String tableName) {
        if (!resolveAuxiliary(userId, connectionId, schemaName, tableName, AccessPermission::isDeletePermission)) {
            return false;
        }
        List<String> primaryKeyColumns = primaryKeyColumns(connectionId, schemaName, tableName);
        if (primaryKeyColumns.isEmpty()) {
            return false;
        }
        return primaryKeyColumns.stream()
                .allMatch(column -> resolvePrimary(userId, connectionId, schemaName, tableName, column)
                        != PrimaryPermission.NONE);
    }

    private boolean resolveAuxiliary(Long userId, Long connectionId, String schemaName, String tableName,
                                      Predicate<AccessPermission> auxiliaryFlag) {
        List<AccessPermission> own = accessPermissionRepository
                .findAllByConnectionIdAndPrincipalTypeAndPrincipalId(connectionId, PrincipalType.USER, userId);
        Optional<Boolean> ownResolved = resolveAuxiliaryFromList(own, schemaName, tableName, auxiliaryFlag);
        if (ownResolved.isPresent()) {
            return ownResolved.get();
        }

        boolean combined = false;
        boolean anyGroupSetting = false;
        for (Long groupId : groupIdsOf(userId)) {
            List<AccessPermission> groupPermissions = accessPermissionRepository
                    .findAllByConnectionIdAndPrincipalTypeAndPrincipalId(connectionId, PrincipalType.GROUP, groupId);
            Optional<Boolean> groupResolved = resolveAuxiliaryFromList(groupPermissions, schemaName, tableName,
                    auxiliaryFlag);
            if (groupResolved.isPresent()) {
                anyGroupSetting = true;
                combined = combined || groupResolved.get();
            }
        }
        return anyGroupSetting && combined;
    }

    private List<Long> groupIdsOf(Long userId) {
        return groupMembershipRepository.findAllByUserId(userId).stream().map(GroupMembership::getGroupId).toList();
    }

    private static Optional<PrimaryPermission> resolvePrimaryFromList(List<AccessPermission> permissions,
                                                                        String schemaName, String tableName,
                                                                        String columnName) {
        if (columnName != null) {
            Optional<AccessPermission> columnLevel = findMatch(permissions, schemaName, tableName, columnName);
            if (columnLevel.isPresent()) {
                return columnLevel.map(AccessPermission::getPrimaryPermission);
            }
        }
        if (tableName != null) {
            Optional<AccessPermission> tableLevel = findMatch(permissions, schemaName, tableName, null);
            if (tableLevel.isPresent()) {
                return tableLevel.map(AccessPermission::getPrimaryPermission);
            }
        }
        return findMatch(permissions, schemaName, null, null).map(AccessPermission::getPrimaryPermission);
    }

    private static Optional<Boolean> resolveAuxiliaryFromList(List<AccessPermission> permissions, String schemaName,
                                                                 String tableName,
                                                                 Predicate<AccessPermission> auxiliaryFlag) {
        Optional<AccessPermission> tableLevel = findMatch(permissions, schemaName, tableName, null);
        if (tableLevel.isPresent()) {
            return tableLevel.map(auxiliaryFlag::test);
        }
        return findMatch(permissions, schemaName, null, null).map(auxiliaryFlag::test);
    }

    private static Optional<AccessPermission> findMatch(List<AccessPermission> permissions, String schemaName,
                                                          String tableName, String columnName) {
        return permissions.stream()
                .filter(p -> p.getSchemaName().equals(schemaName))
                .filter(p -> Objects.equals(p.getTableName(), tableName))
                .filter(p -> Objects.equals(p.getColumnName(), columnName))
                .findFirst();
    }

    private List<String> primaryKeyColumns(Long connectionId, String schemaName, String tableName) {
        Optional<SchemaTable> table = schemaIntrospectionService.getSchema(connectionId).stream()
                .flatMap(snapshot -> snapshot.getTables().stream())
                .filter(t -> t.getSchemaName().equals(schemaName) && t.getTableName().equals(tableName))
                .findFirst();
        return table.flatMap(this::primaryKeyConstraint).map(SchemaConstraint::getColumnNames).orElse(List.of());
    }

    private Optional<SchemaConstraint> primaryKeyConstraint(SchemaTable table) {
        return table.getConstraints().stream()
                .filter(constraint -> constraint.getConstraintType() == ConstraintType.PRIMARY_KEY)
                .findFirst();
    }
}

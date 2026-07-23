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

import cherry.mastermeister.group.entity.Group;
import cherry.mastermeister.group.entity.GroupMembership;
import cherry.mastermeister.group.repository.GroupMembershipRepository;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §5.1のPBT対象プロパティ（PBT-01/03）。
 * jqwikはUNIT-02で依存追加済みだが、本ユニットが実際のプロパティテスト初適用となる。
 */
class EffectivePermissionResolverPropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_A_ID = 10L;
    private static final Long GROUP_B_ID = 20L;
    private static final Long CONNECTION_ID = 100L;

    @Provide
    Arbitrary<PrimaryPermission> anyPermission() {
        return Arbitraries.of(PrimaryPermission.class);
    }

    private static AccessPermission perm(PrincipalType type, Long id, String tableName, String columnName,
                                          PrimaryPermission primary) {
        return new AccessPermission(CONNECTION_ID, type, id, "public", tableName, columnName, primary, false, false,
                Instant.now(), 1L);
    }

    private static GroupMembership membership(Long groupId) {
        GroupMembership membership = new GroupMembership(USER_ID);
        try {
            Group group = new Group("g" + groupId, Instant.now());
            Field idField = Group.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(group, groupId);
            Field groupField = GroupMembership.class.getDeclaredField("group");
            groupField.setAccessible(true);
            groupField.set(membership, group);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return membership;
    }

    /**
     * 階層優先の不変条件: 同一プリンシパルの設定において、より詳細な階層（カラム）に設定が
     * 存在する限り、より粗い階層（テーブル／スキーマ）の設定値に関わらず、解決結果は常に
     * カラム階層の値と一致する。
     */
    @Property
    boolean hierarchyPriority_columnLevelAlwaysWinsOverCoarserLevels(
            @ForAll("anyPermission") PrimaryPermission columnValue,
            @ForAll("anyPermission") PrimaryPermission tableValue,
            @ForAll("anyPermission") PrimaryPermission schemaValue) {
        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        GroupMembershipRepository groupMembershipRepository = mock(GroupMembershipRepository.class);
        when(groupMembershipRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.USER, USER_ID)).thenReturn(List.of(
                perm(PrincipalType.USER, USER_ID, "products", "category_id", columnValue),
                perm(PrincipalType.USER, USER_ID, "products", null, tableValue),
                perm(PrincipalType.USER, USER_ID, null, null, schemaValue)));
        EffectivePermissionResolver resolver = new EffectivePermissionResolver(accessPermissionRepository,
                groupMembershipRepository, mock(SchemaIntrospectionService.class));

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                "category_id");

        return result == columnValue;
    }

    /**
     * 個別設定優先の不変条件: ユーザ自身の設定が対象リソースに1件でも存在する限り、
     * 所属グループの設定内容は解決結果に一切影響しない。
     */
    @Property
    boolean individualSettingPriority_groupSettingNeverAffectsResultWhenOwnSettingExists(
            @ForAll("anyPermission") PrimaryPermission ownValue,
            @ForAll("anyPermission") PrimaryPermission groupValue) {
        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        GroupMembershipRepository groupMembershipRepository = mock(GroupMembershipRepository.class);
        when(groupMembershipRepository.findAllByUserId(USER_ID)).thenReturn(List.of(membership(GROUP_A_ID)));
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.USER, USER_ID))
                .thenReturn(List.of(perm(PrincipalType.USER, USER_ID, "products", null, ownValue)));
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.GROUP, GROUP_A_ID))
                .thenReturn(List.of(perm(PrincipalType.GROUP, GROUP_A_ID, "products", null, groupValue)));
        EffectivePermissionResolver resolver = new EffectivePermissionResolver(accessPermissionRepository,
                groupMembershipRepository, mock(SchemaIntrospectionService.class));

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", null);

        return result == ownValue;
    }

    /**
     * グループ合成の単調性: 各グループの権限設定のうち、いずれか1つでもより許可的な値に
     * 変更すると、グループ合成結果は変化しないか、より許可的な方向にのみ変化する
     * （NONE < READ < UPDATEの順で上位を採用する式と同値であることを確認する）。
     */
    @Property
    boolean groupComposition_choosesMorePermissiveOfTwoGroups(
            @ForAll("anyPermission") PrimaryPermission groupAValue,
            @ForAll("anyPermission") PrimaryPermission groupBValue) {
        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        GroupMembershipRepository groupMembershipRepository = mock(GroupMembershipRepository.class);
        when(groupMembershipRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(membership(GROUP_A_ID), membership(GROUP_B_ID)));
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.USER, USER_ID)).thenReturn(List.of());
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.GROUP, GROUP_A_ID))
                .thenReturn(List.of(perm(PrincipalType.GROUP, GROUP_A_ID, "products", null, groupAValue)));
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.GROUP, GROUP_B_ID))
                .thenReturn(List.of(perm(PrincipalType.GROUP, GROUP_B_ID, "products", null, groupBValue)));
        EffectivePermissionResolver resolver = new EffectivePermissionResolver(accessPermissionRepository,
                groupMembershipRepository, mock(SchemaIntrospectionService.class));

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products", null);

        PrimaryPermission expected = groupAValue.ordinal() >= groupBValue.ordinal() ? groupAValue : groupBValue;
        return result == expected;
    }

    /**
     * 作成可否判定の整合性: canCreate()がtrueを返すのは、CREATE補助権限がtrueであり、
     * かつ（主キーなし、または全主キー列の実効主権限がUPDATE）の場合に限られる（単一PK列で検証）。
     */
    @Property
    boolean canCreate_matchesFormulaForSinglePrimaryKeyColumn(
            @ForAll boolean createFlag,
            @ForAll("anyPermission") PrimaryPermission primaryKeyColumnPermission) {
        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        GroupMembershipRepository groupMembershipRepository = mock(GroupMembershipRepository.class);
        SchemaIntrospectionService schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        when(groupMembershipRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.USER, USER_ID)).thenReturn(List.of(
                new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", null,
                        PrimaryPermission.NONE, createFlag, false, Instant.now(), 1L),
                new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", "id",
                        primaryKeyColumnPermission, false, false, Instant.now(), 1L)));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(schemaWithPk("products",
                List.of("id"))));
        EffectivePermissionResolver resolver = new EffectivePermissionResolver(accessPermissionRepository,
                groupMembershipRepository, schemaIntrospectionService);

        boolean result = resolver.canCreate(USER_ID, CONNECTION_ID, "public", "products");

        boolean expected = createFlag && primaryKeyColumnPermission == PrimaryPermission.UPDATE;
        return result == expected;
    }

    /**
     * 削除可否判定の整合性: canDelete()がtrueを返すのは、DELETE補助権限がtrueであり、
     * かつ主キーを持ち、かつ全主キー列の実効主権限がREAD以上の場合に限られる（単一PK列で検証）。
     */
    @Property
    boolean canDelete_matchesFormulaForSinglePrimaryKeyColumn(
            @ForAll boolean deleteFlag,
            @ForAll("anyPermission") PrimaryPermission primaryKeyColumnPermission) {
        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        GroupMembershipRepository groupMembershipRepository = mock(GroupMembershipRepository.class);
        SchemaIntrospectionService schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        when(groupMembershipRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.USER, USER_ID)).thenReturn(List.of(
                new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", null,
                        PrimaryPermission.NONE, false, deleteFlag, Instant.now(), 1L),
                new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", "id",
                        primaryKeyColumnPermission, false, false, Instant.now(), 1L)));
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(schemaWithPk("products",
                List.of("id"))));
        EffectivePermissionResolver resolver = new EffectivePermissionResolver(accessPermissionRepository,
                groupMembershipRepository, schemaIntrospectionService);

        boolean result = resolver.canDelete(USER_ID, CONNECTION_ID, "public", "products");

        boolean expected = deleteFlag && primaryKeyColumnPermission != PrimaryPermission.NONE;
        return result == expected;
    }

    private static SchemaSnapshot schemaWithPk(String tableName, List<String> pkColumns) {
        SchemaTable table = new SchemaTable("public", tableName, TableType.TABLE, null);
        for (String col : pkColumns) {
            table.addColumn(new SchemaColumn(col, 1, null, "INT", NormalizedType.NUMBER, false, null));
        }
        table.addConstraint(new SchemaConstraint(ConstraintType.PRIMARY_KEY, "pk_" + tableName, pkColumns, null,
                null));
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        return snapshot;
    }
}

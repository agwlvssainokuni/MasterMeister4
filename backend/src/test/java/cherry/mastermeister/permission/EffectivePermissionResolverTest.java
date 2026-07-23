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
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * business-rules.md BR-ACCESS-04の確認済み5ケースをexample-basedテストで再現する。
 */
@ExtendWith(MockitoExtension.class)
class EffectivePermissionResolverTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;
    private static final Long SALES_GROUP_ID = 10L;
    private static final Long ACCOUNTING_GROUP_ID = 20L;

    @Mock
    private AccessPermissionRepository accessPermissionRepository;
    @Mock
    private GroupMembershipRepository groupMembershipRepository;
    @Mock
    private SchemaIntrospectionService schemaIntrospectionService;

    private EffectivePermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EffectivePermissionResolver(accessPermissionRepository, groupMembershipRepository,
                schemaIntrospectionService);
        lenient().when(groupMembershipRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(membership(SALES_GROUP_ID), membership(ACCOUNTING_GROUP_ID)));
    }

    private static GroupMembership membership(Long groupId) {
        GroupMembership membership = new GroupMembership(USER_ID);
        try {
            var groupField = membership.getClass().getDeclaredField("group");
            groupField.setAccessible(true);
            var group = new cherry.mastermeister.group.entity.Group("g" + groupId, Instant.now());
            var idField = group.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(group, groupId);
            groupField.set(membership, group);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return membership;
    }

    private void givenOwn(AccessPermission... permissions) {
        when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.USER, USER_ID)).thenReturn(List.of(permissions));
    }

    private void givenGroup(Long groupId, AccessPermission... permissions) {
        lenient().when(accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(CONNECTION_ID,
                PrincipalType.GROUP, groupId)).thenReturn(List.of(permissions));
    }

    private static AccessPermission perm(PrincipalType type, Long id, String tableName, String columnName,
                                          PrimaryPermission primary) {
        return new AccessPermission(CONNECTION_ID, type, id, "public", tableName, columnName, primary, false, false,
                Instant.now(), 1L);
    }

    @Test
    void case1_ownTableLevelSettingWinsOverGroupSchemaLevel() {
        givenOwn(perm(PrincipalType.USER, USER_ID, "products", null, PrimaryPermission.UPDATE));
        givenGroup(SALES_GROUP_ID, perm(PrincipalType.GROUP, SALES_GROUP_ID, null, null, PrimaryPermission.READ));
        givenGroup(ACCOUNTING_GROUP_ID,
                perm(PrincipalType.GROUP, ACCOUNTING_GROUP_ID, "products", null, PrimaryPermission.NONE));

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                "category_id");

        assertThat(result).isEqualTo(PrimaryPermission.UPDATE);
    }

    @Test
    void case2_noOwnSetting_groupCompositionTakesMorePermissive() {
        givenOwn();
        givenGroup(SALES_GROUP_ID, perm(PrincipalType.GROUP, SALES_GROUP_ID, "products", null, PrimaryPermission.READ));
        givenGroup(ACCOUNTING_GROUP_ID,
                perm(PrincipalType.GROUP, ACCOUNTING_GROUP_ID, "products", "category_id", PrimaryPermission.UPDATE));

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                "category_id");

        assertThat(result).isEqualTo(PrimaryPermission.UPDATE);
    }

    @Test
    void case3_ownSettingOnDifferentTableIsIgnored_groupAppliesForThisTable() {
        givenOwn(perm(PrincipalType.USER, USER_ID, "orders", null, PrimaryPermission.UPDATE));
        givenGroup(SALES_GROUP_ID, perm(PrincipalType.GROUP, SALES_GROUP_ID, "products", null, PrimaryPermission.READ));
        givenGroup(ACCOUNTING_GROUP_ID);

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                "category_id");

        assertThat(result).isEqualTo(PrimaryPermission.READ);
    }

    @Test
    void case4_ownCoarseSchemaLevelSettingStillWinsOverGroupFineGrainedSetting() {
        givenOwn(perm(PrincipalType.USER, USER_ID, null, null, PrimaryPermission.READ));
        givenGroup(SALES_GROUP_ID, perm(PrincipalType.GROUP, SALES_GROUP_ID, "products", null, PrimaryPermission.UPDATE));
        givenGroup(ACCOUNTING_GROUP_ID,
                perm(PrincipalType.GROUP, ACCOUNTING_GROUP_ID, "products", null, PrimaryPermission.UPDATE));

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                "category_id");

        assertThat(result).isEqualTo(PrimaryPermission.READ);
    }

    @Test
    void case5_noSettingAnywhere_defaultsToNone() {
        givenOwn();
        givenGroup(SALES_GROUP_ID);
        givenGroup(ACCOUNTING_GROUP_ID);

        PrimaryPermission result = resolver.resolvePrimary(USER_ID, CONNECTION_ID, "public", "products",
                "category_id");

        assertThat(result).isEqualTo(PrimaryPermission.NONE);
    }

    private void givenSchemaWithPrimaryKey(String tableName, List<String> pkColumns) {
        SchemaTable table = new SchemaTable("public", tableName, TableType.TABLE, null);
        for (String col : pkColumns) {
            table.addColumn(new SchemaColumn(col, 1, null, "INT", NormalizedType.NUMBER, false, null));
        }
        table.addConstraint(new SchemaConstraint(ConstraintType.PRIMARY_KEY, "pk_" + tableName, pkColumns, null,
                null));
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
    }

    @Test
    void canCreate_trueWhenNoPrimaryKeyAndCreateFlagTrue() {
        SchemaTable table = new SchemaTable("public", "logs", TableType.TABLE, null);
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        givenOwn(new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "logs", null,
                PrimaryPermission.READ, true, false, Instant.now(), 1L));

        assertThat(resolver.canCreate(USER_ID, CONNECTION_ID, "public", "logs")).isTrue();
    }

    @Test
    void canCreate_falseWhenPrimaryKeyColumnNotFullyUpdatable() {
        givenSchemaWithPrimaryKey("products", List.of("category_id"));
        givenOwn(new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", null,
                PrimaryPermission.READ, true, false, Instant.now(), 1L));

        assertThat(resolver.canCreate(USER_ID, CONNECTION_ID, "public", "products")).isFalse();
    }

    @Test
    void canCreate_trueWhenPrimaryKeyColumnFullyUpdatable() {
        givenSchemaWithPrimaryKey("products", List.of("category_id"));
        givenOwn(new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", null,
                PrimaryPermission.UPDATE, true, false, Instant.now(), 1L));

        assertThat(resolver.canCreate(USER_ID, CONNECTION_ID, "public", "products")).isTrue();
    }

    @Test
    void canDelete_falseWhenNoPrimaryKey() {
        SchemaTable table = new SchemaTable("public", "logs", TableType.TABLE, null);
        SchemaSnapshot snapshot = new SchemaSnapshot(CONNECTION_ID, Instant.now());
        snapshot.addTable(table);
        when(schemaIntrospectionService.getSchema(CONNECTION_ID)).thenReturn(Optional.of(snapshot));
        givenOwn(new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "logs", null,
                PrimaryPermission.UPDATE, false, true, Instant.now(), 1L));

        assertThat(resolver.canDelete(USER_ID, CONNECTION_ID, "public", "logs")).isFalse();
    }

    @Test
    void canDelete_trueWhenDeleteFlagAndPrimaryKeyReadable() {
        givenSchemaWithPrimaryKey("products", List.of("category_id"));
        givenOwn(new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_ID, "public", "products", null,
                PrimaryPermission.READ, false, true, Instant.now(), 1L));

        assertThat(resolver.canDelete(USER_ID, CONNECTION_ID, "public", "products")).isTrue();
    }
}

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

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.common.exception.PermissionYamlImportRejectedException;
import cherry.mastermeister.group.repository.GroupRepository;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §5.2のPBT対象プロパティ（PBT-02）。
 */
class PermissionYamlServicePropertyTest {

    private static final Long CONNECTION_ID = 1L;
    private static final List<String> EMAILS = List.of("alice@example.com", "bob@example.com");
    private static final List<Long> USER_IDS = List.of(1L, 2L);
    private static final List<String> TABLES = List.of("products", "orders");

    private record Entry(int principalIndex, String tableName, PrimaryPermission primary, boolean create,
                          boolean delete) {
    }

    @Provide
    Arbitrary<List<Entry>> distinctEntries() {
        Arbitrary<Entry> entryArbitrary = Combinators.combine(
                Arbitraries.integers().between(0, EMAILS.size() - 1),
                Arbitraries.of(TABLES),
                Arbitraries.of(PrimaryPermission.class),
                Arbitraries.of(true, false),
                Arbitraries.of(true, false)
        ).as(Entry::new);
        return entryArbitrary.list().ofMinSize(0).ofMaxSize(4).map(list -> {
            List<Entry> distinct = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();
            for (Entry entry : list) {
                String key = entry.principalIndex() + ":" + entry.tableName();
                if (seenKeys.add(key)) {
                    distinct.add(entry);
                }
            }
            return distinct;
        });
    }

    private static User sampleUser(int index) {
        User user = new User(EMAILS.get(index), "hash", "テストユーザ", Language.ja, UserStatus.APPROVED, Role.USER,
                Instant.now(), Instant.now(), null);
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, USER_IDS.get(index));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    /**
     * ラウンドトリップ: 重複のない任意の権限設定状態について、エクスポート→インポート→
     * 再エクスポートした結果は、最初のエクスポート結果と（プリンシパル・リソース・権限値の
     * 集合として）一致する。
     */
    @Property
    boolean roundTrip_exportImportExportProducesSameEntrySet(@ForAll("distinctEntries") List<Entry> entries)
            throws Exception {
        List<AccessPermission> backingStore = new ArrayList<>();
        for (Entry entry : entries) {
            backingStore.add(new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_IDS.get(entry.principalIndex()),
                    "public", entry.tableName(), null, entry.primary(), entry.create(), entry.delete(),
                    Instant.now(), 1L));
        }

        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        when(accessPermissionRepository.findAllByConnectionId(CONNECTION_ID)).thenAnswer(inv -> List.copyOf(backingStore));
        org.mockito.Mockito.doAnswer(inv -> {
            backingStore.clear();
            return null;
        }).when(accessPermissionRepository).deleteAll(org.mockito.ArgumentMatchers.<Iterable<AccessPermission>>any());
        when(accessPermissionRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            AccessPermission saved = inv.getArgument(0);
            backingStore.add(saved);
            return saved;
        });
        for (int i = 0; i < EMAILS.size(); i++) {
            when(userRepository.findByEmail(EMAILS.get(i))).thenReturn(Optional.of(sampleUser(i)));
            when(userRepository.findById(USER_IDS.get(i))).thenReturn(Optional.of(sampleUser(i)));
        }

        PermissionYamlService service = new PermissionYamlService(accessPermissionRepository, userRepository,
                groupRepository, auditEventPublisher);

        String firstExport = service.exportToYaml(CONNECTION_ID, 1L);
        service.importFromYaml(CONNECTION_ID, firstExport, 1L);
        String secondExport = service.exportToYaml(CONNECTION_ID, 1L);

        return entrySetOf(firstExport).equals(entrySetOf(secondExport));
    }

    private Set<PermissionYamlEntry> entrySetOf(String yaml) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        PermissionYamlDocument document = mapper.readValue(yaml, PermissionYamlDocument.class);
        return new HashSet<>(document.permissions());
    }

    /**
     * 重複拒否の原子性: 重複エントリを含む任意のYAMLをインポートしようとした場合、
     * インポート全体が拒否され、対象接続の既存権限設定は一切変更されない。
     */
    @Property
    boolean duplicateRejection_leavesExistingPermissionsUnchanged(@ForAll("distinctEntries") List<Entry> entries) {
        if (entries.isEmpty()) {
            return true;
        }
        List<AccessPermission> backingStore = new ArrayList<>();
        AccessPermission existing = new AccessPermission(CONNECTION_ID, PrincipalType.USER, USER_IDS.get(0), "public",
                "existing_table", null, PrimaryPermission.READ, false, false, Instant.now(), 1L);
        backingStore.add(existing);

        AccessPermissionRepository accessPermissionRepository = mock(AccessPermissionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        when(accessPermissionRepository.findAllByConnectionId(CONNECTION_ID)).thenAnswer(inv -> List.copyOf(backingStore));
        for (int i = 0; i < EMAILS.size(); i++) {
            when(userRepository.findByEmail(EMAILS.get(i))).thenReturn(Optional.of(sampleUser(i)));
        }

        Entry duplicateSource = entries.get(0);
        String yaml = buildYamlWithDuplicate(duplicateSource);

        PermissionYamlService service = new PermissionYamlService(accessPermissionRepository, userRepository,
                groupRepository, auditEventPublisher);

        boolean rejected = false;
        try {
            service.importFromYaml(CONNECTION_ID, yaml, 1L);
        } catch (PermissionYamlImportRejectedException e) {
            rejected = true;
        }

        return rejected && backingStore.size() == 1 && backingStore.get(0) == existing;
    }

    private String buildYamlWithDuplicate(Entry entry) {
        String email = EMAILS.get(entry.principalIndex());
        String entryYaml = """
                  - principalType: "USER"
                    principal: "%s"
                    schemaName: "public"
                    tableName: "%s"
                    columnName: null
                    primaryPermission: "%s"
                    createPermission: %s
                    deletePermission: %s
                """.formatted(email, entry.tableName(), entry.primary(), entry.create(), entry.delete());
        return "connectionId: 1\npermissions:\n" + entryYaml + entryYaml;
    }
}

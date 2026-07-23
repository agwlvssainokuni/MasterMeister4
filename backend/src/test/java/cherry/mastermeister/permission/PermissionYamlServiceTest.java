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
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.exception.PermissionYamlImportRejectedException;
import cherry.mastermeister.group.entity.Group;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionYamlServiceTest {

    private static final Long CONNECTION_ID = 1L;

    @Mock
    private AccessPermissionRepository accessPermissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private PermissionYamlService service;

    @BeforeEach
    void setUp() {
        service = new PermissionYamlService(accessPermissionRepository, userRepository, groupRepository,
                auditEventPublisher);
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static User sampleUser(Long id, String email) {
        User user = new User(email, "hash", "テストユーザ", Language.ja, UserStatus.APPROVED, Role.USER, Instant.now(),
                Instant.now(), null);
        setId(user, id);
        return user;
    }

    @Test
    void exportToYaml_includesResolvedPrincipalIdentifiers() {
        when(accessPermissionRepository.findAllByConnectionId(CONNECTION_ID)).thenReturn(List.of(
                new AccessPermission(CONNECTION_ID, PrincipalType.USER, 42L, "public", "products", null,
                        PrimaryPermission.READ, true, false, Instant.now(), 1L)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser(42L, "alice@example.com")));

        String yaml = service.exportToYaml(CONNECTION_ID, 1L);

        assertThat(yaml).contains("alice@example.com").contains("products").contains("READ");
        verify(auditEventPublisher, times(1)).publish(any());
    }

    @Test
    void importFromYaml_rejectsUnresolvedPrincipal() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        String yaml = """
                connectionId: 1
                permissions:
                  - principalType: "USER"
                    principal: "unknown@example.com"
                    schemaName: "public"
                    tableName: "products"
                    columnName: null
                    primaryPermission: "READ"
                    createPermission: false
                    deletePermission: false
                """;

        assertThatThrownBy(() -> service.importFromYaml(CONNECTION_ID, yaml, 1L))
                .isInstanceOf(PermissionYamlImportRejectedException.class);

        verify(accessPermissionRepository, never()).deleteAll(any());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().resultStatus()).isEqualTo(ResultStatus.FAILURE);
    }

    @Test
    void importFromYaml_rejectsDuplicateEntries() {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(sampleUser(42L, "alice@example.com")));
        String yaml = """
                connectionId: 1
                permissions:
                  - principalType: "USER"
                    principal: "alice@example.com"
                    schemaName: "public"
                    tableName: "products"
                    columnName: null
                    primaryPermission: "READ"
                    createPermission: false
                    deletePermission: false
                  - principalType: "USER"
                    principal: "alice@example.com"
                    schemaName: "public"
                    tableName: "products"
                    columnName: null
                    primaryPermission: "UPDATE"
                    createPermission: false
                    deletePermission: false
                """;

        assertThatThrownBy(() -> service.importFromYaml(CONNECTION_ID, yaml, 1L))
                .isInstanceOf(PermissionYamlImportRejectedException.class);

        verify(accessPermissionRepository, never()).deleteAll(any());
    }

    @Test
    void importFromYaml_replacesExistingPermissionsOnSuccess() {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(sampleUser(42L, "alice@example.com")));
        when(accessPermissionRepository.findAllByConnectionId(CONNECTION_ID)).thenReturn(List.of(
                new AccessPermission(CONNECTION_ID, PrincipalType.USER, 999L, "public", "old_table", null,
                        PrimaryPermission.READ, false, false, Instant.now(), 1L)));
        String yaml = """
                connectionId: 1
                permissions:
                  - principalType: "USER"
                    principal: "alice@example.com"
                    schemaName: "public"
                    tableName: "products"
                    columnName: null
                    primaryPermission: "READ"
                    createPermission: false
                    deletePermission: false
                """;

        service.importFromYaml(CONNECTION_ID, yaml, 1L);

        verify(accessPermissionRepository, times(1)).deleteAll(any());
        verify(accessPermissionRepository, times(1)).save(any());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().resultStatus()).isEqualTo(ResultStatus.SUCCESS);
    }

    @Test
    void importFromYaml_rejectsMalformedYaml() {
        assertThatThrownBy(() -> service.importFromYaml(CONNECTION_ID, "not: [valid: yaml: structure", 1L))
                .isInstanceOf(PermissionYamlImportRejectedException.class);
    }

    @Test
    void importFromYaml_resolvesGroupPrincipal() {
        Group group = new Group("営業", Instant.now());
        setId(group, 5L);
        when(groupRepository.findByName("営業")).thenReturn(Optional.of(group));
        String yaml = """
                connectionId: 1
                permissions:
                  - principalType: "GROUP"
                    principal: "営業"
                    schemaName: "public"
                    tableName: null
                    columnName: null
                    primaryPermission: "READ"
                    createPermission: false
                    deletePermission: false
                """;

        service.importFromYaml(CONNECTION_ID, yaml, 1L);

        ArgumentCaptor<AccessPermission> captor = ArgumentCaptor.forClass(AccessPermission.class);
        verify(accessPermissionRepository).save(captor.capture());
        assertThat(captor.getValue().getPrincipalId()).isEqualTo(5L);
    }
}

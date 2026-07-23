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

package cherry.mastermeister.group;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.common.exception.GroupMembershipDuplicateException;
import cherry.mastermeister.common.exception.GroupNameDuplicateException;
import cherry.mastermeister.common.exception.GroupNotFoundException;
import cherry.mastermeister.common.exception.UserNotFoundException;
import cherry.mastermeister.group.entity.Group;
import cherry.mastermeister.group.entity.GroupMembership;
import cherry.mastermeister.group.repository.GroupRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private AccessPermissionRepository accessPermissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, accessPermissionRepository, userRepository,
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

    @Test
    void createGroup_rejectsWhenNameAlreadyExists() {
        when(groupRepository.findByName("営業")).thenReturn(Optional.of(new Group("営業", Instant.now())));

        assertThatThrownBy(() -> service.createGroup("営業", 1L)).isInstanceOf(GroupNameDuplicateException.class);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void createGroup_savesAndPublishesAuditEvent() {
        when(groupRepository.findByName("経理")).thenReturn(Optional.empty());
        when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Group created = service.createGroup("経理", 1L);

        assertThat(created.getName()).isEqualTo("経理");
        verify(auditEventPublisher, times(1)).publish(any());
    }

    @Test
    void renameGroup_rejectsWhenGroupNotFound() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renameGroup(99L, "新名称", 1L)).isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void renameGroup_allowsKeepingSameName() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.findByName("開発")).thenReturn(Optional.of(group));

        service.renameGroup(1L, "開発", 1L);

        assertThat(group.getName()).isEqualTo("開発");
    }

    @Test
    void renameGroup_rejectsWhenNewNameUsedByAnotherGroup() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        Group other = new Group("経理", Instant.now());
        setId(other, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.findByName("経理")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.renameGroup(1L, "経理", 1L))
                .isInstanceOf(GroupNameDuplicateException.class);
    }

    @Test
    void deleteGroup_deletesAccessPermissionsBeforeGroup() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        service.deleteGroup(1L, 1L);

        verify(accessPermissionRepository, times(1))
                .deleteAllByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, 1L);
        verify(groupRepository, times(1)).delete(group);
    }

    @Test
    void addUserToGroup_rejectsWhenUserNotFound() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addUserToGroup(1L, 42L, 1L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void addUserToGroup_rejectsDuplicateMembership() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        group.addMembership(new GroupMembership(42L));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser(42L)));

        assertThatThrownBy(() -> service.addUserToGroup(1L, 42L, 1L))
                .isInstanceOf(GroupMembershipDuplicateException.class);
    }

    @Test
    void addUserToGroup_addsNewMembership() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser(42L)));

        service.addUserToGroup(1L, 42L, 1L);

        assertThat(group.getMemberships()).hasSize(1);
        verify(auditEventPublisher, times(1)).publish(any());
    }

    @Test
    void removeUserFromGroup_isIdempotentWhenNotAMember() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        service.removeUserFromGroup(1L, 999L, 1L);

        assertThat(group.getMemberships()).isEmpty();
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void removeUserFromGroup_removesExistingMembership() {
        Group group = new Group("開発", Instant.now());
        setId(group, 1L);
        group.addMembership(new GroupMembership(42L));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser(42L)));

        service.removeUserFromGroup(1L, 42L, 1L);

        assertThat(group.getMemberships()).isEmpty();
        verify(auditEventPublisher, times(1)).publish(any());
    }

    private static User sampleUser(Long id) {
        User user = new User("user" + id + "@example.com", "hash", "テストユーザ", Language.ja, UserStatus.APPROVED,
                Role.USER, Instant.now(), Instant.now(), null);
        setId(user, id);
        return user;
    }
}

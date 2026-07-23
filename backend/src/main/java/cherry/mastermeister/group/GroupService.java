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
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.exception.GroupMembershipDuplicateException;
import cherry.mastermeister.common.exception.GroupNameDuplicateException;
import cherry.mastermeister.common.exception.GroupNotFoundException;
import cherry.mastermeister.common.exception.UserNotFoundException;
import cherry.mastermeister.group.entity.Group;
import cherry.mastermeister.group.entity.GroupMembership;
import cherry.mastermeister.group.repository.GroupRepository;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * COMP-10の一部（GroupService、AccessControlServiceから分割）。business-logic-model.md §4。
 * BR-ACCESS-11・BR-ACCESS-12。全mutationメソッドは実効権限キャッシュへ影響するため
 * {@code @CacheEvict(allEntries = true)}を付与する（nfr-design-patterns.md §2.1）。
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final AccessPermissionRepository accessPermissionRepository;
    private final UserRepository userRepository;
    private final AuditEventPublisher auditEventPublisher;

    public GroupService(GroupRepository groupRepository, AccessPermissionRepository accessPermissionRepository,
                         UserRepository userRepository, AuditEventPublisher auditEventPublisher) {
        this.groupRepository = groupRepository;
        this.accessPermissionRepository = accessPermissionRepository;
        this.userRepository = userRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Group> listGroups() {
        return groupRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> listMembers(Long groupId) {
        Group group = findOrThrow(groupId);
        return group.getMemberships().stream()
                .map(membership -> userRepository.findById(membership.getUserId()).orElseThrow(UserNotFoundException::new))
                .toList();
    }

    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public Group createGroup(String name, Long createdBy) {
        assertNameNotDuplicate(name, null);
        Group group = groupRepository.save(new Group(name, Instant.now()));
        auditEventPublisher.publish(new AuditEvent(Instant.now(), createdBy, null,
                AuditEventType.GROUP_CREATED, name, ResultStatus.SUCCESS, null));
        return group;
    }

    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public Group renameGroup(Long groupId, String newName, Long updatedBy) {
        Group group = findOrThrow(groupId);
        String oldName = group.getName();
        assertNameNotDuplicate(newName, groupId);
        group.rename(newName);
        auditEventPublisher.publish(new AuditEvent(Instant.now(), updatedBy, null,
                AuditEventType.GROUP_RENAMED, newName, ResultStatus.SUCCESS, oldName));
        return group;
    }

    /**
     * BR-ACCESS-11: GroupMembershipはGroupへの@OneToMany(cascade=ALL, orphanRemoval=true)で
     * DBレベルでカスケード削除されるが、principalId多態参照であるAccessPermissionはDB外部キーを
     * 持たないためアプリケーション層で明示的に削除する（nfr-design-patterns.md §1.2）。
     */
    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public void deleteGroup(Long groupId, Long deletedBy) {
        Group group = findOrThrow(groupId);
        String name = group.getName();
        accessPermissionRepository.deleteAllByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, groupId);
        groupRepository.delete(group);
        auditEventPublisher.publish(new AuditEvent(Instant.now(), deletedBy, null,
                AuditEventType.GROUP_DELETED, name, ResultStatus.SUCCESS, null));
    }

    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public void addUserToGroup(Long groupId, Long userId, Long addedBy) {
        Group group = findOrThrow(groupId);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        boolean alreadyMember = group.getMemberships().stream().anyMatch(m -> m.getUserId().equals(userId));
        if (alreadyMember) {
            throw new GroupMembershipDuplicateException();
        }
        group.addMembership(new GroupMembership(userId));
        auditEventPublisher.publish(new AuditEvent(Instant.now(), addedBy, null,
                AuditEventType.GROUP_MEMBER_ADDED, group.getName(), ResultStatus.SUCCESS, user.getEmail()));
    }

    /**
     * 対象ユーザが所属していない場合は何もしない（冪等）。
     */
    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public void removeUserFromGroup(Long groupId, Long userId, Long removedBy) {
        Group group = findOrThrow(groupId);
        group.getMemberships().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .ifPresent(membership -> {
                    group.removeMembership(membership);
                    userRepository.findById(userId).ifPresent(user -> auditEventPublisher.publish(
                            new AuditEvent(Instant.now(), removedBy, null, AuditEventType.GROUP_MEMBER_REMOVED,
                                    group.getName(), ResultStatus.SUCCESS, user.getEmail())));
                });
    }

    private void assertNameNotDuplicate(String name, Long excludingGroupId) {
        groupRepository.findByName(name).ifPresent(existing -> {
            if (excludingGroupId == null || !existing.getId().equals(excludingGroupId)) {
                throw new GroupNameDuplicateException();
            }
        });
    }

    private Group findOrThrow(Long groupId) {
        return groupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);
    }
}

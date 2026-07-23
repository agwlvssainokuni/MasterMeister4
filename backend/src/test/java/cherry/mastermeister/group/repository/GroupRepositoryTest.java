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

package cherry.mastermeister.group.repository;

import cherry.mastermeister.group.entity.Group;
import cherry.mastermeister.group.entity.GroupMembership;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class GroupRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMembershipRepository groupMembershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long persistUser(String email) {
        Instant now = Instant.now();
        User user = new User(email, "hash", "テストユーザ", Language.ja, UserStatus.APPROVED, Role.USER, now, now, null);
        return userRepository.saveAndFlush(user).getId();
    }

    @Test
    void save_persistsCascadedMemberships() {
        Long userId = persistUser("alice@example.com");
        Group group = new Group("営業", Instant.now());
        group.addMembership(new GroupMembership(userId));

        groupRepository.saveAndFlush(group);
        entityManager.clear();

        Group reloaded = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(reloaded.getMemberships()).hasSize(1);
        assertThat(reloaded.getMemberships().get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void findByName_returnsMatchingGroup() {
        groupRepository.saveAndFlush(new Group("経理", Instant.now()));

        assertThat(groupRepository.findByName("経理")).isPresent();
        assertThat(groupRepository.findByName("存在しない")).isEmpty();
    }

    @Test
    void save_rejectsDuplicateName() {
        groupRepository.saveAndFlush(new Group("重複グループ", Instant.now()));

        assertThatThrownBy(() -> groupRepository.saveAndFlush(new Group("重複グループ", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteById_cascadesMemberships() {
        Long userId = persistUser("bob@example.com");
        Group group = new Group("開発", Instant.now());
        group.addMembership(new GroupMembership(userId));
        groupRepository.saveAndFlush(group);
        Long groupId = group.getId();

        groupRepository.deleteById(groupId);
        groupRepository.flush();
        entityManager.clear();

        assertThat(groupRepository.findById(groupId)).isEmpty();
        assertThat(groupMembershipRepository.findAllByUserId(userId)).isEmpty();
    }

    @Test
    void findAllByUserId_returnsAllMembershipsAcrossGroups() {
        Long userId = persistUser("carol@example.com");
        Group group1 = new Group("グループ1", Instant.now());
        group1.addMembership(new GroupMembership(userId));
        groupRepository.saveAndFlush(group1);
        Group group2 = new Group("グループ2", Instant.now());
        group2.addMembership(new GroupMembership(userId));
        groupRepository.saveAndFlush(group2);

        assertThat(groupMembershipRepository.findAllByUserId(userId)).hasSize(2);
    }
}

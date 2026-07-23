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

package cherry.mastermeister.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * domain-entities.md §3。userIdは{@code cherry.mastermeister.registration.entity.User}を
 * 指すが、registrationパッケージへの直接依存を避けるためIDのみ保持する。
 */
@Entity
@Table(name = "group_membership")
public class GroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    protected GroupMembership() {
        // JPA
    }

    public GroupMembership(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return group.getId();
    }

    public Long getUserId() {
        return userId;
    }

    void assignGroup(Group group) {
        this.group = group;
    }
}

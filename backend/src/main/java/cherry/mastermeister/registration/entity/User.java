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

package cherry.mastermeister.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * domain-entities.md §1。emailは全ステータス共通で一意（BR-REG-06）。
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 10)
    private Language preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private Long statusChangedBy;

    protected User() {
        // JPA
    }

    public User(String email, String passwordHash, String fullName, Language preferredLanguage,
                UserStatus status, Role role, Instant createdAt, Instant statusChangedAt, Long statusChangedBy) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.preferredLanguage = preferredLanguage;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
        this.statusChangedAt = statusChangedAt;
        this.statusChangedBy = statusChangedBy;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Language getPreferredLanguage() {
        return preferredLanguage;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Long getStatusChangedBy() {
        return statusChangedBy;
    }

    /**
     * BR-REG-01の状態遷移。遷移可否の判定は呼び出し元（UserRegistrationService）が行う。
     */
    public void changeStatus(UserStatus newStatus, Long changedBy, Instant changedAt) {
        this.status = newStatus;
        this.statusChangedBy = changedBy;
        this.statusChangedAt = changedAt;
    }
}

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

package cherry.mastermeister.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * domain-entities.md §4。BR-LOGIN-01〜03。
 */
@Entity
@Table(name = "login_attempt_state")
public class LoginAttemptState {

    @Id
    private String email;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    protected LoginAttemptState() {
        // JPA
    }

    public LoginAttemptState(String email) {
        this.email = email;
        this.failureCount = 0;
    }

    public String getEmail() {
        return email;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void recordFailure(Instant now, int maxFailures, java.time.Duration lockDuration) {
        this.failureCount++;
        this.lastFailureAt = now;
        if (this.failureCount >= maxFailures) {
            this.lockedUntil = now.plus(lockDuration);
        }
    }

    public void reset() {
        this.failureCount = 0;
        this.lockedUntil = null;
        this.lastFailureAt = null;
    }
}

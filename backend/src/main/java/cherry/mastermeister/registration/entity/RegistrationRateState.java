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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * domain-entities.md §5、BR-REG-07。登録開始エンドポイントのレート制限。
 */
@Entity
@Table(name = "registration_rate_state")
public class RegistrationRateState {

    @Id
    private String email;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "window_start_at", nullable = false)
    private Instant windowStartAt;

    protected RegistrationRateState() {
        // JPA
    }

    public RegistrationRateState(String email, int requestCount, Instant windowStartAt) {
        this.email = email;
        this.requestCount = requestCount;
        this.windowStartAt = windowStartAt;
    }

    public String getEmail() {
        return email;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public Instant getWindowStartAt() {
        return windowStartAt;
    }

    public void resetWindow(Instant now) {
        this.requestCount = 0;
        this.windowStartAt = now;
    }

    public void increment() {
        this.requestCount++;
    }
}

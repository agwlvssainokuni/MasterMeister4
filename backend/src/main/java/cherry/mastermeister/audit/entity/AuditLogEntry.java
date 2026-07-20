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

package cherry.mastermeister.audit.entity;

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
 * domain-entities.md §6。BR-AUDIT-01〜03。userId/targetResource/detailの意味は
 * イベント種別によって異なる（domain-entities.md §6.1参照）。
 */
@Entity
@Table(name = "audit_log_entry")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "connection_id")
    private Long connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "target_resource")
    private String targetResource;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 20)
    private ResultStatus resultStatus;

    @Column(length = 2000)
    private String detail;

    protected AuditLogEntry() {
        // JPA
    }

    public AuditLogEntry(Instant occurredAt, Long userId, Long connectionId, AuditEventType eventType,
                          String targetResource, ResultStatus resultStatus, String detail) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.connectionId = connectionId;
        this.eventType = eventType;
        this.targetResource = targetResource;
        this.resultStatus = resultStatus;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getTargetResource() {
        return targetResource;
    }

    public ResultStatus getResultStatus() {
        return resultStatus;
    }

    public String getDetail() {
        return detail;
    }
}

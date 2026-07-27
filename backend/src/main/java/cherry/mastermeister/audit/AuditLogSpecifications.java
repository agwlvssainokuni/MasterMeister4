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

package cherry.mastermeister.audit;

import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.AuditLogEntry;
import cherry.mastermeister.audit.entity.ResultStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * nfr-design-patterns.md §2.1。AuditLogEntryの動的絞込条件を組み立てる
 * 静的ファクトリメソッド集（UNIT-08のQueryHistorySpecificationsと同じパターン）。
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLogEntry> occurredAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    public static Specification<AuditLogEntry> occurredAtTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to);
    }

    public static Specification<AuditLogEntry> eventTypeEquals(AuditEventType eventType) {
        return (root, query, cb) -> cb.equal(root.get("eventType"), eventType);
    }

    public static Specification<AuditLogEntry> userIdEquals(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<AuditLogEntry> connectionIdEquals(Long connectionId) {
        return (root, query, cb) -> cb.equal(root.get("connectionId"), connectionId);
    }

    public static Specification<AuditLogEntry> resultStatusEquals(ResultStatus resultStatus) {
        return (root, query, cb) -> cb.equal(root.get("resultStatus"), resultStatus);
    }
}

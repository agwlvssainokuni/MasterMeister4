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

import cherry.mastermeister.audit.entity.AuditLogEntry;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.audit.repository.AuditLogEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * COMP-18（記録機能のみ）。BR-AUDIT-01: {@code @TransactionalEventListener(phase = AFTER_COMMIT)}で
 * イベントを受信し、{@code @Transactional(propagation = REQUIRES_NEW)}で別トランザクションとして記録する。
 * 業務処理の成功可否と監査ログの記録は独立して扱う。
 */
@Service
public class AuditLogService {

    private final AuditLogEntryRepository auditLogEntryRepository;

    public AuditLogService(AuditLogEntryRepository auditLogEntryRepository) {
        this.auditLogEntryRepository = auditLogEntryRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEvent(AuditEvent event) {
        AuditLogEntry entry = new AuditLogEntry(
                event.occurredAt(),
                event.userId(),
                event.connectionId(),
                event.eventType(),
                event.targetResource(),
                event.resultStatus(),
                event.detail()
        );
        auditLogEntryRepository.save(entry);
    }
}

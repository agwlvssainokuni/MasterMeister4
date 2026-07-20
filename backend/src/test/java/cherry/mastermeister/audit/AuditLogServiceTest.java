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
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.audit.repository.AuditLogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * COMP-18（記録機能のみ）。BR-AUDIT-01〜03。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogEntryRepository auditLogEntryRepository;

    @Test
    void onAuditEvent_persistsAllFieldsFromEvent() {
        AuditLogService service = new AuditLogService(auditLogEntryRepository);
        Instant now = Instant.now();
        AuditEvent event = new AuditEvent(now, 1L, null, AuditEventType.LOGIN, "taro@example.com",
                ResultStatus.SUCCESS, null);

        service.onAuditEvent(event);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogEntryRepository).save(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertThat(saved.getOccurredAt()).isEqualTo(now);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN);
        assertThat(saved.getTargetResource()).isEqualTo("taro@example.com");
        assertThat(saved.getResultStatus()).isEqualTo(ResultStatus.SUCCESS);
    }

    @Test
    void onAuditEvent_allowsNullUserId_forUnattributableEvents() {
        AuditLogService service = new AuditLogService(auditLogEntryRepository);
        AuditEvent event = new AuditEvent(Instant.now(), null, null, AuditEventType.LOGIN_FAILURE,
                "nobody@example.com", ResultStatus.FAILURE, null);

        service.onAuditEvent(event);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }
}

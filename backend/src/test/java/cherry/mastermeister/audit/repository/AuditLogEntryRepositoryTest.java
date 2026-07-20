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

package cherry.mastermeister.audit.repository;

import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.AuditLogEntry;
import cherry.mastermeister.audit.entity.ResultStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditLogEntryRepositoryTest {

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Test
    void save_andFindById_roundTrips() {
        AuditLogEntry entry = new AuditLogEntry(Instant.now(), 1L, null, AuditEventType.LOGIN,
                "taro@example.com", ResultStatus.SUCCESS, null);

        AuditLogEntry saved = auditLogEntryRepository.saveAndFlush(entry);

        assertThat(auditLogEntryRepository.findById(saved.getId())).isPresent();
    }
}

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
import cherry.mastermeister.audit.repository.AuditLogEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static cherry.mastermeister.audit.AuditLogSpecifications.connectionIdEquals;
import static cherry.mastermeister.audit.AuditLogSpecifications.eventTypeEquals;
import static cherry.mastermeister.audit.AuditLogSpecifications.occurredAtFrom;
import static cherry.mastermeister.audit.AuditLogSpecifications.occurredAtTo;
import static cherry.mastermeister.audit.AuditLogSpecifications.resultStatusEquals;
import static cherry.mastermeister.audit.AuditLogSpecifications.userIdEquals;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * nfr-design-patterns.md §2.1。各ファクトリメソッド単体、および複数条件の組み合わせでの絞込結果を確認する。
 */
@DataJpaTest
class AuditLogSpecificationsTest {

    @Autowired
    private AuditLogEntryRepository repository;

    @Test
    void occurredAtRange_filtersByDateRange() {
        Instant base = Instant.now();
        repository.saveAndFlush(entry(base.minus(2, ChronoUnit.DAYS), 1L, 100L, AuditEventType.LOGIN));
        repository.saveAndFlush(entry(base, 1L, 100L, AuditEventType.LOGIN));
        repository.saveAndFlush(entry(base.plus(2, ChronoUnit.DAYS), 1L, 100L, AuditEventType.LOGIN));

        var result = repository.findAll(
                Specification.where(occurredAtFrom(base.minus(1, ChronoUnit.DAYS)))
                        .and(occurredAtTo(base.plus(1, ChronoUnit.DAYS))),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void eventTypeEquals_filtersByEventType() {
        Instant now = Instant.now();
        repository.saveAndFlush(entry(now, 1L, 100L, AuditEventType.LOGIN));
        repository.saveAndFlush(entry(now, 1L, 100L, AuditEventType.LOGOUT));

        var result = repository.findAll(Specification.where(eventTypeEquals(AuditEventType.LOGOUT)),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(e -> e.getEventType() == AuditEventType.LOGOUT);
    }

    @Test
    void userIdEquals_filtersByUser() {
        Instant now = Instant.now();
        repository.saveAndFlush(entry(now, 1L, 100L, AuditEventType.LOGIN));
        repository.saveAndFlush(entry(now, 2L, 100L, AuditEventType.LOGIN));

        var result = repository.findAll(Specification.where(userIdEquals(1L)), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(e -> e.getUserId().equals(1L));
    }

    @Test
    void connectionIdEquals_filtersByConnection() {
        Instant now = Instant.now();
        repository.saveAndFlush(entry(now, 1L, 100L, AuditEventType.QUERY_EXECUTED));
        repository.saveAndFlush(entry(now, 1L, 200L, AuditEventType.QUERY_EXECUTED));

        var result = repository.findAll(Specification.where(connectionIdEquals(100L)), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(e -> e.getConnectionId().equals(100L));
    }

    @Test
    void resultStatusEquals_filtersByResultStatus() {
        Instant now = Instant.now();
        repository.saveAndFlush(new AuditLogEntry(now, 1L, null, AuditEventType.LOGIN_FAILURE, null,
                ResultStatus.FAILURE, null));
        repository.saveAndFlush(entry(now, 1L, null, AuditEventType.LOGIN));

        var result = repository.findAll(Specification.where(resultStatusEquals(ResultStatus.FAILURE)),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(e -> e.getResultStatus() == ResultStatus.FAILURE);
    }

    @Test
    void combinedConditions_areAndedTogether() {
        Instant now = Instant.now();
        repository.saveAndFlush(entry(now, 1L, 100L, AuditEventType.QUERY_EXECUTED));
        repository.saveAndFlush(entry(now, 2L, 100L, AuditEventType.QUERY_EXECUTED));
        repository.saveAndFlush(entry(now, 1L, 200L, AuditEventType.QUERY_EXECUTED));

        var result = repository.findAll(
                Specification.where(connectionIdEquals(100L)).and(userIdEquals(1L)), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getConnectionId()).isEqualTo(100L);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
    }

    private static AuditLogEntry entry(Instant occurredAt, Long userId, Long connectionId,
                                        AuditEventType eventType) {
        return new AuditLogEntry(occurredAt, userId, connectionId, eventType, null, ResultStatus.SUCCESS, null);
    }
}

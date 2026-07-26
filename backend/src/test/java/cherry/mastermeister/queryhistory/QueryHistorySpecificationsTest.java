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

package cherry.mastermeister.queryhistory;

import cherry.mastermeister.query.entity.QueryExecutionRecord;
import cherry.mastermeister.query.repository.QueryExecutionRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.connectionIdEquals;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.executedAtFrom;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.executedAtTo;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.executedByEquals;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.schemaNameEquals;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.sqlContains;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * nfr-design-patterns.md §2.1。各ファクトリメソッド単体、および複数条件の組み合わせでの絞込結果を確認する。
 */
@DataJpaTest
class QueryHistorySpecificationsTest {

    @Autowired
    private QueryExecutionRecordRepository repository;

    @Test
    void connectionIdEquals_filtersByConnection() {
        Instant now = Instant.now();
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, now));
        repository.saveAndFlush(new QueryExecutionRecord(1L, 200L, "public", "SELECT 1", null, null, 1L, 5L, now));

        var result = repository.findAll(Specification.where(connectionIdEquals(100L)), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(r -> r.getConnectionId().equals(100L));
    }

    @Test
    void executedByEquals_filtersByExecutor() {
        Instant now = Instant.now();
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, now));
        repository.saveAndFlush(new QueryExecutionRecord(2L, 100L, "public", "SELECT 1", null, null, 1L, 5L, now));

        var result = repository.findAll(Specification.where(executedByEquals(1L)), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(r -> r.getExecutedBy().equals(1L));
    }

    @Test
    void executedAtRange_filtersByDateRange() {
        Instant base = Instant.now();
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L,
                base.minus(2, ChronoUnit.DAYS)));
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, base));
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L,
                base.plus(2, ChronoUnit.DAYS)));

        var result = repository.findAll(
                Specification.where(executedAtFrom(base.minus(1, ChronoUnit.DAYS)))
                        .and(executedAtTo(base.plus(1, ChronoUnit.DAYS))),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void schemaNameEquals_filtersBySchema() {
        Instant now = Instant.now();
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, now));
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "secret", "SELECT 1", null, null, 1L, 5L, now));

        var result = repository.findAll(Specification.where(schemaNameEquals("secret")), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1).allMatch(r -> r.getSchemaName().equals("secret"));
    }

    @Test
    void sqlContains_filtersByPartialMatch() {
        Instant now = Instant.now();
        repository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT * FROM items", null, null, 1L, 5L, now));
        repository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT * FROM sales", null, null, 1L, 5L, now));

        var result = repository.findAll(Specification.where(sqlContains("items")), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void combinedConditions_areAndedTogether() {
        Instant now = Instant.now();
        repository.saveAndFlush(new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, now));
        repository.saveAndFlush(new QueryExecutionRecord(2L, 100L, "public", "SELECT 1", null, null, 1L, 5L, now));
        repository.saveAndFlush(new QueryExecutionRecord(1L, 200L, "public", "SELECT 1", null, null, 1L, 5L, now));

        var result = repository.findAll(
                Specification.where(connectionIdEquals(100L)).and(executedByEquals(1L)), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getConnectionId()).isEqualTo(100L);
        assertThat(result.getContent().get(0).getExecutedBy()).isEqualTo(1L);
    }
}

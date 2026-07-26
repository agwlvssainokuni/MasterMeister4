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

package cherry.mastermeister.query.repository;

import cherry.mastermeister.query.entity.QueryExecutionRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QueryExecutionRecordRepositoryTest {

    @Autowired
    private QueryExecutionRecordRepository queryExecutionRecordRepository;

    @Test
    void save_persistsAdHocExecution_withNullSavedQueryId() {
        QueryExecutionRecord record = queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));

        QueryExecutionRecord reloaded = queryExecutionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getSavedQueryId()).isNull();
        assertThat(reloaded.getRowCount()).isEqualTo(1L);
        assertThat(reloaded.getDurationMillis()).isEqualTo(5L);
    }

    @Test
    void save_persistsSavedQueryExecution_withParams() {
        QueryExecutionRecord record = queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT * FROM t WHERE id = :id",
                        "{\"id\":\"1\"}", 42L, 1L, 5L, Instant.now()));

        QueryExecutionRecord reloaded = queryExecutionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getSavedQueryId()).isEqualTo(42L);
        assertThat(reloaded.getParams()).isEqualTo("{\"id\":\"1\"}");
    }

    /**
     * UNIT-08 logical-components.md §2。DISTINCT取得系メソッド（BR-QUERYHISTORY-10・11）。
     */
    @Test
    void findDistinctConnectionIdByExecutedBy_returnsOnlyOwnConnections() {
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 200L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(2L, 300L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));

        assertThat(queryExecutionRecordRepository.findDistinctConnectionIdByExecutedBy(1L))
                .containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void findDistinctConnectionId_returnsAllUsersConnections() {
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(2L, 300L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));

        assertThat(queryExecutionRecordRepository.findDistinctConnectionId())
                .containsExactlyInAnyOrder(100L, 300L);
    }

    @Test
    void findDistinctSchemaNameByConnectionIdAndExecutedBy_returnsOnlyOwnSchemas() {
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(2L, 100L, "secret", "SELECT 1", null, null, 1L, 5L, Instant.now()));

        assertThat(queryExecutionRecordRepository.findDistinctSchemaNameByConnectionIdAndExecutedBy(100L, 1L))
                .containsExactly("public");
    }

    @Test
    void findDistinctSchemaNameByConnectionId_returnsAllUsersSchemas() {
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(1L, 100L, "public", "SELECT 1", null, null, 1L, 5L, Instant.now()));
        queryExecutionRecordRepository.saveAndFlush(
                new QueryExecutionRecord(2L, 100L, "secret", "SELECT 1", null, null, 1L, 5L, Instant.now()));

        assertThat(queryExecutionRecordRepository.findDistinctSchemaNameByConnectionId(100L))
                .containsExactlyInAnyOrder("public", "secret");
    }
}

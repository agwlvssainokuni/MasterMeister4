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

import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.entity.Visibility;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SavedQueryRepositoryTest {

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long persistConnection(String displayName) {
        Instant now = Instant.now();
        RdbmsConnection connection = new RdbmsConnection(displayName, DbType.MYSQL, "localhost", 3306,
                "mastermeister", "root", "encrypted", 1, null, now, now);
        return rdbmsConnectionRepository.saveAndFlush(connection).getId();
    }

    @Test
    void save_persistsAllFields() {
        Long connectionId = persistConnection("接続1");
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.saveAndFlush(
                new SavedQuery(connectionId, "売上一覧", "SELECT * FROM sales", Visibility.PUBLIC, 1L, now));

        SavedQuery reloaded = savedQueryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getConnectionId()).isEqualTo(connectionId);
        assertThat(reloaded.getName()).isEqualTo("売上一覧");
        assertThat(reloaded.getSql()).isEqualTo("SELECT * FROM sales");
        assertThat(reloaded.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(reloaded.getCreatedBy()).isEqualTo(1L);
        assertThat(reloaded.isRetired()).isFalse();
    }

    @Test
    void findAllByConnectionId_returnsOnlyMatchingConnection() {
        Long connectionId1 = persistConnection("接続1");
        Long connectionId2 = persistConnection("接続2");
        Instant now = Instant.now();
        savedQueryRepository.saveAndFlush(
                new SavedQuery(connectionId1, "クエリA", "SELECT 1", Visibility.PUBLIC, 1L, now));
        savedQueryRepository.saveAndFlush(
                new SavedQuery(connectionId2, "クエリB", "SELECT 2", Visibility.PUBLIC, 1L, now));

        assertThat(savedQueryRepository.findAllByConnectionId(connectionId1)).hasSize(1)
                .allMatch(q -> q.getConnectionId().equals(connectionId1));
    }

    @Test
    void deleteConnection_cascadesSavedQuery() {
        Long connectionId = persistConnection("接続1");
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.saveAndFlush(
                new SavedQuery(connectionId, "クエリ", "SELECT 1", Visibility.PRIVATE, 1L, now));
        Long savedQueryId = saved.getId();

        rdbmsConnectionRepository.deleteById(connectionId);
        rdbmsConnectionRepository.flush();
        entityManager.clear();

        assertThat(savedQueryRepository.findById(savedQueryId)).isEmpty();
    }

    @Test
    void update_changesFieldsAndRetire() {
        Long connectionId = persistConnection("接続1");
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.saveAndFlush(
                new SavedQuery(connectionId, "クエリ", "SELECT 1", Visibility.PRIVATE, 1L, now));

        Instant updatedAt = now.plusSeconds(60);
        saved.update("改名クエリ", "SELECT 2", Visibility.PUBLIC, updatedAt);
        savedQueryRepository.saveAndFlush(saved);

        SavedQuery reloaded = savedQueryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("改名クエリ");
        assertThat(reloaded.getSql()).isEqualTo("SELECT 2");
        assertThat(reloaded.getVisibility()).isEqualTo(Visibility.PUBLIC);

        reloaded.retire(updatedAt.plusSeconds(60));
        savedQueryRepository.saveAndFlush(reloaded);

        assertThat(savedQueryRepository.findById(saved.getId()).orElseThrow().isRetired()).isTrue();
    }
}

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

package cherry.mastermeister.rdbmsconnection.repository;

import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RdbmsConnectionRepositoryTest {

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    private RdbmsConnection newConnection(String displayName) {
        Instant now = Instant.now();
        return new RdbmsConnection(displayName, DbType.MYSQL, "localhost", 3306, "mastermeister",
                null, "root", "encrypted", 1, null, now, now);
    }

    @Test
    void save_allowsDuplicateHostPortDatabase_perBrRdbms02() {
        rdbmsConnectionRepository.saveAndFlush(newConnection("接続1"));
        rdbmsConnectionRepository.saveAndFlush(newConnection("接続2"));

        assertThat(rdbmsConnectionRepository.findAll()).hasSize(2);
    }

    @Test
    void save_allowsDuplicateDisplayName_perReview() {
        rdbmsConnectionRepository.saveAndFlush(newConnection("同じ名前"));
        rdbmsConnectionRepository.saveAndFlush(newConnection("同じ名前"));

        assertThat(rdbmsConnectionRepository.findAll()).hasSize(2);
    }

    @Test
    void update_changesFieldsAndBumpsUpdatedAt() {
        RdbmsConnection saved = rdbmsConnectionRepository.saveAndFlush(newConnection("接続"));
        Instant updatedAt = Instant.now().plusSeconds(60);

        saved.update("更新後", DbType.POSTGRESQL, "otherhost", 5432, "otherdb", "public", "user2",
                "encrypted2", 2, "sslmode=require", updatedAt);
        rdbmsConnectionRepository.saveAndFlush(saved);

        RdbmsConnection reloaded = rdbmsConnectionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("更新後");
        assertThat(reloaded.getDbType()).isEqualTo(DbType.POSTGRESQL);
        assertThat(reloaded.getEncryptionKeyId()).isEqualTo(2);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void delete_removesConnection() {
        RdbmsConnection saved = rdbmsConnectionRepository.saveAndFlush(newConnection("削除対象"));

        rdbmsConnectionRepository.delete(saved);

        assertThat(rdbmsConnectionRepository.findById(saved.getId())).isEmpty();
    }
}

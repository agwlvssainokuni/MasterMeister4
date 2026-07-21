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

import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.NormalizedType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.entity.SchemaColumn;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;
import cherry.mastermeister.rdbmsconnection.entity.SchemaSnapshot;
import cherry.mastermeister.rdbmsconnection.entity.SchemaTable;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SchemaSnapshotRepositoryTest {

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    @Autowired
    private SchemaSnapshotRepository schemaSnapshotRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long persistConnection() {
        Instant now = Instant.now();
        RdbmsConnection connection = new RdbmsConnection("動作確認用", DbType.MYSQL, "localhost", 3306,
                "mastermeister", null, "root", "encrypted", 1, null, now, now);
        return rdbmsConnectionRepository.saveAndFlush(connection).getId();
    }

    private SchemaTable sampleTable() {
        SchemaTable table = new SchemaTable("categories", TableType.TABLE, null);
        table.addColumn(new SchemaColumn("category_id", 1, null, "INT", NormalizedType.NUMBER, false, null));
        table.addColumn(new SchemaColumn("category_name", 2, null, "VARCHAR(100)", NormalizedType.STRING, false,
                null));
        table.addConstraint(new SchemaConstraint(ConstraintType.PRIMARY_KEY, "pk_categories",
                List.of("category_id"), null, null));
        return table;
    }

    @Test
    void save_persistsCascadedTablesColumnsConstraints() {
        Long connectionId = persistConnection();
        SchemaSnapshot snapshot = new SchemaSnapshot(connectionId, Instant.now());
        snapshot.addTable(sampleTable());

        schemaSnapshotRepository.saveAndFlush(snapshot);

        SchemaSnapshot reloaded = schemaSnapshotRepository.findById(connectionId).orElseThrow();
        assertThat(reloaded.getTables()).hasSize(1);
        SchemaTable table = reloaded.getTables().get(0);
        assertThat(table.getColumns()).hasSize(2);
        assertThat(table.getConstraints()).hasSize(1);
        assertThat(table.getConstraints().get(0).getColumnNames()).containsExactly("category_id");
    }

    @Test
    void replace_deletesOldSnapshotAndCascadesChildren_perBrRdbms08() {
        Long connectionId = persistConnection();
        SchemaSnapshot first = new SchemaSnapshot(connectionId, Instant.now());
        first.addTable(sampleTable());
        schemaSnapshotRepository.saveAndFlush(first);

        schemaSnapshotRepository.deleteById(connectionId);
        schemaSnapshotRepository.flush();
        // 削除がDB(FKのON DELETE CASCADE)経由で反映されるため、Hibernateの
        // 第一階層キャッシュに残った古い管理エンティティをクリアしてから再取得する
        entityManager.clear();

        SchemaSnapshot replacement = new SchemaSnapshot(connectionId, Instant.now());
        replacement.addTable(new SchemaTable("products", TableType.TABLE, null));
        schemaSnapshotRepository.saveAndFlush(replacement);
        entityManager.clear();

        SchemaSnapshot reloaded = schemaSnapshotRepository.findById(connectionId).orElseThrow();
        assertThat(reloaded.getTables()).hasSize(1);
        assertThat(reloaded.getTables().get(0).getTableName()).isEqualTo("products");
    }

    @Test
    void deleteConnection_cascadesSnapshotAndChildren_perBrRdbms09() {
        Long connectionId = persistConnection();
        SchemaSnapshot snapshot = new SchemaSnapshot(connectionId, Instant.now());
        snapshot.addTable(sampleTable());
        schemaSnapshotRepository.saveAndFlush(snapshot);

        rdbmsConnectionRepository.deleteById(connectionId);
        rdbmsConnectionRepository.flush();
        entityManager.clear();

        assertThat(schemaSnapshotRepository.findById(connectionId)).isEmpty();
    }
}

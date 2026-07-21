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

package cherry.mastermeister.rdbmsconnection.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * domain-entities.md §2。BR-RDBMS-08: 全置換方式のため、1接続につき常に最新の1件のみ
 * 存在する。connectionIdを主キー兼外部キーとする（RdbmsConnection.idと同一値を
 * 手動採番、@GeneratedValueは使用しない）。
 */
@Entity
@Table(name = "schema_snapshot")
public class SchemaSnapshot {

    @Id
    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @OneToMany(mappedBy = "schemaSnapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SchemaTable> tables = new ArrayList<>();

    protected SchemaSnapshot() {
        // JPA
    }

    public SchemaSnapshot(Long connectionId, Instant importedAt) {
        this.connectionId = connectionId;
        this.importedAt = importedAt;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public List<SchemaTable> getTables() {
        return List.copyOf(tables);
    }

    public void addTable(SchemaTable table) {
        table.assignSnapshot(this);
        tables.add(table);
    }
}

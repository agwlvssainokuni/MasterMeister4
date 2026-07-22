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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * domain-entities.md §2.1。SchemaSnapshotに属するテーブル/ビュー。
 * schemaName: 1接続内に複数スキーマが存在しうる（UNIT-04 Functional Designにて、PostgreSQL/H2）
 * ため、どのスキーマに属するテーブルかを区別する属性を追加（訂正）。MySQL/MariaDBは
 * データベース＝スキーマの単位のため、常にdatabaseNameと同値を設定する。
 */
@Entity
@Table(name = "schema_table")
public class SchemaTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "connection_id", nullable = false)
    private SchemaSnapshot schemaSnapshot;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(name = "table_type", nullable = false, length = 20)
    private TableType tableType;

    @Column(length = 1000)
    private String comment;

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SchemaColumn> columns = new ArrayList<>();

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SchemaConstraint> constraints = new ArrayList<>();

    protected SchemaTable() {
        // JPA
    }

    public SchemaTable(String schemaName, String tableName, TableType tableType, String comment) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.tableType = tableType;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public TableType getTableType() {
        return tableType;
    }

    public String getComment() {
        return comment;
    }

    public List<SchemaColumn> getColumns() {
        return List.copyOf(columns);
    }

    public List<SchemaConstraint> getConstraints() {
        return List.copyOf(constraints);
    }

    public void addColumn(SchemaColumn column) {
        column.assignTable(this);
        columns.add(column);
    }

    public void addConstraint(SchemaConstraint constraint) {
        constraint.assignTable(this);
        constraints.add(constraint);
    }

    void assignSnapshot(SchemaSnapshot schemaSnapshot) {
        this.schemaSnapshot = schemaSnapshot;
    }
}

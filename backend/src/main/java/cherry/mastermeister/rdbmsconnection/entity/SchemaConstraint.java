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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.List;

/**
 * domain-entities.md §2.3。BR-RDBMS-06（Q3=C）: 主キー・外部キー・一意制約・インデックスの4種。
 * columnNames/referencedColumnsは複合キー対応のためカンマ区切り文字列として永続化する
 * （Code Generation時点のシンプルさ優先の実装判断）。
 */
@Entity
@Table(name = "schema_constraint")
public class SchemaConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    private SchemaTable table;

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", nullable = false, length = 20)
    private ConstraintType constraintType;

    @Column(name = "constraint_name", nullable = false)
    private String constraintName;

    @Column(name = "column_names", nullable = false, length = 1000)
    private String columnNamesRaw;

    @Column(name = "referenced_table")
    private String referencedTable;

    @Column(name = "referenced_columns", length = 1000)
    private String referencedColumnsRaw;

    protected SchemaConstraint() {
        // JPA
    }

    public SchemaConstraint(ConstraintType constraintType, String constraintName, List<String> columnNames,
                             String referencedTable, List<String> referencedColumns) {
        this.constraintType = constraintType;
        this.constraintName = constraintName;
        this.columnNamesRaw = String.join(",", columnNames);
        this.referencedTable = referencedTable;
        this.referencedColumnsRaw = referencedColumns == null ? null : String.join(",", referencedColumns);
    }

    public Long getId() {
        return id;
    }

    public ConstraintType getConstraintType() {
        return constraintType;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public List<String> getColumnNames() {
        return List.of(columnNamesRaw.split(","));
    }

    public String getReferencedTable() {
        return referencedTable;
    }

    public List<String> getReferencedColumns() {
        return referencedColumnsRaw == null ? List.of() : List.of(referencedColumnsRaw.split(","));
    }

    void assignTable(SchemaTable table) {
        this.table = table;
    }
}

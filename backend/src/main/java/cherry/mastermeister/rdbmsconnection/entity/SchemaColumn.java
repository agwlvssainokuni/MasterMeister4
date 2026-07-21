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

/**
 * domain-entities.md §2.2。BR-RDBMS-06（Q3=C, Q4=B）。
 */
@Entity
@Table(name = "schema_column")
public class SchemaColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    private SchemaTable table;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "ordinal_position", nullable = false)
    private int ordinalPosition;

    @Column(length = 1000)
    private String comment;

    @Column(name = "native_type", nullable = false)
    private String nativeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normalized_type", nullable = false, length = 20)
    private NormalizedType normalizedType;

    @Column(nullable = false)
    private boolean nullable;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    protected SchemaColumn() {
        // JPA
    }

    public SchemaColumn(String columnName, int ordinalPosition, String comment, String nativeType,
                         NormalizedType normalizedType, boolean nullable, String defaultValue) {
        this.columnName = columnName;
        this.ordinalPosition = ordinalPosition;
        this.comment = comment;
        this.nativeType = nativeType;
        this.normalizedType = normalizedType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
    }

    public Long getId() {
        return id;
    }

    public String getColumnName() {
        return columnName;
    }

    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    public String getComment() {
        return comment;
    }

    public String getNativeType() {
        return nativeType;
    }

    public NormalizedType getNormalizedType() {
        return normalizedType;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    void assignTable(SchemaTable table) {
        this.table = table;
    }
}

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

package cherry.mastermeister.query.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * domain-entities.md §1。BR-QUERY-05〜09。connectionIdは保存時点で固定し、以後変更不可
 * （スキーマは保持しない、Q11）。
 */
@Entity
@Table(name = "saved_query")
public class SavedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(nullable = false)
    private String sql;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private boolean retired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedQuery() {
        // JPA
    }

    public SavedQuery(Long connectionId, String name, String sql, Visibility visibility, Long createdBy,
                       Instant createdAt) {
        this.connectionId = connectionId;
        this.name = name;
        this.sql = sql;
        this.visibility = visibility;
        this.createdBy = createdBy;
        this.retired = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public String getName() {
        return name;
    }

    public String getSql() {
        return sql;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public boolean isRetired() {
        return retired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * BR-QUERY-07。作成者のみ呼び出し可能（呼び出し側で権限判定済みであること）。
     */
    public void update(String name, String sql, Visibility visibility, Instant updatedAt) {
        this.name = name;
        this.sql = sql;
        this.visibility = visibility;
        this.updatedAt = updatedAt;
    }

    /**
     * BR-QUERY-08。作成者のみ呼び出し可能（呼び出し側で権限判定済みであること）。
     * un-retireは提供しない。
     */
    public void retire(Instant updatedAt) {
        this.retired = true;
        this.updatedAt = updatedAt;
    }
}

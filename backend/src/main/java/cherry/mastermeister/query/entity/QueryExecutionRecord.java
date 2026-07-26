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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * domain-entities.md §2。BR-QUERY-10。クエリ実行1回分の記録（不変）。
 * ユーザ向け実行履歴の基礎データ（閲覧機能はUNIT-08が追加）。connection_id/saved_query_idは
 * audit_log_entryと同じ理由で外部キー制約を設けない（履歴のライフサイクル独立性）。
 */
@Entity
@Table(name = "query_execution_record")
public class QueryExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executed_by", nullable = false)
    private Long executedBy;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Lob
    @Column(nullable = false)
    private String sql;

    @Lob
    @Column
    private String params;

    @Column(name = "saved_query_id")
    private Long savedQueryId;

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "duration_millis", nullable = false)
    private long durationMillis;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected QueryExecutionRecord() {
        // JPA
    }

    public QueryExecutionRecord(Long executedBy, Long connectionId, String schemaName, String sql, String params,
                                 Long savedQueryId, long rowCount, long durationMillis, Instant executedAt) {
        this.executedBy = executedBy;
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.sql = sql;
        this.params = params;
        this.savedQueryId = savedQueryId;
        this.rowCount = rowCount;
        this.durationMillis = durationMillis;
        this.executedAt = executedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutedBy() {
        return executedBy;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getSql() {
        return sql;
    }

    public String getParams() {
        return params;
    }

    public Long getSavedQueryId() {
        return savedQueryId;
    }

    public long getRowCount() {
        return rowCount;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}

-- QueryExecutionRecord（domain-entities.md §2）。クエリ実行1回分の記録。
-- audit_log_entryと同じ理由（対象リソースのライフサイクル変更が実行履歴に影響しないように
-- するため）で、connection_id・saved_query_idには外部キー制約を設けない。
-- sql/paramsは任意長のユーザ入力SQL文・パラメータを保持するためCLOBとする。
CREATE TABLE query_execution_record
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    executed_by     BIGINT       NOT NULL,
    connection_id   BIGINT       NOT NULL,
    schema_name     VARCHAR(255) NOT NULL,
    sql             CLOB         NOT NULL,
    params          CLOB,
    saved_query_id  BIGINT,
    row_count       BIGINT       NOT NULL,
    duration_millis BIGINT       NOT NULL,
    executed_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_query_execution_record_executed_by ON query_execution_record (executed_by);
CREATE INDEX idx_query_execution_record_executed_at ON query_execution_record (executed_at);
CREATE INDEX idx_query_execution_record_saved_query ON query_execution_record (saved_query_id);

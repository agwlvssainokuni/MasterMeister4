-- AuditLogEntry（domain-entities.md §6）。UNIT-02で記録基盤を新設するが、
-- 全ユニット共通で利用される横断テーブル。user_id/connection_idは、対象レコードの
-- ライフサイクル変更（削除等）が監査履歴に影響しないよう、あえて外部キー制約を設けない。
CREATE TABLE audit_log_entry
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at     TIMESTAMP     NOT NULL,
    user_id         BIGINT,
    connection_id   BIGINT,
    event_type      VARCHAR(50)   NOT NULL,
    target_resource VARCHAR(255),
    result_status   VARCHAR(20)   NOT NULL,
    detail          VARCHAR(2000)
);

CREATE INDEX idx_audit_log_entry_occurred_at ON audit_log_entry (occurred_at);
CREATE INDEX idx_audit_log_entry_event_type ON audit_log_entry (event_type);
CREATE INDEX idx_audit_log_entry_user_id ON audit_log_entry (user_id);

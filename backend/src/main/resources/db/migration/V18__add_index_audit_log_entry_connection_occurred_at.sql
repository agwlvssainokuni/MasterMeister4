CREATE INDEX idx_audit_log_entry_connection_occurred_at
    ON audit_log_entry (connection_id, occurred_at);

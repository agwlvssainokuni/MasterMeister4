-- SchemaSnapshot（domain-entities.md §2）。全置換方式（BR-RDBMS-08）のため、
-- 1接続につき常に最新の1件のみ存在する。connection_idを主キー兼外部キーとする。
CREATE TABLE schema_snapshot
(
    connection_id BIGINT    NOT NULL PRIMARY KEY,
    imported_at   TIMESTAMP NOT NULL,
    CONSTRAINT fk_schema_snapshot_connection FOREIGN KEY (connection_id)
        REFERENCES rdbms_connection (id) ON DELETE CASCADE
);

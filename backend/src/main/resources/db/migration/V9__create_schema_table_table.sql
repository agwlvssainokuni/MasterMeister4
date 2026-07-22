-- SchemaTable（domain-entities.md §2.1）。SchemaSnapshotに属するテーブル/ビュー。
-- schema_name: 1接続内に複数スキーマが存在しうる前提（UNIT-04 Functional Designにて訂正）
-- のため、どのスキーマに属するかを保持する。
CREATE TABLE schema_table
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT       NOT NULL,
    schema_name   VARCHAR(255) NOT NULL,
    table_name    VARCHAR(255) NOT NULL,
    table_type    VARCHAR(20)  NOT NULL,
    comment       VARCHAR(1000),
    CONSTRAINT fk_schema_table_connection FOREIGN KEY (connection_id)
        REFERENCES schema_snapshot (connection_id) ON DELETE CASCADE
);

CREATE INDEX idx_schema_table_connection_id ON schema_table (connection_id);

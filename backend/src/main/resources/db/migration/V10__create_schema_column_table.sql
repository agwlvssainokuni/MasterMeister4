-- SchemaColumn（domain-entities.md §2.2）。BR-RDBMS-06: 型情報はnative_type/normalized_type
-- の両方を保持する（Q4=B）。
CREATE TABLE schema_column
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id         BIGINT       NOT NULL,
    column_name      VARCHAR(255) NOT NULL,
    ordinal_position INT          NOT NULL,
    comment          VARCHAR(1000),
    native_type      VARCHAR(255) NOT NULL,
    normalized_type  VARCHAR(20)  NOT NULL,
    nullable         BOOLEAN      NOT NULL,
    default_value    VARCHAR(500),
    CONSTRAINT fk_schema_column_table FOREIGN KEY (table_id)
        REFERENCES schema_table (id) ON DELETE CASCADE
);

CREATE INDEX idx_schema_column_table_id ON schema_column (table_id);

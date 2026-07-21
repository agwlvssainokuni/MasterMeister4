-- SchemaConstraint（domain-entities.md §2.3）。BR-RDBMS-06: 主キー・外部キー・一意制約・
-- インデックスの4種（Q3=C）。column_names/referenced_columnsは複合キー対応のため
-- カンマ区切り文字列として保持する（Code Generation時点の実装判断、シンプルさを優先）。
CREATE TABLE schema_constraint
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id           BIGINT       NOT NULL,
    constraint_type    VARCHAR(20)  NOT NULL,
    constraint_name    VARCHAR(255) NOT NULL,
    column_names       VARCHAR(1000) NOT NULL,
    referenced_table   VARCHAR(255),
    referenced_columns VARCHAR(1000),
    CONSTRAINT fk_schema_constraint_table FOREIGN KEY (table_id)
        REFERENCES schema_table (id) ON DELETE CASCADE
);

CREATE INDEX idx_schema_constraint_table_id ON schema_constraint (table_id);

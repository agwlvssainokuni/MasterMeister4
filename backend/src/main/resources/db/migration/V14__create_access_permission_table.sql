-- AccessPermission（domain-entities.md §1）。プリンシパル×リソース階層に対する明示的な権限設定。
-- schema_name/table_name/column_nameはUNIT-03のschema_table/schema_columnへの外部キーとせず、
-- 名前の文字列として独立して保持する（スキーマ再取込の全置換で権限設定が失われないため）。
-- table_name/column_nameが「該当階層なし」の場合、SQL NULLではなく空文字列('')を格納する
-- (nfr-design-patterns.md §3.1)。NULL同士は複合UNIQUE制約上「等しくない」とみなされ
-- 一意性が機能しなくなるため、全列NOT NULLとしこの回避策を採用する。
CREATE TABLE access_permission
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id     BIGINT       NOT NULL,
    principal_type    VARCHAR(10)  NOT NULL,
    principal_id      BIGINT       NOT NULL,
    schema_name       VARCHAR(255) NOT NULL,
    table_name        VARCHAR(255) NOT NULL,
    column_name       VARCHAR(255) NOT NULL,
    primary_permission VARCHAR(10) NOT NULL,
    create_permission BOOLEAN      NOT NULL,
    delete_permission BOOLEAN      NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    updated_by        BIGINT       NOT NULL,
    CONSTRAINT fk_access_permission_connection FOREIGN KEY (connection_id)
        REFERENCES rdbms_connection (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_access_permission_key
    ON access_permission (connection_id, principal_type, principal_id, schema_name, table_name, column_name);
CREATE INDEX idx_access_permission_principal
    ON access_permission (connection_id, principal_type, principal_id);
CREATE INDEX idx_access_permission_resource
    ON access_permission (connection_id, schema_name, table_name, column_name);

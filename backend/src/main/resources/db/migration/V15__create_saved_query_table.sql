-- SavedQuery（domain-entities.md §1）。保存クエリ本体。connection_idは保存時点で固定し、
-- 対象接続が削除されれば連動して削除する（UNIT-04のaccess_permissionと同じ理由でON DELETE CASCADE）。
-- スキーマ名は保持しない（FR-6.3）。sqlは任意長のユーザ入力SQL文を保持するためCLOBとする。
CREATE TABLE saved_query
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    sql           CLOB         NOT NULL,
    visibility    VARCHAR(10)  NOT NULL,
    created_by    BIGINT       NOT NULL,
    retired       BOOLEAN      NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT fk_saved_query_connection FOREIGN KEY (connection_id)
        REFERENCES rdbms_connection (id) ON DELETE CASCADE
);

CREATE INDEX idx_saved_query_connection ON saved_query (connection_id);
CREATE INDEX idx_saved_query_created_by ON saved_query (created_by);

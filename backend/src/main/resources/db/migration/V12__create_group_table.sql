-- Group（domain-entities.md §2）。ユーザグループ。テーブル名はH2の予約語"GROUP"を避け、
-- V1のapp_user（同様の理由）に倣いapp_groupとする。
CREATE TABLE app_group
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_app_group_name ON app_group (name);

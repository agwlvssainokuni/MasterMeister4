-- User（domain-entities.md §1）。テーブル名はH2の予約語"USER"を避けるためapp_userとする。
-- emailは全ステータス共通で一意（BR-REG-06）。
CREATE TABLE app_user
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    full_name          VARCHAR(255) NOT NULL,
    preferred_language VARCHAR(10)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    created_at         TIMESTAMP    NOT NULL,
    status_changed_at  TIMESTAMP    NOT NULL,
    status_changed_by  BIGINT
);

CREATE INDEX idx_app_user_status ON app_user (status);

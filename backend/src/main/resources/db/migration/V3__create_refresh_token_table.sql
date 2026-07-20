-- RefreshToken（domain-entities.md §3）。トークンファミリID単位のローテーション・
-- 再利用検知（BR-TOKEN-01/02）、管理者による無効化に伴う一括失効（BR-TOKEN-04）に対応する。
CREATE TABLE refresh_token
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES app_user (id),
    token_family_id   VARCHAR(36)  NOT NULL,
    token_hash        VARCHAR(255) NOT NULL,
    issued_at         TIMESTAMP    NOT NULL,
    expires_at        TIMESTAMP    NOT NULL,
    revoked_at        TIMESTAMP,
    revoked_reason    VARCHAR(20)
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_token_family_id ON refresh_token (token_family_id);
CREATE INDEX idx_refresh_token_token_hash ON refresh_token (token_hash);

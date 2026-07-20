-- RegistrationToken（domain-entities.md §2）。Step2完了までUserレコードが存在しないため、
-- emailで緩やかに対応付け、Userへの外部キーは持たない（BR-REG-02）。
CREATE TABLE registration_token
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_registration_token_email ON registration_token (email);
CREATE INDEX idx_registration_token_token_hash ON registration_token (token_hash);

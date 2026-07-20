-- RegistrationRateState（domain-entities.md §5）。登録開始エンドポイントのレート制限（BR-REG-07）。
CREATE TABLE registration_rate_state
(
    email            VARCHAR(255) PRIMARY KEY,
    request_count    INT       NOT NULL,
    window_start_at  TIMESTAMP NOT NULL
);

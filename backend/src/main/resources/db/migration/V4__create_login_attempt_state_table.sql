-- LoginAttemptState（domain-entities.md §4）。ログイン試行制限（BR-LOGIN-01〜03）。
CREATE TABLE login_attempt_state
(
    email           VARCHAR(255) PRIMARY KEY,
    failure_count   INT       NOT NULL,
    locked_until    TIMESTAMP,
    last_failure_at TIMESTAMP
);

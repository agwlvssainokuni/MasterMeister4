-- RdbmsConnection（domain-entities.md §1）。対象RDBMSへの接続情報。
-- host/port/database_nameの重複は許容する（BR-RDBMS-02）ため一意制約は設けない。
CREATE TABLE rdbms_connection
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name      VARCHAR(255)   NOT NULL,
    db_type           VARCHAR(20)    NOT NULL,
    host              VARCHAR(255)   NOT NULL,
    port              INT            NOT NULL,
    database_name     VARCHAR(255)   NOT NULL,
    schema_name       VARCHAR(255),
    username          VARCHAR(255)   NOT NULL,
    encrypted_password VARCHAR(500)  NOT NULL,
    encryption_key_id INT            NOT NULL,
    additional_params VARCHAR(1000),
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP      NOT NULL
);

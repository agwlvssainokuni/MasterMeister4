-- GroupMembership（domain-entities.md §3）。グループとユーザの所属関係。
-- user_idはUNIT-02のapp_userを参照するが、registrationパッケージへの直接依存を避けるため
-- JPAエンティティ上はIDのみ保持する。DB外部キー制約自体は整合性のため設定する。
CREATE TABLE group_membership
(
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id  BIGINT NOT NULL,
    CONSTRAINT fk_group_membership_group FOREIGN KEY (group_id)
        REFERENCES app_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_membership_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_group_membership_group_user ON group_membership (group_id, user_id);

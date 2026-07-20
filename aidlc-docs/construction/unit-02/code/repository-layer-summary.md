# UNIT-02 ユーザ登録・認証 - Repository Layer Summary

`unit-02-code-generation-plan.md` Section 2〜5の実行結果サマリ。

## Flywayマイグレーション（`backend/src/main/resources/db/migration/`）

| ファイル | 内容 |
|---|---|
| `V1__create_app_user_table.sql` | `app_user`テーブル（H2予約語`USER`を避けた命名）。`email`は全ステータス共通で一意（BR-REG-06） |
| `V2__create_registration_token_table.sql` | `registration_token`テーブル |
| `V3__create_refresh_token_table.sql` | `refresh_token`テーブル。`user_id`は`app_user`への外部キー |
| `V4__create_login_attempt_state_table.sql` | `login_attempt_state`テーブル |
| `V5__create_registration_rate_state_table.sql` | `registration_rate_state`テーブル |
| `V6__create_audit_log_entry_table.sql` | `audit_log_entry`テーブル。`user_id`/`connection_id`は対象レコードのライフサイクル変更が監査履歴に影響しないよう、あえて外部キー制約を設けていない |

## JPAエンティティ（`backend/src/main/java/cherry/mastermeister/`）

| パッケージ | エンティティ・enum |
|---|---|
| `registration.entity` | `User`, `RegistrationToken`, `RegistrationRateState`, `UserStatus`, `Role`, `Language` |
| `auth.entity` | `RefreshToken`, `LoginAttemptState`, `RevokeReason` |
| `audit.entity` | `AuditLogEntry`, `AuditEventType`, `ResultStatus` |
| `audit.event` | `AuditEvent`（永続化しないDTO） |

各エンティティは、状態遷移・不変条件（BR-REG-01、BR-TOKEN-01/02/04、BR-LOGIN-01〜03、BR-REG-07等）に対応するメソッド（`changeStatus`, `revoke`, `recordFailure`, `increment`等）を持つ。

## Spring Data JPAリポジトリ

`UserRepository`, `RegistrationTokenRepository`, `RegistrationRateStateRepository`（`registration.repository`）、`RefreshTokenRepository`, `LoginAttemptStateRepository`（`auth.repository`）、`AuditLogEntryRepository`（`audit.repository`）。

## テスト結果

`@DataJpaTest`による6リポジトリ・16テストケース、すべて成功。`User.email`の一意制約違反、`RefreshToken`の外部キー制約、Flywayマイグレーションの自動適用を確認済み。

## 実装時の判断・トラブルシューティング

- **テーブル名`app_user`**: H2の予約語`USER`を避けるため、エンティティ名`User`に対し`@Table(name = "app_user")`を明示
- **Spring Boot 4.1でのテストスライスアノテーションのパッケージ変更**: `@DataJpaTest`は`org.springframework.boot.test.autoconfigure.orm.jpa`（Spring Boot 3.x系の場所）ではなく、`org.springframework.boot.data.jpa.test.autoconfigure`（`spring-boot-data-jpa-test`モジュール）に移動していた。`spring-boot-starter-test`だけでは提供されず、`testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")`の追加が必要だった。同様に`@WebMvcTest`も`org.springframework.boot.webmvc.test.autoconfigure`（`spring-boot-starter-webmvc-test`）に移動していることを確認済み（Section 11で使用）
- **`refresh_token.user_id`の外部キー制約**: `RefreshTokenRepositoryTest`で実在しない`userId`を直接指定していたため制約違反で失敗。`UserRepository`で実際に`User`を永続化してから`id`を使うよう修正

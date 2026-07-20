# UNIT-02 ユーザ登録・認証 - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定Bean）に落とし込む。パッケージは`cherry.mastermeister.registration`, `cherry.mastermeister.auth`, `cherry.mastermeister.audit`, `cherry.mastermeister.common`（unit-of-work.mdの既存方針）。

---

## 1. セキュリティ設定

### SecurityConfig（`cherry.mastermeister.common.security`）
- `SecurityFilterChain` Beanを定義（3.6のURLパターンマッチング）
- `JwtDecoder` Bean（HS256、`mm.app.jwt.secret`から鍵を読込）
- CORS設定（許可オリジンの明示列挙）
- HTTPセキュリティヘッダ設定（`headers()` DSL + CSP明示）

### JwtAuthenticationConverter（`cherry.mastermeister.common.security`）
- JWTクレームからロール（`ADMIN`/`USER`）を抽出し、Spring Securityの`Authentication`オブジェクトへ変換する

---

## 2. 登録・認証ドメイン（`cherry.mastermeister.registration`, `cherry.mastermeister.auth`）

### UserRegistrationService（COMP-01、`cherry.mastermeister.registration`）
- business-logic-model.md §1〜3の登録・承認・無効化フローを実装
- `UserRepository`（Spring Data JPA）経由でUser永続化

### RegistrationRateGuard（`cherry.mastermeister.registration`、tech-stack-decisions.md Q4新設）
- Step1エンドポイントのメールアドレス単位レート制限（SECURITY-11）
- `RegistrationRateState`エンティティ（LoginAttemptStateと同様のパターン）で永続化

### PasswordBreachChecker（`cherry.mastermeister.registration`）
- HIBP等の既知漏洩パスワードチェックAPIを呼び出す（BR-PWD-02）
- タイムアウト3秒、フェイルオープン（3.1参照）

### TokenGenerator（`cherry.mastermeister.common.security`、business-rules.md BR-REG-02で言及済み）
- ランダムトークン生成・ハッシュ化の共通ユーティリティ。RegistrationTokenServiceとRefreshTokenServiceの双方から利用する

### AdminBootstrapService（COMP-02、`cherry.mastermeister.registration`）
- `ApplicationRunner`として起動時に実行

### AuthenticationService（COMP-03、`cherry.mastermeister.auth`）
- ログイン・ログアウト・アクセストークン発行
- JWT生成には`spring-security-oauth2-jose`の`JwtEncoder`（HS256）を用いる

### RefreshTokenService（COMP-04、`cherry.mastermeister.auth`）
- ローテーション・再利用検知（business-logic-model.md §6）
- `RefreshTokenRepository`（Spring Data JPA）

### LoginAttemptGuard（COMP-05、`cherry.mastermeister.auth`）
- ログイン試行制限（BR-LOGIN-01〜03）
- `LoginAttemptStateRepository`（Spring Data JPA）

### EmailNotificationService（COMP-06、`cherry.mastermeister.registration`）
- Mustacheテンプレートレンダリング（`cherry.mustache.Mustache` API呼び出し）
- 件名抽出（BR-MAIL-03の正規表現処理）
- `spring-boot-starter-mail`の`JavaMailSender`でSMTP送信
- 送信失敗時のフェイルオープン処理（3.1参照）

---

## 3. 監査ログ基盤（`cherry.mastermeister.audit`）

### AuditEventPublisher（COMP-19）
- `ApplicationEventPublisher`のラッパー

### AuditLogService（COMP-18、記録機能のみ）
- `@TransactionalEventListener(phase = AFTER_COMMIT)`でAuditEventを受信
- 記録メソッドに`@Transactional(propagation = Propagation.REQUIRES_NEW)`を明示付与（BR-AUDIT-01）
- `AuditLogEntryRepository`（Spring Data JPA）

---

## 4. 横断コンポーネント（`cherry.mastermeister.common`）

### GlobalExceptionHandler
- `@RestControllerAdvice`（3.5参照）
- BR-API-01形式のエラーレスポンスDTOへ変換

### ApiErrorResponse（DTO）
- `{ code: String, message: String }`（BR-API-01）

### MailTemplateRenderer
- `cherry-mustache-core`（独立Gradleサブプロジェクト、BR-MAIL-02）をラップし、言語別テンプレートファイルの解決・レンダリング・件名抽出（BR-MAIL-03）を行う

---

## 5. データアクセス（内部DB）

- Spring Data JPAリポジトリ: `UserRepository`, `RegistrationTokenRepository`, `RefreshTokenRepository`, `LoginAttemptStateRepository`, `RegistrationRateStateRepository`, `AuditLogEntryRepository`
- Flywayマイグレーションスクリプト（`backend/src/main/resources/db/migration/`）:
  - `V1__create_user_table.sql`
  - `V2__create_registration_token_table.sql`
  - `V3__create_refresh_token_table.sql`
  - `V4__create_login_attempt_state_table.sql`
  - `V5__create_registration_rate_state_table.sql`
  - `V6__create_audit_log_entry_table.sql`
  - （具体的なバージョン番号・分割単位はCode Generation段階で調整）
- H2接続設定: `jdbc:h2:file:${mm.app.datasource.path}`（環境変数で指定）。開発・テストプロファイルでは`jdbc:h2:mem:...`を使用可

---

## 6. フロントエンド連携コンポーネント（参考、詳細はfrontend-components.md）

- トークン保管: `sessionStorage`（3.1参照）。アクセス・リフレッシュ双方を保管するユーティリティ（`tokenStorage.ts`等）をCode Generation段階で新設
- 認証状態管理（AuthContext等）・AppShell配下ルートの認証ガードは、Code Generation段階でフロントエンド実装として具体化する

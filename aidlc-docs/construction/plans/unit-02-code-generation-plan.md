# UNIT-02 ユーザ登録・認証 - Code Generation Plan（Part 1）

## Unit Context

- **対応ストーリー**: STORY-1.1〜1.4（ユーザ登録）、STORY-3.1〜3.3（認証）
- **対応要件**: requirements.md §5.1（FR-1.1〜1.14）、§5.3（FR-3.1〜3.7）、§6（監査ログ要件）
- **対応コンポーネント**: COMP-01（UserRegistrationService）, COMP-02（AdminBootstrapService）, COMP-03（AuthenticationService）, COMP-04（RefreshTokenService）, COMP-05（LoginAttemptGuard）, COMP-06（EmailNotificationService）, COMP-18（記録機能のみ）, COMP-19（AuditEventPublisher）
- **前提ユニット**: UNIT-01（`design-system`一式・`PublicLayout`/`AppShell`・代表画面モックを利用）
- **設計インプット**: `aidlc-docs/construction/unit-02/functional-design/`（business-logic-model.md, business-rules.md, domain-entities.md, frontend-components.md）、`aidlc-docs/construction/unit-02/nfr-requirements/`（nfr-requirements.md, tech-stack-decisions.md）、`aidlc-docs/construction/unit-02/nfr-design/`（nfr-design-patterns.md, logical-components.md）
- **パッケージ**: `cherry.mastermeister.registration`, `cherry.mastermeister.auth`, `cherry.mastermeister.audit`, `cherry.mastermeister.common`（横断基盤）
- **バックエンドコード配置**: `backend/src/main/java/cherry/mastermeister/`、`backend/src/test/java/cherry/mastermeister/`
- **フロントエンドコード配置**: `frontend/src/pages/`, `frontend/src/api/`, `frontend/src/auth/`

本計画は`unit-02-functional-design-plan.md`・`unit-02-nfr-requirements-plan.md`・`unit-02-nfr-design-plan.md`で確定した内容の実装への落とし込みであり、本計画自体が新たな設計判断を行う場ではない（既存決定と矛盾する記述を発見した場合は生成前に指摘する）。

## Part 1計画へのユーザー追加指示の反映

- **設定値アクセス方針**: `application.yml`の`mm.app.*`配下の設定値は、`AppProperties`をトップとするrecordクラス階層（`@ConfigurationProperties(prefix = "mm.app")`）で受け取る。機能領域ごとにネストしたrecord（`Jwt`, `Password`, `LoginAttempt`, `UserRegistration`, `AdminBootstrap`, `Datasource`等）に分割する。各コンポーネントは個別に`@Value`を使わず、`AppProperties`（またはそのネストレコード）をコンストラクタインジェクションで参照する。recordのコンパクトコンストラクタで値検証（必須項目のnullチェック、JWT鍵長、正の数値等）を行う
- **SPA配信とSecurityConfig**: フロントエンドは単一WARに同梱されるSPA（React Router、クライアントサイドルーティング）であるため、SecurityFilterChainは`/api/**`以外を`permitAll()`とする（ページレベルの認可はクライアント側のAuthContextが担い、サーバ側では`/api/**`のみを認可制御する）。あわせて、存在する静的リソースはそのまま返却し、`/api/**`はコントローラへスルーし、それ以外（React Routerのクライアントサイドルート）は`/index.html`にフォールバックするルーティング処理を追加する

---

## 計画チェックリスト

### 1. Project Structure Setup

- [x] Step 1.1: `reference/mustache-engine/cherry-mustache-core`（パッケージ`cherry.mustache`、テストコード一式含む）をワークスペース直下`cherry-mustache-core/`へそのままコピーする（BR-MAIL-02）
- [x] Step 1.2: `settings.gradle.kts`に`include("cherry-mustache-core")`を追加する
- [x] Step 1.3: `backend/build.gradle.kts`に依存関係を追加する: `implementation(project(":cherry-mustache-core"))`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-validation`, `spring-boot-starter-mail`, `spring-boot-starter-data-jpa`, `com.h2database:h2`, `org.flywaydb:flyway-core`, `org.springframework.boot:spring-boot-flyway`, `logstash-logback-encoder`（本番プロファイル用）, `net.jqwik:jqwik`（testImplementation）。あわせてOpenAPI（Step 10.7で使用）の`springdoc-openapi-starter-webmvc-ui`も先行追加
- [x] Step 1.4: `backend/src/main/resources/application.yml`を拡張する（H2ファイルベース接続設定、JPA/Flyway設定、`mm.app.*`配下のJWT・パスワードポリシー・ログイン試行制限・登録レート制限・初期管理者・フロントエンドベースURL等の設定項目、Mail設定。値はすべて環境変数プレースホルダー、NFR-2.3準拠）
- [x] Step 1.5: `AppProperties`（`cherry.mastermeister.common.config`）を作成する。`@ConfigurationProperties(prefix = "mm.app")`を付与したrecordをトップとし、機能領域ごとにネストしたrecord（`Jwt`（secret, accessTokenExpiry, refreshTokenExpiry）, `Password`（bcryptStrength, minLength）, `LoginAttempt`（maxFailures, lockDuration）, `UserRegistration`（tokenExpiry, rateLimitMaxRequests, rateLimitWindow）, `AdminBootstrap`（email, password）, `Frontend`（baseUrl）, `Datasource`（path））に分割する。各recordのコンパクトコンストラクタで値検証（必須項目のnullチェック、JWT鍵長、正の数値等）を行う。`MasterMeisterApplication`に`@ConfigurationPropertiesScan`を付与して有効化した
- [x] Step 1.6: **検証チェックポイント**: `./gradlew :cherry-mustache-core:test`（成功）、`./gradlew :backend:build`（成功）。`cherry-mustache-core/build.gradle.kts`は独立プロジェクトの想定だったため`repositories{}`・Java toolchain・`useJUnitPlatform()`が root側委譲で未設定だった（reference/mustache-engine/build.gradle.ktsのallprojects設定に依存していたため）。MasterMeisterにはルートbuild.gradle.ktsが存在しないため、これらをcherry-mustache-core/build.gradle.kts自体に追加して解決（OWASP Dependency-Checkプラグインのバージョンもbackendと同じ12.1.0に統一）

### 2. Database Migration Scripts

- [x] Step 2.1: `backend/src/main/resources/db/migration/V1__create_user_table.sql`を作成する（domain-entities.md §1 User、`email`一意制約は全ステータス共通、BR-REG-06）
- [x] Step 2.2: `V2__create_registration_token_table.sql`を作成する（domain-entities.md §2）
- [x] Step 2.3: `V3__create_refresh_token_table.sql`を作成する（domain-entities.md §3、`revokedReason`に`ADMIN_DISABLED`を含む）
- [x] Step 2.4: `V4__create_login_attempt_state_table.sql`を作成する（domain-entities.md §4）
- [x] Step 2.5: `V5__create_registration_rate_state_table.sql`を作成する（domain-entities.md §5、BR-REG-07）
- [x] Step 2.6: `V6__create_audit_log_entry_table.sql`を作成する（domain-entities.md §6、`eventType`は本ユニットで追加する9種を含むが、他ユニットの追加を見越して文字列型で保持する）
- [x] Step 2.7: **検証チェックポイント**: Flywayマイグレーションが起動時に正常適用されることを、後続のRepository層テスト（Section 4）実行時に確認した

### 3. Repository Layer Generation

- [x] Step 3.1: JPAエンティティを作成する: `User`, `RegistrationToken`, `RefreshToken`, `LoginAttemptState`, `RegistrationRateState`, `AuditLogEntry`（domain-entities.md §1〜6の属性・不変条件をエンティティ制約として反映。`UserStatus`, `Role`, `Language`, `RevokeReason`, `AuditEventType`, `ResultStatus`はenumとして作成）
- [x] Step 3.2: Spring Data JPAリポジトリを作成する: `UserRepository`, `RegistrationTokenRepository`, `RefreshTokenRepository`, `LoginAttemptStateRepository`, `RegistrationRateStateRepository`, `AuditLogEntryRepository`（`cherry.mastermeister.registration.repository`, `cherry.mastermeister.auth.repository`, `cherry.mastermeister.audit.repository`）
- [x] Step 3.3: `AuditEvent`（永続化しないDTO、domain-entities.md §7）を作成する

### 4. Repository Layer Unit Testing

- [x] Step 4.1: `@DataJpaTest`で各リポジトリの基本CRUD・制約（`User.email`一意制約、`RegistrationToken`の有効期限判定用クエリ等）を検証するテストを作成する
- [x] Step 4.2: Flywayマイグレーションが`@DataJpaTest`実行時に正常適用されることを確認する（`refresh_token.user_id`にapp_userへのFK制約を追加していたため、テストが実在しないuserIdを使っていて失敗していた点を修正し、全16テスト成功を確認）

### 5. Repository Layer Summary

- [x] Step 5.1: `aidlc-docs/construction/unit-02/code/repository-layer-summary.md`を作成する（作成したエンティティ・リポジトリ・マイグレーション一覧、テスト結果）

### 6. Business Logic Generation

- [x] Step 6.1: `TokenGenerator`（`cherry.mastermeister.common.security`）を作成する（ランダムトークン生成・ハッシュ化の共通ユーティリティ、BR-REG-02）
- [x] Step 6.2: `PasswordBreachChecker`（`cherry.mastermeister.registration`）を作成する（HIBP等API呼び出し、タイムアウト3秒、フェイルオープン、BR-PWD-02）
- [x] Step 6.3: `MailTemplateRenderer`（`cherry.mastermeister.common.mail`）を作成する（`cherry-mustache-core`のラップ、言語別テンプレート解決、BR-MAIL-03の件名抽出ロジック: 改行除去は抽出専用コピーに適用、非貪欲マッチ抽出、トリム、HTMLエンティティデコード、未検出/空時は例外送出）
- [x] Step 6.4: `EmailNotificationService`（COMP-06、`cherry.mastermeister.registration`）を作成する（登録確認・承認結果・却下結果メール送信、`MailTemplateRenderer`・`JavaMailSender`利用、送信失敗時フェイルオープン）
- [x] Step 6.5: メールテンプレートファイルを作成する（`backend/src/main/resources/mail-templates/{ja,en}/registration-confirmation.html`, `approval-result.html`, `rejection-result.html`。各`<title>`要素に件名を含む）
- [x] Step 6.6: `RegistrationRateGuard`（`cherry.mastermeister.registration`）を作成する（BR-REG-07、デフォルト1時間3回）
- [x] Step 6.7: `UserRegistrationService`（COMP-01、`cherry.mastermeister.registration`）を作成する（business-logic-model.md §1〜3の登録・承認・却下・却下取消・無効化・再有効化フローを実装。`createApprovedAccount()`含む）
- [x] Step 6.8: `AdminBootstrapService`（COMP-02、`cherry.mastermeister.registration`）を作成する（`ApplicationRunner`、`AppProperties.AdminBootstrap`経由での初期管理者作成、business-logic-model.md §4）
- [x] Step 6.9: `LoginAttemptGuard`（COMP-05、`cherry.mastermeister.auth`）を作成する（BR-LOGIN-01〜03）
- [x] Step 6.10: `AuthenticationService`（COMP-03、`cherry.mastermeister.auth`）を作成する（ログイン・ログアウト・アクセストークン発行、`JwtEncoder`利用、business-logic-model.md §5）
- [x] Step 6.11: `RefreshTokenService`（COMP-04、`cherry.mastermeister.auth`）を作成する（ローテーション・再利用検知、`TokenGenerator`共用、business-logic-model.md §6。無効化時の`ADMIN_DISABLED`一括失効メソッドも含む）
- [x] Step 6.12: `AuditEventPublisher`（COMP-19、`cherry.mastermeister.audit`）を作成する
- [x] Step 6.13: `AuditLogService`（COMP-18、記録機能のみ、`cherry.mastermeister.audit`）を作成する（`@TransactionalEventListener(phase=AFTER_COMMIT)` + `@Transactional(propagation=REQUIRES_NEW)`、BR-AUDIT-01）

### 7. Business Logic Unit Testing

- [x] Step 7.1: `UserRegistrationService`のユニットテストを作成する（Mockito、登録・承認・却下・却下取消・無効化・再有効化の各分岐、BR-REG-01〜07の境界値）
- [x] Step 7.2: `AuthenticationService`・`LoginAttemptGuard`のユニットテストを作成する（ロック閾値・解除、BR-LOGIN-01〜03）
- [x] Step 7.3: `RefreshTokenService`のユニットテストを作成する（ローテーション、再利用検知、ファミリ一括失効、BR-TOKEN-01〜04）
- [x] Step 7.4: `MailTemplateRenderer`のユニットテストを作成する（件名抽出の境界値: 改行を含む`<title>`、属性付き`<title>`、`<title>`欠落時の例外、HTMLエンティティのデコード）。jqwikによるプロパティベーステストも検討する（NFR Requirements PBT-09基盤の初適用機会だが、本ユニットはPBT対象プロパティなしと判定済みのためexample-basedテストのみとする）
- [x] Step 7.5: `AuditLogService`のユニットテストを作成する（イベント受信・別トランザクション記録の検証、domain-entities.md §6.1のイベント種別ごとのuserId/targetResource/detail検証）
- [x] Step 7.6: `TokenGenerator`・`PasswordBreachChecker`・`RegistrationRateGuard`のユニットテストを作成する

### 8. Business Logic Summary

- [x] Step 8.1: `aidlc-docs/construction/unit-02/code/business-logic-summary.md`を作成する（作成したサービス一覧、責務、テスト結果）

### 9. Security Configuration

- [ ] Step 9.1: `SecurityConfig`（`cherry.mastermeister.common.security`）を作成する（`SecurityFilterChain`。`/api/**`以外は`permitAll()`とする（SPA配信のため、ページレベル認可はクライアント側のAuthContextが担う）。`/api/**`配下は従来どおりnfr-design-patterns.md §3.6のURLパターンマッチング: `/api/auth/**`・`/api/registrations/**`を`permitAll()`、`/api/admin/**`を`hasRole('ADMIN')`、その他の`/api/**`を`authenticated()`とする）
- [ ] Step 9.2: `JwtDecoder`/`JwtEncoder` Beanを作成する（HS256、`AppProperties.Jwt`（Step 1.5で作成）から鍵・有効期限を取得。鍵長検証は`AppProperties.Jwt`のコンパクトコンストラクタで実施済みのため、Bean定義側では追加検証しない）
- [ ] Step 9.3: `JwtAuthenticationConverter`を作成する（JWTクレーム→ロール変換）
- [ ] Step 9.4: HTTPセキュリティヘッダ設定を作成する（Spring Securityデフォルト + CSP明示、nfr-design-patterns.md §3.2）
- [ ] Step 9.5: CORS設定を作成する（許可オリジンの明示列挙）
- [ ] Step 9.6: SPAフォールバックルーティングを実装する（`WebMvcConfigurer`の`addResourceHandlers`にPathResourceResolverを拡張し、リクエストパスが実在する静的リソースに一致すればそれを返却、`/api/**`はコントローラへスルー（`addResourceHandlers`の対象外とする）、それ以外は`/index.html`にフォールバックする。React Routerのクライアントサイドルート（`/`, `/login`, `/register`, `/users`等）がリロード・直接アクセスされた場合も正しく配信されることをStep 18.2の手動確認で検証する）

### 10. API Layer Generation

- [ ] Step 10.1: `GlobalExceptionHandler`（`cherry.mastermeister.common`）を作成する（`@RestControllerAdvice`、BR-API-01形式への変換、nfr-design-patterns.md §3.5）
- [ ] Step 10.2: `ApiErrorResponse` DTOを作成する（`code`, `message`）
- [ ] Step 10.3: リクエストDTOを作成する（Bean Validationアノテーション付与）: `RegistrationStartRequest`, `RegistrationCompleteRequest`, `LoginRequest`, `RefreshRequest`
- [ ] Step 10.4: `RegistrationController`（`cherry.mastermeister.registration`）を作成する（`POST /api/registrations`, `POST /api/registrations/{token}/complete`）
- [ ] Step 10.5: `AuthController`（`cherry.mastermeister.auth`）を作成する（`POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`）
- [ ] Step 10.6: `AdminUserController`（`cherry.mastermeister.registration`）を作成する（`GET /api/admin/users`, `POST /api/admin/users/{id}/approve`, `.../reject`, `.../disable`, `.../enable`。frontend-components.md §4準拠）
- [ ] Step 10.7: OpenAPI/Swagger UI自動生成を設定する（`springdoc-openapi-starter-webmvc-ui`依存追加、NFR §7.12 API仕様書）

### 11. API Layer Unit Testing

- [ ] Step 11.1: `@WebMvcTest`で`RegistrationController`のテストを作成する（正常系・トークン無効・パスワードポリシー違反・レート制限）
- [ ] Step 11.2: `@WebMvcTest`で`AuthController`のテストを作成する（ログイン成功・失敗・ロック中、リフレッシュ正常・再利用検知）
- [ ] Step 11.3: `@WebMvcTest`で`AdminUserController`のテストを作成する（管理者ロールチェック、各ステータス遷移操作）
- [ ] Step 11.4: `SecurityConfig`の統合テストを作成する（`/api/admin/**`が非ADMINで403、未認証で401等）

### 12. API Layer Summary

- [ ] Step 12.1: `aidlc-docs/construction/unit-02/code/api-layer-summary.md`を作成する（エンドポイント一覧、テスト結果）

### 13. Frontend Components Generation

- [ ] Step 13.1: APIクライアント（`frontend/src/api/`）を作成する（`registrations.ts`, `auth.ts`, `adminUsers.ts`、フェッチラッパー、BR-API-01エラー形式の共通処理）
- [ ] Step 13.2: トークン保管ユーティリティ（`frontend/src/auth/tokenStorage.ts`）を作成する（`sessionStorage`、nfr-design-patterns.md §3.1）
- [ ] Step 13.3: 認証状態管理（`frontend/src/auth/AuthContext.tsx`等）を作成する（AppShell配下ルートの認証ガード、アクセストークン期限切れ時のリフレッシュ自動再試行、frontend-components.md §7）
- [ ] Step 13.4: `LoginPage`（`frontend/src/pages/`）を作成する（frontend-components.md §1）
- [ ] Step 13.5: `RegisterStep1Page`・`RegisterStep2Page`を作成する（frontend-components.md §2〜3、氏名・言語設定入力を含む）
- [ ] Step 13.6: `UserManagementPage`を作成する（frontend-components.md §4、統合済みの全ステータス対応・アクション出し分け）
- [ ] Step 13.7: `HomePage`（`FeatureCard`新設コンポーネント含む）を作成する（frontend-components.md §5、`Card`をベースに構築）
- [ ] Step 13.8: AppShell Headerのログアウト導線を実装する（既存の`Header`/`HeaderControl`コンポーネントを修正、frontend-components.md §6）
- [ ] Step 13.9: ルーティング設定を更新する（`/`, `/login`, `/register`, `/register/complete`, `/users`を追加し、AppShell配下ルートに認証ガードを適用）

### 14. Frontend Components Unit Testing

- [ ] Step 14.1: 各ページコンポーネントのテストを作成する（Vitest + RTL、状態遷移・バリデーション・API呼び出しモック）
- [ ] Step 14.2: 認証状態管理・トークン保管ユーティリティのテストを作成する

### 15. Frontend Components Summary

- [ ] Step 15.1: `aidlc-docs/construction/unit-02/code/frontend-summary.md`を作成する（作成した画面・コンポーネント一覧、テスト結果）

### 16. Documentation Generation

- [ ] Step 16.1: `backend/README.md`を更新する（新規依存関係、環境変数一覧、Flywayマイグレーション運用、OpenAPI閲覧方法）
- [ ] Step 16.2: `frontend/README.md`を更新する（新規ページ・ルーティングの追記）

### 17. Deployment Artifacts

- [ ] Step 17.1: `devenv/docker-compose.yml`を確認し、UNIT-02で必要な環境変数（MailPit接続先等）が既存のMailPitコンテナ設定と整合していることを確認する（新規追加は不要見込み）
- [ ] Step 17.2: `.env.example`等、開発時に設定すべき環境変数一覧のドキュメント（application.ymlのコメント、またはbackend/README.md）を整備する

### 18. 最終ビルド検証

- [ ] Step 18.1: **検証チェックポイント**: `./gradlew :backend:build`（全ユニットテスト成功）、`./gradlew :backend:test`、`npm test`（frontend）、`npm run build`（frontend）がすべて成功することを確認する
- [ ] Step 18.2: `./gradlew :backend:bootWar`で統合WARを生成し、`java -jar`起動・MailPit経由でのメール送受信確認・登録〜承認〜ログインの一連の手動確認を行う
- [ ] Step 18.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`, `:cherry-mustache-core:dependencyCheckAnalyze`）・`npm audit`を実行する

---

## Story Traceability

| ストーリー | 対応ステップ |
|---|---|
| STORY-1.1〜1.4（ユーザ登録） | Section 6（UserRegistrationService等）、Section 10（RegistrationController）、Section 13（RegisterStep1/2Page, UserManagementPage） |
| STORY-3.1〜3.3（認証） | Section 6（AuthenticationService, RefreshTokenService, LoginAttemptGuard）、Section 10（AuthController）、Section 13（LoginPage, 認証状態管理） |
| 監査ログ記録基盤（§6、UNIT-02が新設） | Section 3（AuditLogEntry）、Section 6（AuditEventPublisher, AuditLogService） |

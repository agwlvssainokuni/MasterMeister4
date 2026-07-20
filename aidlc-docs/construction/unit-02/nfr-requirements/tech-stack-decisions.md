# UNIT-02 ユーザ登録・認証 - Tech Stack Decisions

`unit-02-nfr-requirements-plan.md`の回答（全問A）に基づく、バックエンドの技術選定一覧。

---

## 1. 認証基盤フレームワーク（Q1=A）

**決定**: Spring Security + OAuth2 Resource Server機構（`JwtDecoder`/`JwtAuthenticationConverter`等の標準クラス）でJWT検証を行う。

**理由**: 自前の`ServletFilter`実装と比べ、認証・認可という最もセキュリティクリティカルな部分を十分に検証されたSpring公式実装に委ねることで、SECURITY-11（セキュアデザイン、多層防御）の観点でリスクを低減できる。Spring Boot 4.1（UNIT-01で確定済み）との親和性も高い。

**依存関係**: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`

## 2. JWT署名アルゴリズム・鍵管理（Q2=A）

**決定**: 対称鍵（HS256）。署名鍵は環境変数（`mm.app.jwt.secret`）から読み込む。

**理由**: 検証を行うのはバックエンド自身のみ（他サービスへの公開鍵配布が不要な単一アプリケーション構成）のため、非対称鍵（RS256）による鍵ペア管理の複雑さは本プロジェクトの規模（単独開発・約10名利用）に見合わない。

**設定項目**: `mm.app.jwt.secret`（必須、最小256bit相当の長さをアプリ起動時に検証）

## 3. パスワードハッシュアルゴリズム（Q3=A）

**決定**: BCrypt（Spring Security標準の`BCryptPasswordEncoder`）。コスト係数はデフォルト10とし、設定可能（`mm.app.password.bcrypt-strength`）とする。

**理由**: Spring Securityにビルトインで提供され追加ライブラリが不要。FR-1.13（適応型ハッシュアルゴリズム）・SECURITY-12の要件を満たす、広く実績のあるアルゴリズム。

**依存関係**: 追加不要（`spring-boot-starter-security`に含まれる）

## 4. 登録開始（Step1）エンドポイントのレート制限（Q4=A）

**決定**: `LoginAttemptGuard`と同様、メールアドレス単位で一定時間内の再送信回数を制限する仕組み（`RegistrationRateGuard`等、名称はCode Generation段階で確定）を新設する。

**理由**: SECURITY-11（公開エンドポイントのレート制限）に対応し、大量送信によるメール爆撃・スパム対策を行う。既存のLoginAttemptGuardと同じ設計パターン（メールアドレス単位のカウンタ・時間窓）を再利用することで、実装・レビューコストを抑える。

**設定項目**: `mm.app.user-registration.rate-limit.max-requests`（デフォルト値・時間窓はNFR Design段階で確定）

## 5. Property-Based Testingフレームワーク（Q5=A、NFR-5.2最終確定）

**決定**: jqwik（JUnit5統合）。

**理由**: requirements.md NFR-5.2で候補として明記済み。ユーザー提供の`reference/mustache-engine/cherry-mustache-core`（今回`cherry-mustache-core`として本プロジェクトに組み込み予定、business-rules.md BR-MAIL-02参照）で既に採用実績があり、依存関係・設定パターンをそのまま参考にできる。

**依存関係**: `net.jqwik:jqwik`（バージョンは`cherry-mustache-core`のbuild.gradle.ktsに合わせる想定、Code Generation段階で確定）

**適用範囲**: 本ユニット自体にはPBT対象プロパティなし。後続ユニット（UNIT-04 EffectivePermissionResolver・PermissionYamlService、UNIT-07 QueryBuilderService）が利用する基盤として、`backend`のGradle設定に組み込む

## 6. アプリケーションログ出力形式（Q6=A）

**決定**: SLF4J + Logback（Spring Boot標準）。開発環境はコンソール出力（人間可読）、本番相当環境向けには構造化JSON出力に切替可能な設定とする。

**理由**: Spring Bootのデフォルト構成をベースにでき、追加の学習・運用コストが小さい。NFR-2.4（コンテナ環境に適した構造化ログ出力）に対応しつつ、NFR-4.5と同様に集中ログ基盤（ELK等）の導入は本プロジェクトの規模には過剰と判断しスコープ外とする。

**依存関係**: `logstash-logback-encoder`（JSON出力用、本番プロファイルのみ有効化）

## 7. 機微設定情報の取り扱い（Q7=A）

**決定**: 初期管理者パスワード等の機微情報は環境変数経由でのみ受け渡す。アプリ起動ログ・例外メッセージ・監査ログのいずれにも値を出力しない。

**理由**: SECURITY-09（ハードニング）・NFR-2.3（全設定は環境変数経由）に準拠する既定方針。UNIT-01で確定済みのTwelve-Factor App準拠方針（NFR-2.1）とも整合する。

## 8. 内部DBへのアクセス方式（Q8〜Q10=A、レビュー指摘の反映）

**決定**:
- ORM抽象化: Spring Data JPA（リポジトリインターフェースによるCRUD）
- スキーマ管理: Flyway（バージョン管理されたSQLマイグレーションスクリプト、`src/main/resources/db/migration/`配下）
- H2永続化モード: ファイルベース（`jdbc:h2:file:...`）。DBファイルパスは環境変数で指定。開発・テストではインメモリモード（`jdbc:h2:mem:...`）も使用可
- コネクションプール: Spring Boot標準のHikariCP（デフォルト設定）

**理由**: requirements.md §2で内部DBのDBアクセス方式（JPA）・データベース種別（H2）は既に確定済みであり、UNIT-02はこれを実装する最初のユニット。Spring Data JPAはSpring Bootの標準的な使い方でボイラープレートを削減できる。Hibernateの`ddl-auto=update`による自動スキーマ生成は、本番運用でのスキーマドリフト（意図しないカラム変更・削除の見落とし等）のリスクがあるため避け、Flywayによる明示的でバージョン管理されたマイグレーションを採用する。

**依存関係**: `spring-boot-starter-data-jpa`, `com.h2database:h2`, `org.flywaydb:flyway-core`

---

## 依存関係まとめ（backend、UNIT-02で追加）

| 依存関係 | 用途 |
|---|---|
| `spring-boot-starter-security` | 認証・認可基盤 |
| `spring-boot-starter-oauth2-resource-server` | JWT検証（`JwtDecoder`） |
| `spring-boot-starter-validation` | 入力バリデーション（SECURITY-05） |
| `spring-boot-starter-mail` | メール送信（NFR-3.1〜3.2、既存決定） |
| `spring-boot-starter-data-jpa` | 内部DBへのアクセス（Spring Data JPA） |
| `com.h2database:h2` | 内部DB（H2 Database） |
| `org.flywaydb:flyway-core` | 内部DBのスキーママイグレーション |
| `logstash-logback-encoder` | 本番プロファイルの構造化JSON出力 |
| `net.jqwik:jqwik`（testImplementation） | Property-Based Testing基盤 |

`cherry-mustache-core`（メールテンプレートエンジン）は依存関係としてではなく、独立したGradleサブプロジェクトとして`backend`から`implementation(project(":cherry-mustache-core"))`で参照する（business-rules.md BR-MAIL-02、既存決定）。

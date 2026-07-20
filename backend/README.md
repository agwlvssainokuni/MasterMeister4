# MasterMeister backend

Spring Boot 4.1 / Spring Security 7.x / Java 25製のバックエンド。UNIT-02（ユーザ登録・認証）でユーザ登録・JWT認証・ユーザ管理・監査ログの基盤を構築した。

## 起動

開発用にMailPit（メール送信確認用SMTPサーバ）を起動しておく（`../devenv/docker-compose.yml`）。

```bash
docker compose -f ../devenv/docker-compose.yml up -d mailpit
```

JWT署名鍵は必須（デフォルト空文字のため未設定だと起動時に`AppProperties`のコンストラクタ検証で失敗する。32バイト以上の任意の文字列を指定する）。

```bash
export MM_APP_JWT_SECRET="$(openssl rand -base64 32)"
./gradlew :backend:bootRun
```

`http://localhost:8080/`で起動する。H2データベースは`./data/mastermeister`にファイルとして作成され、Flywayが`src/main/resources/db/migration/`のスクリプトを自動適用する（`ddl-auto: validate`のためスキーマはFlywayのみが変更する）。

## 環境変数

`src/main/resources/application.yml`にデフォルト値付きで定義されている。本番相当の運用では以下を明示的に設定すること。

| 環境変数 | デフォルト | 用途 |
|---|---|---|
| `MM_APP_JWT_SECRET` | (空・必須) | JWT署名鍵（HS256、32バイト以上） |
| `MM_APP_JWT_ACCESS_TOKEN_EXPIRY` | `PT10M` | アクセストークン有効期限（ISO-8601 duration） |
| `MM_APP_JWT_REFRESH_TOKEN_EXPIRY` | `P1D` | リフレッシュトークン有効期限 |
| `MM_APP_PASSWORD_BCRYPT_STRENGTH` | `10` | BCryptのストレングス |
| `MM_APP_PASSWORD_MIN_LENGTH` | `8` | パスワード最小長 |
| `MM_APP_LOGIN_ATTEMPT_MAX_FAILURES` | `5` | ログイン失敗ロックまでの回数 |
| `MM_APP_LOGIN_ATTEMPT_LOCK_DURATION` | `PT15M` | ロック時間 |
| `MM_APP_USER_REGISTRATION_TOKEN_EXPIRY` | `PT3H` | 登録トークン有効期限 |
| `MM_APP_USER_REGISTRATION_RATE_LIMIT_MAX_REQUESTS` | `3` | 登録レート制限（回数） |
| `MM_APP_USER_REGISTRATION_RATE_LIMIT_WINDOW` | `PT1H` | 登録レート制限（時間枠） |
| `MM_APP_ADMIN_BOOTSTRAP_EMAIL` / `MM_APP_ADMIN_BOOTSTRAP_PASSWORD` | (空) | 初回起動時作成される初期管理者。いずれか未設定ならブートストラップをスキップ |
| `MM_APP_FRONTEND_BASE_URL` | `http://localhost:8080` | 登録確認メール本文中のリンク生成に使用するフロントエンドのベースURL |
| `MM_APP_DATASOURCE_PATH` | `./data/mastermeister` | 内部H2データベースのファイルパス |
| `MM_APP_DATASOURCE_USERNAME` / `MM_APP_DATASOURCE_PASSWORD` | `sa` / (空) | 内部H2データベースの認証情報 |
| `MM_APP_MAIL_HOST` / `MM_APP_MAIL_PORT` | `localhost` / `1025` | 送信メールサーバ（devはMailPit） |
| `MM_APP_MAIL_USERNAME` / `MM_APP_MAIL_PASSWORD` | (空) | 送信メールサーバの認証情報 |
| `MM_APP_MAIL_SMTP_AUTH` / `MM_APP_MAIL_SMTP_STARTTLS` | `false` / `false` | 送信メールサーバのSMTP認証・STARTTLS |
| `MM_APP_MAIL_FROM` | `no-reply@mastermeister.example` | 送信メールのFromアドレス |
| `SERVER_PORT` | `8080` | Webサーバのポート |

## Flywayマイグレーション

`src/main/resources/db/migration/V{n}__{description}.sql`に追加する。`ddl-auto: validate`のためJPAエンティティ側のスキーマ変更は必ずマイグレーションスクリプトを伴わせること。既存の適用済みスクリプトは変更しない（新しいバージョンを追加する）。

## API仕様書（OpenAPI/Swagger UI）

起動後、`http://localhost:8080/swagger-ui.html`で確認できる（`springdoc-openapi-starter-webmvc-ui`により自動生成、`/api/admin/**`はBearer認証が必要）。

## ビルド・テスト

```bash
./gradlew :backend:build          # ビルド（テスト含む）
./gradlew :backend:test           # テストのみ
./gradlew :backend:bootWar        # フロントエンドを内包した単一WARを生成
```

`bootWar`はfrontendの`npm run build`成果物（`../frontend/dist`）を取り込んで単一WARを生成する（`build.gradle.kts`参照）。

## モジュール構成

- `backend`: 本モジュール（アプリケーション本体）
- `cherry-mustache-core`: メールテンプレートレンダリングに使用する自作Mustacheエンジン（独立したGradleサブプロジェクト。パッケージ名は`cherry.mustache`のまま維持）

詳細は`aidlc-docs/construction/unit-02/code/{repository-layer-summary,business-logic-summary,api-layer-summary}.md`を参照。

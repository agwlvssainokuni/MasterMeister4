# MasterMeister backend

Spring Boot 4.1 / Spring Security 7.x / Java 25製のバックエンド。UNIT-02（ユーザ登録・認証）でユーザ登録・JWT認証・ユーザ管理・監査ログの基盤を、UNIT-03（RDBMSセットアップ）で対象RDBMS接続の登録・管理とスキーマ取込の基盤を、UNIT-04（アクセス制御）でグループ管理・権限設定・実効権限判定の基盤を、UNIT-05（マスタメンテナンス）で一般ユーザ向けのマスタデータ参照・編集機能を、UNIT-06（クエリ保存・実行）で任意SQLの読み取り専用実行・クエリの名前付き保存機能を構築した。

## 起動

開発用にMailPit（メール送信確認用SMTPサーバ）、および対象RDBMS接続の動作確認用にMySQL/MariaDB/PostgreSQLを起動しておく（`../devenv/docker-compose.yml`）。

```bash
docker compose -f ../devenv/docker-compose.yml up -d
```

JWT署名鍵・RDBMS接続パスワード暗号鍵はいずれも必須（デフォルト空文字のため未設定だと起動時に`AppProperties`のコンストラクタ検証で失敗する）。

```bash
export MM_APP_JWT_SECRET="$(openssl rand -base64 32)"
export MM_APP_RDBMS_ENCRYPTION_KEYS="1:$(openssl rand -base64 32)"
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
| `MM_APP_FRONTEND_BASE_URL` | `http://localhost:5173` | 登録確認メール本文中のリンク生成に使用するフロントエンドのベースURL。devはViteのdevサーバ（`npm run dev`）を指す。単一WAR構成で本番稼働させる場合はバックエンドのオリジン（`SERVER_PORT`）を設定する |
| `MM_APP_DATASOURCE_PATH` | `./data/mastermeister` | 内部H2データベースのファイルパス |
| `MM_APP_DATASOURCE_USERNAME` / `MM_APP_DATASOURCE_PASSWORD` | `sa` / (空) | 内部H2データベースの認証情報 |
| `MM_APP_MAIL_HOST` / `MM_APP_MAIL_PORT` | `localhost` / `1025` | 送信メールサーバ（devはMailPit） |
| `MM_APP_MAIL_USERNAME` / `MM_APP_MAIL_PASSWORD` | (空) | 送信メールサーバの認証情報 |
| `MM_APP_MAIL_SMTP_AUTH` / `MM_APP_MAIL_SMTP_STARTTLS` | `false` / `false` | 送信メールサーバのSMTP認証・STARTTLS |
| `MM_APP_MAIL_FROM` | `no-reply@mastermeister.example` | 送信メールのFromアドレス |
| `MM_APP_RDBMS_ENCRYPTION_KEYS` | (空・必須) | 対象RDBMS接続パスワードの暗号鍵（AES-256-GCM）。`keyId:base64key`形式、複数世代をカンマ区切りで指定可能（鍵ローテーション対応。最大の`keyId`が新規暗号化に使われる現在鍵となり、全世代の鍵が復号に使用可能）。各鍵はBase64デコード後32バイト（AES-256）である必要がある。生成例: `openssl rand -base64 32` |
| `SERVER_PORT` | `8080` | Webサーバのポート |
| `MM_LOGGING_LEVEL_ROOT` | `WARN` | ルートロガーのログレベル |
| `MM_LOGGING_LEVEL_APP` | `INFO` | `cherry.mastermeister`配下のログレベル。`TRACE`にするとトレースログ（下記）が有効化される |

## Flywayマイグレーション

`src/main/resources/db/migration/V{n}__{description}.sql`に追加する。`ddl-auto: validate`のためJPAエンティティ側のスキーマ変更は必ずマイグレーションスクリプトを伴わせること。既存の適用済みスクリプトは変更しない（新しいバージョンを追加する）。

## 対象RDBMS接続（UNIT-03）

管理者ダッシュボードの「RDBMS接続設定」画面（`/connections`）から、マスタメンテナンス対象のRDBMS接続を登録・管理する。対応するJDBCドライバはMySQL（`com.mysql:mysql-connector-j`）、MariaDB（`org.mariadb.jdbc:mariadb-java-client`）、PostgreSQL（`org.postgresql:postgresql`）、H2（`com.h2database:h2`、内部DB用と共用）を同梱済み。

- **対象RDBMS接続に使用するDBユーザは、最小権限（本アプリの用途に必要な範囲のみ）で作成することを推奨する**（読取専用のマスタメンテナンスであれば`SELECT`権限のみのユーザを用意する等）。DBユーザの権限設定自体はRDBMS側の運用管理であり、本アプリは指定された認証情報でそのまま接続を試みるのみでアプリケーション側での権限チェック・強制は行わない
- TLS接続はデフォルトで無効。有効化する場合は接続情報の「追加パラメータ」欄に、対象RDBMSの実際のTLS構成に応じたJDBCパラメータ（例: MySQL/MariaDBは`useSSL=true`、PostgreSQLは`sslmode=require`）を指定する

## アクセス制御（UNIT-04）

管理者ダッシュボードの「グループ管理」画面（`/groups`）でユーザグループを作成・管理し、各RDBMS接続の「権限設定」画面（`/permissions/{connectionId}`）でユーザ／グループ単位にスキーマ／テーブル／カラム階層の権限（主権限: NONE/READ/UPDATE、補助権限: CREATE/DELETE）を設定する。実効権限の判定結果（`EffectivePermissionResolver`）はCaffeineでインメモリキャッシュし（`spring.cache.*`、`maximumSize=10000, expireAfterWrite=30m`）、権限変更・グループ変更・スキーマ再取込のたびに全体無効化する。追加の環境変数は不要（キャッシュはアプリ内蔵、外部ミドルウェア不要）。

## マスタメンテナンス（UNIT-05）

一般ユーザ（ロール不問、認証済みであれば誰でも）が、`/master-data`画面から自分がアクセス可能なRDBMS接続・テーブル/ビュー・レコードを参照・編集する。API名前空間は`/api/master-data/**`（`/api/admin/**`とは独立した、本プロジェクト初の非管理者向けトップレベル名前空間）で、既存のSecurityFilterChain設定（`/api/**`→`authenticated()`の汎用ルール）がそのまま適用されるため追加のセキュリティ設定は不要。

- レコード一覧の絞込は、構造化フィルタ（カラム・演算子・値の組）とSQL手入力のWHERE/ORDER BY句を併用可能（AND結合、BR-MASTER-15）。SQL手入力は[JSqlParser](https://github.com/JSQLParser/JSqlParser)でダミーSELECT文へ埋め込みパースし、許可された構文要素（比較演算子・AND/OR・カラム参照・リテラル値のみ）のみで構成されているかを検証したうえで、パラメータ化されたSQLへ再構築する（`RawQueryConditionValidator`）
- 一括反映（作成・更新・削除混在バッチ）はオールオアナッシングで、対象RDBMS用に**リクエストごとに生成する**`DataSourceTransactionManager`＋`TransactionTemplate`で明示的にトランザクション制御する（`RecordBatchService`）。Spring Bootの`@Transactional`は既定でアプリ内部DB用のトランザクションマネージャに紐づき、動的に選択される対象RDBMS用の`DataSource`とは無関係であるため、この方式を採用している
- 一括反映の1リクエストあたりの操作件数上限（既定1,000件）、大量データ取得とみなす閾値（既定100件、監査ログ`MASTER_DATA_BULK_ACCESSED`の記録判定に使用）は、下記環境変数で設定可能

| 環境変数 | デフォルト | 用途 |
|---|---|---|
| `MM_APP_MASTERDATA_BATCH_MAX_SIZE` | `1000` | 一括反映バッチの1リクエストあたりの操作件数上限 |
| `MM_APP_AUDIT_BULK_ACCESS_THRESHOLD` | `100` | `MASTER_DATA_BULK_ACCESSED`監査イベントを記録する結果件数の閾値 |

## クエリ保存・実行（UNIT-06）

一般ユーザ（ロール不問、認証済みであれば誰でも）が、`/query-execution`画面から任意のSELECT文をad-hocで実行し、`/saved-queries`画面から名前を付けて保存・再実行できる。API名前空間は`/api/queries/**`で、UNIT-05と同様、既存のSecurityFilterChain設定（`/api/**`→`authenticated()`の汎用ルール）がそのまま適用されるため追加のセキュリティ設定は不要。

- SQLは`QuerySqlAnalyzer`がJSqlParserで構文解析し、単一のSELECT文（JOIN・サブクエリ・集約関数・UNIONを含みうる）であることのみを検証する（BR-QUERY-01）。UNIT-05のWHERE/ORDER BY句の式レベル許可リスト検証とは異なり、文全体が読み取り専用であることの保証のみが目的
- 名前付きパラメータ（`:name`形式）はJSqlParserのAST走査（`JdbcNamedParameter`ノード収集）で検出し、`NamedParameterJdbcTemplate`でバインドする。文字列リテラル内の`:`を誤検出しない
- アクセス制御はスキーマ単位（実行者が対象接続内のいずれかのテーブル/カラムに実効主権限READ以上を持つスキーマのみ選択可能）で、UNIT-05のようなテーブル/カラム単位の制御は行わない（任意のJOIN・サブクエリを含むSELECT文に対する参照先テーブルの網羅的特定が技術的に大きな負担となるため、BR-QUERY-04）
- 保存クエリ（`SavedQuery`）は保存時点の対象接続に固定されるが、スキーマは固定せず実行のたびに選択する（実行者自身のその時点の実効権限で評価）
- クエリ実行はスキーマ切替（対象RDBMS方言に応じ`RdbmsDialectStrategy.applySchemaSwitch`を適用）した単一の物理JDBC接続上でCOUNT取得→結果取得の順に実行し、ページング無効時も内部的に上限+1件取得して安全上限を超えないか判定する

| 環境変数 | デフォルト | 用途 |
|---|---|---|
| `MM_APP_QUERY_EXECUTION_TIMEOUT_SECONDS` | `30` | クエリ実行のタイムアウト秒数（超過時408 `QUERY_EXECUTION_TIMEOUT`） |
| `MM_APP_QUERY_MAX_RESULT_ROWS` | `10000` | ページング無効時の結果件数上限（超過時400 `QUERY_RESULT_SIZE_EXCEEDED`） |

## クエリビルダー（UNIT-07）

`/query-builder`画面で、タブUI（SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET）によるSQL組み立て・既存SQLからのリバースエンジニアリングができる。API名前空間は`/api/query-builder/**`（3エンドポイント）。接続一覧・スキーマ一覧はUNIT-06の既存API（`/api/queries/connections`・`/api/queries/{connectionId}/schemas`）をそのまま再利用する。UNIT-05の`MasterDataService`には依存せず、UNIT-03の`SchemaIntrospectionService`・UNIT-04の`EffectivePermissionResolver`を直接組み合わせて独自に実装する（`QueryBuilderAccessResolver`）。

- FROM/JOINタブのテーブル候補・他タブのカラム候補は、列単位の実効権限フィルタリングで絞り込む（UNIT-05の`isTableVisible`と同じOR条件、BR-QUERYBUILDER-01）。UNIT-06のad-hoc実行がスキーマ単位の粒度に留めているのとは異なる粒度だが、タブUI上で参照テーブル/カラムが常に明示的に特定できるため技術的制約なく実現できる
- SQL生成（`QueryBuilderService.generateSql`）・リバースエンジニアリング（`parseToBuilderState`）はいずれもJSqlParserのオブジェクトモデル（`PlainSelect`/`Join`/`SelectItem`等）を構築・走査する。WHERE/HAVING比較値は列のデータ型分類に応じた型安全なリテラル（`LongValue`/`StringValue`等）へ変換し、SQL文字列連結によるインジェクションを構造的に防止する
- JOIN種別はINNER/LEFT/RIGHTのみサポート（FULL JOINは対象RDBMS4種のうちMySQL/MariaDBが非対応のため除外）。JOIN条件は構造化された等価結合のみ
- サポート対象外の構文（サブクエリ・UNION・FULL JOIN等）を検出した場合は422、参照するテーブル/カラムへのアクセス権限がない場合は403で、いずれもタブUIへの部分的な反映は行わない（フェイルクローズ、BR-QUERYBUILDER-07）
- 生成したSQLは、UNIT-06のクエリ実行画面・保存クエリ作成/編集画面へそのまま連携できる（逆方向の「クエリビルダーで編集」による相互遷移にも対応）

## クエリ履歴（UNIT-08）

`/query-history`画面で、UNIT-06が記録する`QueryExecutionRecord`（クエリ実行1回分の記録）の閲覧・絞込・ページングができる。API名前空間は`/api/query-history/**`（3エンドポイント: 接続一覧・スキーマ名一覧・履歴一覧）。新規のデータ記録処理は持たない（成功した実行のみが記録対象、UNIT-06の既存設計を踏襲）。

- 接続一覧・スキーマ名一覧はいずれも「現在アクセス可能なもの」ではなく、履歴に実際に記録された実績ベースで返す（BR-QUERYHISTORY-10・11）。閲覧時点でアクセス権を失った接続・スキーマの履歴も、記録の不変性の原則（UNIT-02監査ログと同じ考え方）に基づき閲覧可能なため
- 実行者スコープ（「全ユーザ」／「自分のみ」）は管理者のみ「全ユーザ」を選択可能。一般ユーザが指定してもサーバ側で「自分のみ」に強制する（フェイルクローズ、BR-QUERYHISTORY-03）。この判定はController層でのみ行い、Service層にはロールではなく絞込済みの`executedByFilter`（`Long`、nullなら全ユーザ対象）のみを渡す
- 絞込条件（実行日時範囲・実行者スコープ・対象スキーマ・SQLテキスト）はSpring Data JPAの`Specification`（`QueryHistorySpecifications`）で動的に組み立てる（プロジェクト内初のSpecification API採用）。ページングは同じく標準の`Pageable`/`Page`を使用（UNIT-06のクエリ実行結果ページング（サブクエリラップ方式）とは別の仕組み）
- `query_execution_record`テーブルに`(connection_id, executed_at)`の複合インデックス（V17）を追加し、本ユニットの主要アクセスパターンに最適化した

## 監査ログ閲覧（UNIT-09）

`/audit-log`画面で、UNIT-02〜08が記録する`AuditLogEntry`（監査ログ）の閲覧・絞込・ページングができる。API名前空間は`/api/admin/audit-log`（単一エンドポイント）。管理者専用であり、既存の`SecurityConfig`の`/api/admin/**`ルールでエンドポイント全体を保護する（UNIT-08のようなロールに応じたデータ絞込は不要、アクセスできた時点で全件が閲覧対象）。

- 絞込条件（発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータス）はSpring Data JPAの`Specification`（`AuditLogSpecifications`）で動的に組み立てる（UNIT-08で確立した`Specification` API採用パターンを踏襲）。ページングは同じく標準の`Pageable`/`Page`を使用
- `audit_log_entry`テーブルに`(connection_id, occurred_at)`の複合インデックス（V18）を追加した（既存は`occurred_at`・`event_type`・`user_id`の単独インデックスのみで、`connection_id`にインデックスがなかったため）
- 対象ユーザ・対象接続の表示名は`findAllById`による一括解決（キャッシュなし、UNIT-08と同じ方式）。削除済みユーザ・接続は「(不明なユーザ)」「(削除済み接続)」のプレースホルダー表示とする
- 監査ログの閲覧自体（大量閲覧含む）は新たな監査記録対象としない。UNIT-05の`MASTER_DATA_BULK_ACCESSED`（一般ユーザ向け機能の大量アクセス検知）とは異なり、本ユニットは管理者専用機能であり全件閲覧が通常の業務行為であるため

## トレースログ

`TraceAspect`（`cherry.mastermeister.common.aop`、`reference/trace/TraceAspect.java`を移植）が、`cherry.mastermeister`配下（`common.config`を除く）の全メソッドの呼び出し・復帰・例外を`Spring`の`CustomizableTraceInterceptor`経由でログ出力する。実際に出力されるのは`MM_LOGGING_LEVEL_APP=TRACE`のとき（既定値`INFO`では無効）。設定値は`mm.app.trace.*`（`AppProperties.Trace`）で調整可能。

**注意（機微情報）**: `CustomizableTraceInterceptor`はメソッドの引数・戻り値をそのまま`toString()`してログ出力するため、TRACE有効時は認証関連処理（ログインのパスワード平文、発行したJWT/リフレッシュトークン等）がログに残る。トラブルシューティング等で一時的に有効化する用途に限定し、本番環境・共有ログでは常時有効化しないこと。

## Spring Boot Actuator

`spring-boot-starter-actuator`を有効化済み。`http://localhost:8080/actuator/health`は未認証で疎通確認用に公開する（ロードバランサ等のヘルスチェック向け、詳細情報は含まない`{"status":"UP"}`相当のみ）。それ以外のActuatorエンドポイント（`/actuator/metrics`等）は内部状態を露出しうるため、`/api/admin/**`と同様にADMINロール必須（`SecurityConfig`）。`health`の詳細情報（DB接続・ディスク容量・メール送信先等）も`management.endpoint.health.show-details: when-authorized`＋`roles: ADMIN`により、ADMINロールで認証済みの場合のみ表示される。

公開するエンドポイントは`management.endpoints.web.exposure.include`（`application.yml`）で調整する。既定は`health,info,metrics`のみ（`env`・`beans`・`heapdump`等、機微情報や内部構造を露出するエンドポイントは含めていない）。

## OpenTelemetry（トレース・メトリクス・ログ）

`spring-boot-starter-opentelemetry`（Micrometer Tracing + OTLPエクスポート）を追加済み。デフォルトでは無効（既存の開発・テスト・CI環境への影響を避けるため）で、以下の環境変数を設定すると有効化する。

| 環境変数 | デフォルト | 用途 |
|---|---|---|
| `MM_TRACING_ENABLED` | `false` | トレースのエクスポートを有効化する |
| `MM_TRACING_SAMPLING_PROBABILITY` | `1.0` | トレースのサンプリング確率（開発用に全件サンプリングを既定値とする） |
| `MM_OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLPトレース送信先 |
| `MM_OTLP_METRICS_ENABLED` | `false` | メトリクスのOTLPエクスポートを有効化する |
| `MM_OTLP_METRICS_ENDPOINT` | `http://localhost:4318/v1/metrics` | OTLPメトリクス送信先 |
| `MM_OTLP_METRICS_STEP` | `15s` | メトリクスのエクスポート間隔 |
| `MM_OTLP_LOGGING_ENABLED` | `false` | ログのOTLPエクスポートを有効化する |
| `MM_OTLP_LOGGING_ENDPOINT` | `http://localhost:4318/v1/logs` | OTLPログ送信先 |

ログのOTLPエクスポートには`OpenTelemetryLoggingConfig`＋`logback-spring.xml`のアペンダ登録が必要（`spring-boot-starter-opentelemetry`はSDK側のロガープロバイダ・エクスポータのみを構成し、Logbackのログイベントをそこへ橋渡しするアペンダ（`io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`）は含まないため別途追加）。なお同アペンダが推移的に要求する`opentelemetry-api-incubator`はSpring Boot管理下のOTel SDK本体（1.62.0系）よりも新しいalpha版を要求してくるため、`build.gradle.kts`で明示的に1.62.0-alphaへ強制している（バージョン不一致による`NoClassDefFoundError`対策）。

受信・可視化環境（Grafana + Tempo + Prometheus + Loki + OpenTelemetry Collector、`devenv/`とは独立）は`../observability/`を参照。

```bash
docker compose -f ../observability/docker-compose.yml up -d
export MM_TRACING_ENABLED=true
export MM_OTLP_METRICS_ENABLED=true
export MM_OTLP_LOGGING_ENABLED=true
./gradlew :backend:bootRun
```

## API仕様書（OpenAPI/Swagger UI）

起動後、`http://localhost:8080/swagger-ui.html`で確認できる（`springdoc-openapi-starter-webmvc-ui`により自動生成、`/api/admin/**`はBearer認証＋ADMINロールが、`/api/master-data/**`・`/api/queries/**`はBearer認証のみ（ロール不問）が必要）。

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

詳細は`aidlc-docs/construction/unit-0{2,3,4,5}/code/{repository-layer-summary,data-access-layer-summary,business-logic-summary,api-layer-summary}.md`を参照。

## CI/CD（UNIT-10）

GitHub Actionsで構成する（NFR-10.1〜10.3）。

- **CI**（`.github/workflows/ci.yml`）: `main`ブランチへのpush・プルリクエストをトリガーに、`backend`（`./gradlew build`、`cherry-mustache-core`含む）・`frontend`（lint/test/build）を並行実行する。OWASP Dependency-Checkは`NVD_API_KEY`シークレットが設定されている場合のみ実行し、未設定時はスキップする（NFR-4.4、`continue-on-error: true`）
- **Release**（`.github/workflows/release.yml`）: `v*`形式のタグpushをトリガーに、`./gradlew bootWar`でビルドしGitHub Releasesを作成する（NFR-10.3）。**タグ名（`v`を除いた値）と`build.gradle.kts`の`version`が一致しない場合はビルド前にリリースを中断する**（バージョン整合性チェック）。リリース前には`build.gradle.kts`の`version`をリリース対象のバージョンに更新し、対応するタグ（例: `v0.0.0`）をpushすること

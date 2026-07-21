# UNIT-03 RDBMSセットアップ - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-2.1（対象RDBMS接続の登録・管理）, STORY-2.2（スキーマ取込）
- **対応要件**: FR-2.1, FR-2.2
- **対応コンポーネント**: COMP-07（RdbmsConnectionService）, COMP-08（SchemaIntrospectionService）, COMP-09（RdbmsDialectStrategy）
- **前提ユニット**: UNIT-01（design-systemコンポーネント）, UNIT-02（JWT認証・ロールチェック、AuditEventPublisher/AuditLogService、AppPropertiesパターン）
- **依存関係**: 本ユニットはUNIT-02の`SecurityFilterChain`（`/api/admin/**`）、`AuditEventPublisher`、`AuditLogEntry`（`connectionId`列は既存）、`AppProperties`パターンをそのまま利用・拡張する。新規のセキュリティ設定・監査ログ基盤は作らない
- **本ユニットが所有するデータ**: `RdbmsConnection`, `SchemaSnapshot`, `SchemaTable`, `SchemaColumn`, `SchemaConstraint`
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時に発見した訂正事項

- フロントエンドの実装場所は、Functional Designで想定していた新規モジュール`frontend/src/rdbms-connection/`ではなく、UNIT-02実装で実際に確立された`frontend/src/pages/`・`frontend/src/api/`のフラット構成に従う
- 画面パスは`/rdbms-connections`ではなく、UNIT-01由来の`design-system/components/navigation.ts`（`NAV_ROUTES`）に既に予約済みの`/connections`（key: `connections`）を使用する。i18nラベル（`nav.connections`, `home.card.connections`）も既存のものをそのまま利用する
- 詳細はfunctional-design/frontend-components.mdの訂正注記を参照

## 計画チェックリスト

### 1. Build Configuration

- [x] Step 1.1: `backend/build.gradle.kts`にJDBCドライバを追加する: `runtimeOnly("com.mysql:mysql-connector-j:...")`, `runtimeOnly("org.mariadb.jdbc:mariadb-java-client:...")`, `runtimeOnly("org.postgresql:postgresql:...")`（バージョンは追加時点の最新安定版を確認）。H2は既存の`runtimeOnly("com.h2database:h2")`を対象RDBMS接続でも共用する（tech-stack-decisions.md §8）— WebSearchで確認の上、mysql-connector-j:9.7.0, mariadb-java-client:3.5.9, postgresql:42.7.13を追加
- [x] Step 1.2: `backend/src/main/resources/application.yml`に`mm.app.rdbms.encryption-keys`を追加する（`MM_APP_RDBMS_ENCRYPTION_KEYS`環境変数プレースホルダー、必須項目）
- [x] Step 1.3: `AppProperties`（`cherry.mastermeister.common.config`）を拡張する。新規ネストrecord`Rdbms(String encryptionKeys)`を追加し、コンストラクタで検証を行う: 最低1件の鍵、`keyId:base64key`形式のパース可否、`keyId`重複なし、各鍵がBase64デコード後32バイト（AES-256）であること（logical-components.md §3、Q3=A fail-fast）。既存の`new AppProperties(...)`呼び出し箇所（テストファイル含む）に引数を追加する — `parsedEncryptionKeys()`でパース結果（`List<EncryptionKey>`）を都度取得する設計とした（単一環境変数からのSpring標準プロパティバインディングを維持するため、フィールド自体はStringのまま保持）。5件のテストファイルに`new AppProperties.Rdbms("1:...")`を追加、`./gradlew :backend:compileJava :backend:compileTestJava`で成功確認

### 2. Database Migration Scripts

- [ ] Step 2.1: `backend/src/main/resources/db/migration/V7__create_rdbms_connection_table.sql`を作成する（domain-entities.md §1、`encryption_key_id`列を含む）
- [ ] Step 2.2: `V8__create_schema_snapshot_table.sql`を作成する（domain-entities.md §2、`connection_id`を主キー兼外部キーとする）
- [ ] Step 2.3: `V9__create_schema_table_table.sql`を作成する（domain-entities.md §2.1）
- [ ] Step 2.4: `V10__create_schema_column_table.sql`を作成する（domain-entities.md §2.2）
- [ ] Step 2.5: `V11__create_schema_constraint_table.sql`を作成する（domain-entities.md §2.3、`column_names`/`referenced_columns`は複数値のためカンマ区切り文字列または別テーブルのいずれかで表現する。Code Generation時点でシンプルなカンマ区切り文字列カラムとして実装する）
- [ ] Step 2.6: 既存の`AuditEventType`（`cherry.mastermeister.audit.entity`）に`CONNECTION_REGISTERED`, `CONNECTION_UPDATED`, `CONNECTION_DELETED`, `SCHEMA_IMPORTED`を追加する（domain-entities.md §3。`audit_log_entry.connection_id`は既存カラムのため追加マイグレーション不要）
- [ ] Step 2.7: **検証チェックポイント**: Flywayマイグレーションが後続のRepository層テスト実行時に正常適用されることを確認する

### 3. Repository Layer Generation

- [ ] Step 3.1: enumを作成する: `DbType`, `TableType`, `NormalizedType`, `ConstraintType`（`cherry.mastermeister.rdbmsconnection.entity`）
- [ ] Step 3.2: JPAエンティティを作成する: `RdbmsConnection`, `SchemaSnapshot`, `SchemaTable`, `SchemaColumn`, `SchemaConstraint`（`cherry.mastermeister.rdbmsconnection.entity`、domain-entities.md §1〜2.3の属性を反映。`SchemaSnapshot`→`SchemaTable`→`SchemaColumn`/`SchemaConstraint`は`cascade=ALL, orphanRemoval=true`の`@OneToMany`で全置換（BR-RDBMS-08）を表現する）
- [ ] Step 3.3: Spring Data JPAリポジトリを作成する: `RdbmsConnectionRepository`, `SchemaSnapshotRepository`（`cherry.mastermeister.rdbmsconnection.repository`）
- [ ] Step 3.4: **検証チェックポイント**: `@DataJpaTest`で基本CRUD・カスケード削除・全置換（既存スナップショット削除→新規保存）を検証するテストを作成する

### 4. Repository Layer Summary

- [ ] Step 4.1: `aidlc-docs/construction/unit-03/code/repository-layer-summary.md`を作成する（エンティティ・リポジトリ・マイグレーション一覧、テスト結果）

### 5. Business Logic Generation

- [ ] Step 5.1: `ConnectionCredentialCipher`（`cherry.mastermeister.rdbmsconnection`）を作成する（AES-256-GCM、`AppProperties.Rdbms`から鍵一覧をロード、`encrypt()`/`decrypt(keyId)`、logical-components.md §1）
- [ ] Step 5.2: `RdbmsDialectStrategy`インターフェースと実装群（`MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgresDialectStrategy`, `H2DialectStrategy`）を作成する（`cherry.mastermeister.rdbmsconnection.dialect`。`requiresSchemaSwitch()`, `applySchemaSwitch()`, `resolveDialect()`, `buildJdbcUrl()`をnfr-design/logical-components.md §1のとおり実装。ポート番号のデフォルト値定数（3306/3306/5432/9092）もここに保持し、後述のAPIレスポンスまたはフロントエンドの静的定義のいずれかから参照できるようにする）
- [ ] Step 5.3: `RdbmsConnectionService`（COMP-07、`cherry.mastermeister.rdbmsconnection`）を作成する（登録・更新・削除・接続テスト（保存済み/未保存）・`getDataSource()`内部DataSourceキャッシュ（`ConcurrentHashMap`、HikariCP `maximumPoolSize=5`/`minimumIdle=0`/`connectionTimeout=5000`）、business-logic-model.md §1・§2・§4、BR-RDBMS-01〜05・09〜12）
- [ ] Step 5.4: `SchemaIntrospectionService`（COMP-08、`cherry.mastermeister.rdbmsconnection`）を作成する（`CompletableFuture.orTimeout(60秒)`によるタイムアウト制御、タイムアウト時のConnection強制close、JDBC `DatabaseMetaData`によるテーブル/カラム/制約読取、オールオアナッシング・全置換、business-logic-model.md §3、BR-RDBMS-06〜08）
- [ ] Step 5.5: `RdbmsConnectionService`・`SchemaIntrospectionService`から`AuditEventPublisher`（UNIT-02既存）経由で`CONNECTION_REGISTERED`/`CONNECTION_UPDATED`/`CONNECTION_DELETED`/`SCHEMA_IMPORTED`イベントを発行する処理を組み込む（domain-entities.md §3のuserId/targetResource/detail対応表のとおり）

### 6. Business Logic Unit Testing

- [ ] Step 6.1: `ConnectionCredentialCipherTest`を作成する（暗号化・復号往復、複数世代の鍵での復号、`keyId`不明時のエラー）
- [ ] Step 6.2: `RdbmsDialectStrategyTest`を作成する（4方言の`buildJdbcUrl()`出力形式、`requiresSchemaSwitch()`の真偽、`resolveDialect()`のファクトリ挙動）
- [ ] Step 6.3: `RdbmsConnectionServiceTest`を作成する（Mockito。登録・更新・削除・DataSourceキャッシュのライフサイクル（更新/削除時の`close()`呼び出し）、BR-RDBMS-01〜02・09の境界値）
- [ ] Step 6.4: `SchemaIntrospectionServiceTest`を作成する（正常系の全置換、一部テーブル読取失敗時のオールオアナッシング、タイムアウト時のConnection強制close確認）

### 7. Business Logic Summary

- [ ] Step 7.1: `aidlc-docs/construction/unit-03/code/business-logic-summary.md`を作成する（作成したサービス一覧、責務、Part 1計画からの実装時判断（暗号鍵設定のパース方式等）、テスト結果）

### 8. API Layer Generation

- [ ] Step 8.1: DTOを作成する（`cherry.mastermeister.rdbmsconnection.dto`）: `RdbmsConnectionRequest`（Bean Validation付与）, `ConnectionTestRequest`, `RdbmsConnectionResponse`, `RdbmsConnectionSummaryResponse`（パスワード非公開、BR-RDBMS-12）, `ConnectionTestResult`, `SchemaSnapshotResponse`/`SchemaTableResponse`/`SchemaColumnResponse`/`SchemaConstraintResponse`
- [ ] Step 8.2: `RdbmsConnectionController`（`cherry.mastermeister.rdbmsconnection.api`）を作成する（`GET/POST/PUT/DELETE /api/admin/rdbms-connections[/{id}]`, `POST /api/admin/rdbms-connections/{id}/test`, `POST /api/admin/rdbms-connections/test`, `POST /api/admin/rdbms-connections/{id}/schema-refresh`, `GET /api/admin/rdbms-connections/{id}/schema`。既存の`SecurityConfig`の`/api/admin/**`パターンに含まれるため追加のセキュリティ設定は不要）
- [ ] Step 8.3: OpenAPI/Swagger UIへの反映を確認する（既存の`springdoc-openapi-starter-webmvc-ui`により自動生成されることを確認するのみ、追加実装不要）

### 9. API Layer Unit Testing

- [ ] Step 9.1: `@WebMvcTest`で`RdbmsConnectionController`のテストを作成する（管理者ロールチェック（非ADMIN/未認証で403/401）、バリデーションエラー、レスポンスにパスワードが含まれないことの確認、接続テスト・スキーマ取込のエラー分類レスポンス）

### 10. API Layer Summary

- [ ] Step 10.1: `aidlc-docs/construction/unit-03/code/api-layer-summary.md`を作成する（エンドポイント一覧、テスト結果）

### 11. Frontend Components Generation

- [ ] Step 11.1: APIクライアント（`frontend/src/api/rdbmsConnections.ts`）を作成する（一覧・登録・更新・削除・接続テスト（保存済み/未保存）・スキーマ取込・スキーマ取得の各関数、`http.ts`共通ラッパー利用）
- [ ] Step 11.2: `RdbmsConnectionListPage`（`frontend/src/pages/`）を作成する（frontend-components.md §1、一覧・登録編集フォームModal・削除確認・接続テスト/スキーマ取込アクション、`dbType`選択時のデフォルトポート自動入力・方言別`additionalParams`ヘルプテキスト出し分け）
- [ ] Step 11.3: `SchemaDetailPage`（`frontend/src/pages/`）を作成する（frontend-components.md §2、テーブル一覧・カラム一覧の2段DataTable）
- [ ] Step 11.4: `App.tsx`のルーティングに`/connections`（`RdbmsConnectionListPage`、`ProtectedRoute`配下）・`/connections/:id/schema`（`SchemaDetailPage`、同）を追加する
- [ ] Step 11.5: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'connections'`を追加する（frontend-components.md §3訂正版）

### 12. Frontend Components Unit Testing

- [ ] Step 12.1: `RdbmsConnectionListPage`・`SchemaDetailPage`のテストを作成する（Vitest + RTL、フォーム操作・バリデーション・API呼び出しモック・デフォルトポート自動入力）
- [ ] Step 12.2: `rdbmsConnections.ts`（APIクライアント）のテストを作成する

### 13. Frontend Components Summary

- [ ] Step 13.1: `aidlc-docs/construction/unit-03/code/frontend-summary.md`を作成する（作成した画面・コンポーネント一覧、テスト結果）

### 14. Documentation Generation

- [ ] Step 14.1: `backend/README.md`を更新する（`MM_APP_RDBMS_ENCRYPTION_KEYS`環境変数、JDBCドライバ追加、対象RDBMS接続用DBユーザの最小権限推奨に関する注記、NFR-03-07）
- [ ] Step 14.2: `frontend/README.md`を更新する（新規ページ・ルーティングの追記、必要であれば）

### 15. Deployment Artifacts

- [ ] Step 15.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に必要な対象RDBMS（MySQL/MariaDB/PostgreSQL）が既存構成（本セッション冒頭で整備済み）でそのまま利用できることを確認する（新規追加は不要見込み）

### 16. 最終ビルド検証

- [ ] Step 16.1: **検証チェックポイント**: `./gradlew :backend:build`（全ユニットテスト成功）、`./gradlew :backend:test`、`npm test`（frontend）、`npm run build`（frontend）がすべて成功することを確認する
- [ ] Step 16.2: `./gradlew :backend:bootWar`で統合WARを生成し、`java -jar`起動。devenvのMySQL/MariaDB/PostgreSQL（サンプルデータ投入済み）それぞれへの接続登録・接続テスト・スキーマ取込を行い、`categories`/`products`テーブルの構造が正しく取り込まれることをスキーマ詳細画面で確認する
- [ ] Step 16.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）を実行する（UNIT-02同様、NVD APIキー未設定の場合は既知の制約として記録し実施見送りとする）

---

## Story Traceability

- STORY-2.1（対象RDBMS接続の登録・管理）: Step 3, 5.3, 8, 11.2 で実装
- STORY-2.2（スキーマ取込）: Step 3, 5.4, 8, 11.3 で実装

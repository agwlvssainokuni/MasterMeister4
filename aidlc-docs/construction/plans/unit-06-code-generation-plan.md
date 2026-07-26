# UNIT-06 クエリ保存・実行 - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-6.1（保存クエリの作成・公開範囲）, STORY-6.2（保存クエリの実行・編集・非表示化）, STORY-7.1（SQL入力・読み取り専用検証・パラメータ）, STORY-7.2（実行対象接続・スキーマ指定）, STORY-7.3（結果表示・履歴記録）
- **対応要件**: FR-6.1〜FR-6.6, FR-7.1〜FR-7.9
- **対応コンポーネント**: COMP-14（`QueryExecutionService`）, COMP-15（`SavedQueryService`）。nfr-design/logical-components.md §1参照
- **前提ユニット**: UNIT-01（design-systemコンポーネント）, UNIT-02（JWT認証、`AuditEventPublisher`/`AuditEventType`、`GlobalExceptionHandler`、SecurityFilterChain）, UNIT-03（`RdbmsConnectionService.getDataSource()`、`RdbmsDialectStrategy.applySchemaSwitch`/`requiresSchemaSwitch`）, UNIT-04（`EffectivePermissionResolver.resolvePrimary`）, UNIT-05（JSqlParser依存関係、`ApiException`パターン）
- **依存関係**: 対象RDBMSへの動的アクセスはUNIT-03の`RdbmsConnectionService.getDataSource(connectionId)`（HikariCP）を経由し、`NamedParameterJdbcTemplate`で実行する
- **本ユニットが所有するデータ**: 内部DBに新規永続化する`SavedQuery`（保存クエリ本体）と`QueryExecutionRecord`（実行記録）。対象RDBMS側のデータ（SELECT結果）自体は永続化しない
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時の実装判断

- パッケージ構成は`cherry.mastermeister.query`単一（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。エンティティは`{package}.entity`、リポジトリは`{package}.repository`、サービスクラスはパッケージ直下、DTOは`{package}.dto`、コントローラは`{package}.api`に配置する
- `saved_query.connection_id`は`rdbms_connection`への外部キー制約（`ON DELETE CASCADE`）を設ける。UNIT-04の`access_permission`と同様、`SavedQuery`は接続が存在する前提で意味を持つ生きたリソースであり、対象接続が削除されれば連動して削除されるべきと判断する
- `query_execution_record`の`connection_id`・`saved_query_id`は外部キー制約を設けない。UNIT-02の`audit_log_entry`と同じ理由（対象リソースのライフサイクル変更が実行履歴に影響しないようにするため）で、`QueryExecutionRecord`は監査ログに準じた履歴記録として扱う
- `saved_query.sql`・`query_execution_record.sql`・`query_execution_record.params`は`@Lob`（CLOB/TEXT相当、方言ごとにH2はCLOB・PostgreSQLはTEXT・MySQL/MariaDBはLONGTEXTへ解決）とする。既存のテキスト系カラム（`audit_log_entry.detail VARCHAR(2000)`等）はいずれも短い要約用の固定長だが、本ユニットで初めて任意長のユーザ入力SQL文自体を永続化するため、固定長VARCHARによる切り詰め・保存失敗リスクを避ける（レビュー指摘の反映）
- スキーマ切替＋クエリ実行の接続管理は`SingleConnectionDataSource`（`suppressClose=true`）によるラップ方式（nfr-design-patterns.md §2.1）。`DataSourceTransactionManager`は導入しない
- SQL読み取り専用検証・パラメータ検出は`QuerySqlAnalyzer`（1クラス、1回のJSqlParser解析結果を両方の用途で再利用）で実装する（nfr-design-patterns.md §3.1）
- Controllerは`QueryController`（接続一覧・スキーマ一覧・ad-hoc実行）と`SavedQueryController`（保存クエリCRUD・実行・非表示化）の2つに分割する（nfr-design/logical-components.md §1、Q7=B）
- 本ユニットもPBT対象プロパティ（business-logic-model.md §9: SQL読み取り専用検証、パラメータ検出、スキーマ許可リスト判定）を持つため、jqwikによるプロパティベーステストを実装する
- フロントエンドはFlow A（保存クエリ管理、既存ナビ項目`savedQueries`）とFlow B（ad-hocクエリ実行、新規ナビ項目`queryExecution`）の2フローを実装する（frontend-components.md）

## 計画チェックリスト

### 1. Build Configuration

- [ ] Step 1.1: `backend/src/main/resources/application.yml`に`mm.app.query.execution-timeout-seconds`（デフォルト30）・`mm.app.query.max-result-rows`（デフォルト10000）を追加する（logical-components.md §3）
- [ ] Step 1.2: `AppProperties`に新規レコード`Query(int executionTimeoutSeconds, int maxResultRows)`を追加する（`Masterdata`/`Audit`と同様のバリデーション付きコンパクトコンストラクタ）

### 2. Database Migration Scripts

- [ ] Step 2.1: `V15__create_saved_query_table.sql`を作成する（`saved_query`テーブル: `id`, `connection_id`（FK、ON DELETE CASCADE）, `name`, `sql`（CLOB/TEXT相当）, `visibility`, `created_by`, `retired`, `created_at`, `updated_at`。domain-entities.md §1）
- [ ] Step 2.2: `V16__create_query_execution_record_table.sql`を作成する（`query_execution_record`テーブル: `id`, `executed_by`, `connection_id`（FK制約なし）, `schema_name`, `sql`（CLOB/TEXT相当）, `params`（CLOB/TEXT相当）, `saved_query_id`（nullable、FK制約なし）, `row_count`, `duration_millis`, `executed_at`。domain-entities.md §2）
- [ ] Step 2.3: 既存の`AuditEventType`（`cherry.mastermeister.audit.entity`）に`QUERY_EXECUTED`, `QUERY_SAVED`, `QUERY_UPDATED`, `QUERY_RETIRED`を追加する（domain-entities.md §6。`audit_log_entry.connection_id`は既存カラムのため追加マイグレーション不要）
- [ ] Step 2.4: **検証チェックポイント**: Flywayマイグレーションが後続のRepository層テスト実行時に正常適用されることを確認する（Step 3.4で実施）

### 3. Repository Layer Generation

- [ ] Step 3.1: enumを作成する: `Visibility`（`cherry.mastermeister.query.entity`）
- [ ] Step 3.2: JPAエンティティ`SavedQuery`・`QueryExecutionRecord`（`cherry.mastermeister.query.entity`）を作成する（domain-entities.md §1〜2の属性。`sql`/`params`フィールドは`@Lob`を付与する）
- [ ] Step 3.3: Spring Data JPAリポジトリを作成する: `SavedQueryRepository`（`cherry.mastermeister.query.repository`、`findAllByConnectionId`等）, `QueryExecutionRecordRepository`（同、`save`のみが主用途）
- [ ] Step 3.4: **検証チェックポイント**: リポジトリの基本CRUD操作をH2（テスト用インメモリDB）で確認する

### 4. Repository Layer Summary

- [ ] Step 4.1: `aidlc-docs/construction/unit-06/code/repository-layer-summary.md`を作成する（作成したエンティティ・リポジトリ一覧、マイグレーション内容、テスト結果）

### 5. Business Logic Generation

- [ ] Step 5.1: `QuerySqlAnalyzer`（`cherry.mastermeister.query`）を作成する（JSqlParserによる1回の構文解析、`isReadOnly()`・`detectParameters()`の2メソッド、nfr-design-patterns.md §3.1）
- [ ] Step 5.2: 新規例外を作成する: `QuerySchemaNotAccessibleException`（403）, `NonReadOnlyQueryException`（400）, `SavedQueryNotAccessibleException`（404）, `QueryExecutionTimeoutException`（408）, `QueryResultSizeExceededException`（400）（`cherry.mastermeister.common.exception`、既存パッケージ規約に合わせる）
- [ ] Step 5.3: `QueryExecutionService`（`cherry.mastermeister.query`、COMP-14）を作成する（`execute`/`executeSavedQuery`。接続確立・スキーマ切替（`SingleConnectionDataSource`）→スキーマ許可リスト判定→`QuerySqlAnalyzer`検証→パラメータバインド実行・ページング（サブクエリラップ＋LIMIT/OFFSET、COUNT取得、結果件数上限）→`QueryExecutionRecord`永続化・`QUERY_EXECUTED`監査ログ記録、business-logic-model.md §1〜6）
- [ ] Step 5.4: `SavedQueryService`（`cherry.mastermeister.query`、COMP-15）を作成する（`saveQuery`/`updateQuery`/`retireQuery`/`getSavedQuery`/`listSavedQueries`。BR-QUERY-05〜09のアクセス可否判定、`QUERY_SAVED`/`QUERY_UPDATED`/`QUERY_RETIRED`監査ログ記録、business-logic-model.md §7〜8）

### 6. Business Logic Unit Testing

- [ ] Step 6.1: `QuerySqlAnalyzerTest`を作成する（許可SQL（単一SELECT、JOIN・サブクエリ・集約関数を含む）の受理、禁止SQL（INSERT/UPDATE/DELETE/DDL・複数ステートメント・パース不能）の拒否、パラメータ検出（文字列リテラル内の`:`除外）を確認）
- [ ] Step 6.2: `QueryExecutionServiceTest`を作成する（Mockito＋H2実テーブル併用。スキーマ許可リスト判定、ページング・COUNT取得、結果件数上限超過時の`QueryResultSizeExceededException`、保存クエリ経由実行時のアクセス可否判定）
- [ ] Step 6.3: `SavedQueryServiceTest`を作成する（Mockito。BR-QUERY-05〜09のアクセス可否判定境界値、編集・非表示化の作成者限定チェック）
- [ ] Step 6.4: `QuerySqlAnalyzerPropertyTest`をjqwikで作成する（business-logic-model.md §9: 任意のSELECT文は常に受理、任意のINSERT/UPDATE/DELETE/DDL文・複数ステートメントは常に拒否、任意個数の`:param`トークンが過不足なく検出される、という性質）

### 7. Business Logic Summary

- [ ] Step 7.1: `aidlc-docs/construction/unit-06/code/business-logic-summary.md`を作成する（作成したサービス一覧、責務、PBTプロパティ実装内容、テスト結果）

### 8. API Layer Generation

- [ ] Step 8.1: DTOを作成する（`cherry.mastermeister.query.dto`: `AccessibleConnectionResponse`, `AccessibleSchemaResponse`, `QueryExecutionRequest`, `QueryResultResponse`, `SavedQueryRequest`, `SavedQuerySummaryResponse`, `SavedQueryExecutionRequest`。logical-components.md §1）
- [ ] Step 8.2: `QueryController`（`cherry.mastermeister.query.api`）を作成する（接続一覧・スキーマ一覧・ad-hoc実行の3エンドポイント）
- [ ] Step 8.3: `SavedQueryController`（`cherry.mastermeister.query.api`）を作成する（保存クエリの一覧・取得・作成・更新・実行・非表示化の6エンドポイント）
- [ ] Step 8.4: `GlobalExceptionHandler`への追加要否を確認する（Step 5.2の5例外はいずれも`ApiException`のサブクラスのため、UNIT-05の前例（Step 8.3訂正）通り既存の汎用`@ExceptionHandler(ApiException.class)`で処理される可能性が高い。実装時に確認する）
- [ ] Step 8.5: SecurityFilterChain設定への`/api/queries/**`ルール追加要否を確認する（nfr-design-patterns.md §3.2。UNIT-05のCode Generationで判明した前例（既存の`/api/**`→`authenticated()`という汎用ルールが既にカバーしていた）を踏まえ、実装時に確認する）
- [ ] Step 8.6: OpenAPI/Swagger UIへの反映を確認する（既存の自動生成のみ、追加実装不要）

### 9. API Layer Unit Testing

- [ ] Step 9.1: `@WebMvcTest`で`QueryControllerTest`を作成する（一般ユーザ（非ADMIN）でもアクセス可能なことの確認、ad-hoc実行のバリデーション・エラー応答）
- [ ] Step 9.2: `@WebMvcTest`で`SavedQueryControllerTest`を作成する（CRUD・実行・非表示化、作成者以外による編集/非表示化の403相当拒否、非公開/非表示化クエリへのアクセス拒否）

### 10. API Layer Summary

- [ ] Step 10.1: `aidlc-docs/construction/unit-06/code/api-layer-summary.md`を作成する（エンドポイント一覧、テスト結果）

### 11. Frontend Components Generation

- [ ] Step 11.1: `frontend/src/design-system/components/navigation.ts`の`NAV_ROUTES`に新規ナビ項目`{ key: 'queryExecution', labelKey: 'nav.queryExecution', path: '/query-execution' }`を`savedQueries`の直前に追加する（frontend-components.md ナビゲーション節）
- [ ] Step 11.2: APIクライアント`frontend/src/api/query.ts`を作成する（接続一覧・スキーマ一覧・ad-hoc実行・保存クエリCRUD・保存クエリ実行・非表示化の各関数）
- [ ] Step 11.3: `SavedQueryConnectionListPage`（`frontend/src/pages/`）を作成する（frontend-components.md A-1）
- [ ] Step 11.4: `SavedQueryListPage`（`frontend/src/pages/`）を作成する（A-2、FilterBar・DataTable・「追加」ボタン）
- [ ] Step 11.5: `SavedQueryEditorPage`（`frontend/src/pages/`）を作成する（A-3/A-4共用、`mode='new'|'existing'`。スキーマセレクタ・SQL入力欄・パラメータフォーム・ページング設定・実行・保存/更新/非表示化、router stateからのprefill対応）
- [ ] Step 11.6: `QueryExecutionConnectionListPage`（`frontend/src/pages/`）を作成する（B-1）
- [ ] Step 11.7: `QueryExecutionPage`（`frontend/src/pages/`）を作成する（B-2、「名前を付けて保存」でA-3へrouter state経由の遷移）
- [ ] Step 11.8: `App.tsx`のルーティングに`/saved-queries`, `/saved-queries/:connectionId`, `/saved-queries/:connectionId/new`, `/saved-queries/:connectionId/:savedQueryId`, `/query-execution`, `/query-execution/:connectionId`を追加する（`ProtectedRoute`配下）
- [ ] Step 11.9: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'savedQueries'`, `'queryExecution'`を追加する
- [ ] Step 11.10: i18nリソース（`common.json`の`ja`/`en`）に`nav.queryExecution`・`savedQuery.*`/`queryExecution.*`関連キーを追加する

### 12. Frontend Components Unit Testing

- [ ] Step 12.1: `SavedQueryConnectionListPage.test.tsx`・`QueryExecutionConnectionListPage.test.tsx`を作成する（Vitest + RTL、一覧表示・遷移）
- [ ] Step 12.2: `SavedQueryListPage.test.tsx`を作成する（フィルタ・一覧表示・非表示化アクション）
- [ ] Step 12.3: `SavedQueryEditorPage.test.tsx`を作成する（new/existingモード、実行・保存・更新・非表示化、router state prefill）
- [ ] Step 12.4: `QueryExecutionPage.test.tsx`を作成する（実行・「名前を付けて保存」遷移）
- [ ] Step 12.5: `query.test.ts`（APIクライアント）を作成する
- [ ] Step 12.6: `HomePage.test.tsx`の実装済みバッジ数の変化を反映する

### 13. Frontend Components Summary

- [ ] Step 13.1: `aidlc-docs/construction/unit-06/code/frontend-summary.md`を作成する（作成した画面・コンポーネント一覧、テスト結果）

### 14. Documentation Generation

- [ ] Step 14.1: `backend/README.md`を更新する（UNIT-06概要: クエリ保存・実行、読み取り専用SQL検証、スキーマ単位アクセス制御、新規環境変数`MM_APP_QUERY_EXECUTION_TIMEOUT_SECONDS`/`MM_APP_QUERY_MAX_RESULT_ROWS`を追記）
- [ ] Step 14.2: `frontend/README.md`を更新する（UNIT-06の新規画面（Flow A/B）をpages概要に追記）

### 15. Deployment Artifacts

- [ ] Step 15.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に追加のインフラが不要であることを確認する（既存構成のまま変更なし）

### 16. 最終ビルド検証

- [ ] Step 16.1: **検証チェックポイント**: `./gradlew :backend:build`（jqwikプロパティテスト含む全件成功）、`npm test`（frontend、全件成功）、`npm run build`（frontend、成功）を確認する
- [ ] Step 16.2: devenv（PostgreSQL・MySQL）に対し、実アプリへcurlで、一般ユーザ（非ADMIN）としてのad-hoc実行（スキーマ許可リスト判定・読み取り専用検証・パラメータ・ページング・結果件数上限）、保存クエリのCRUD・実行・非表示化（公開範囲・作成者限定操作）を確認する
- [ ] Step 16.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）はUNIT-02〜05と同じくNVD APIキー未設定のため実施見送り（既知の制約として記録）

## Story Traceability

- STORY-6.1（保存クエリの作成・公開範囲）: Step 5.4, 8.3, 11.3〜11.5 で実装
- STORY-6.2（保存クエリの実行・編集・非表示化）: Step 5.4, 8.3, 11.5 で実装
- STORY-7.1（SQL入力・読み取り専用検証・パラメータ）: Step 5.1〜5.3, 8.2〜8.3, 11.5, 11.7 で実装
- STORY-7.2（実行対象接続・スキーマ指定）: Step 5.3, 8.2〜8.3, 11.3〜11.7 で実装
- STORY-7.3（結果表示・履歴記録）: Step 5.3, 8.2〜8.3, 11.5, 11.7 で実装

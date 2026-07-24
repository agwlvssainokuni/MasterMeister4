# UNIT-05 マスタメンテナンス - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-4.1（テーブル/ビュー一覧・レコード一覧）, STORY-4.2（絞込・SQL入力検索）, STORY-4.3（レコード編集・一括反映）, STORY-4.4（レコード作成・削除）
- **対応要件**: FR-4.1〜FR-4.8
- **対応コンポーネント**: COMP-13（`MasterDataService`, `RecordQueryService`, `RecordBatchService`, `RawQueryConditionValidator`, `ColumnDataTypeMapper`。unit-05/nfr-design/logical-components.md §1参照）
- **前提ユニット**: UNIT-01（design-systemコンポーネント）, UNIT-02（JWT認証、`AuditEventPublisher`/`AuditEventType`、`GlobalExceptionHandler`、SecurityFilterChain）, UNIT-03（`RdbmsConnectionService.getDataSource()`、`SchemaIntrospectionService`）, UNIT-04（`EffectivePermissionResolver.resolvePrimary`/`canCreate`/`canDelete`、Java直接呼び出し）
- **依存関係**: 本ユニットは新規の内部DBエンティティ・マイグレーションを持たない（Functional Design Q9=A）。対象RDBMSへの動的アクセスはUNIT-03の`RdbmsConnectionService.getDataSource(connectionId)`（HikariCP）を経由し、`NamedParameterJdbcTemplate`で実行する
- **本ユニットが所有するデータ**: なし（内部DBへの新規永続化エンティティなし）。対象RDBMS側のマスタデータを都度読み書きするのみ
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時の実装判断

- パッケージ構成は`cherry.mastermeister.masterdata`単一（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。サービスクラスはパッケージ直下、DTOは`{package}.dto`、コントローラは`{package}.api`、値オブジェクト（`RecordFilterCondition`等）は`{package}.model`に配置する
- 一括反映（BR-MASTER-07）は宣言的`@Transactional`を使わず、`RecordBatchService`内でリクエストごとに`new DataSourceTransactionManager(dataSource)`を生成し`TransactionTemplate`でトランザクション制御する（nfr-design-patterns.md §1.1・§2.1）
- SQL手入力の構文検証（`RawQueryConditionValidator`）はJSqlParserを使い、ダミーSELECT文への埋め込み経由でWHERE/ORDER BY句を抽出・検証する（nfr-design-patterns.md §3.1）
- `/api/master-data/**`は新規のSecurityFilterChainルール（認証済みなら誰でも許可）を追加する（nfr-design-patterns.md §3.2）。既存の`/api/admin/**`ルールとは独立
- 本ユニットもUNIT-04に続きPBT対象プロパティ（business-logic-model.md §7: SQL手入力の構文検証、オールオアナッシング、表示対象カラムの絞り込み）を持つため、jqwikによるプロパティベーステストを実装する

## 計画チェックリスト

### 1. Build Configuration

- [x] Step 1.1: `backend/build.gradle.kts`に依存関係を追加する: `implementation("com.github.jsqlparser:jsqlparser:...")`（tech-stack-decisions.md §1、WebSearchで最新安定版を確認の上、明示バージョン指定で追加）— jsqlparser 5.3（2026-07時点最新安定版、Maven Central確認済み）を追加
- [x] Step 1.2: `backend/src/main/resources/application.yml`に`mm.app.masterdata.batch-max-size`（デフォルト1000）・`mm.app.audit.bulk-access-threshold`（デフォルト100）を追加する（logical-components.md §3）
- [x] Step 1.3: `AppProperties`に対応するネストプロパティ（`Masterdata.batchMaxSize`, `Audit.bulkAccessThreshold`）を追加する — 既存の`new AppProperties(...)`直接構築テスト7件に新規引数を追加、コンパイル確認済み

### 2. AuditEventType拡張

- [x] Step 2.1: 既存の`AuditEventType`（`cherry.mastermeister.audit.entity`）に`MASTER_DATA_BULK_ACCESSED`, `MASTER_DATA_BATCH_APPLIED`を追加する（domain-entities.md §9。`audit_log_entry.connection_id`は既存カラムのため追加マイグレーション不要）

### 3. Data Access Layer Generation & Testing

- [x] Step 3.1: `ColumnDataTypeMapper`（`cherry.mastermeister.masterdata`）を作成する（UNIT-03の`SchemaColumn`のJDBC型情報から`ColumnDataTypeCategory`への変換、logical-components.md §1）— 実装訂正: `SchemaColumn.normalizedType`が既にJDBC型情報の正規化結果を保持していたため、生JDBC型情報の再解析ではなく`NormalizedType`からのマッピングとした（詳細はdata-access-layer-summary.md参照）
- [x] Step 3.2: `RawQueryConditionValidator`（`cherry.mastermeister.masterdata`）を作成する（JSqlParserによるWHERE/ORDER BY句の構文検証・パラメータ化、nfr-design-patterns.md §3.1。拒否時は`InvalidQueryConditionException`を送出）— 識別子クオートのため`RdbmsDialectStrategy.quoteIdentifier()`を新設（MySQL/MariaDBはバッククオート、他はダブルクオート）
- [x] Step 3.3: `RecordQueryService`（`cherry.mastermeister.masterdata`）を作成する（`NamedParameterJdbcTemplate`による動的SELECT文組み立て・実行、ページング・構造化フィルタ・SQL手入力のAND結合（BR-MASTER-15）、表示対象カラムの絞り込み（BR-MASTER-14）。`MASTER_DATA_BULK_ACCESSED`記録はMasterDataService側で件数を見て発行する（Step 5で実施）
- [x] Step 3.4: `RecordBatchService`（`cherry.mastermeister.masterdata`）を作成する（権限事前検証→`DataSourceTransactionManager`+`TransactionTemplate`によるトランザクション内個別SQL実行→成功時コミット／失敗時ロールバック・失敗理由返却、BR-MASTER-06〜09。`MASTER_DATA_BATCH_APPLIED`記録・バッチ件数上限チェックはMasterDataService側で実施、Step 5）
- [x] Step 3.5: **検証チェックポイント**: `RawQueryConditionValidatorTest`（11件、許可構文の受理、禁止構文（サブクエリ・関数呼び出し・コメント記号・複数ステートメント）の拒否）、`RecordQueryServiceTest`（8件）・`RecordBatchServiceTest`（7件、オールオアナッシングのロールバック実証を含む）をH2の実テーブル（インメモリ、テスト用に動的作成）に対して作成し、全件成功を確認した

### 4. Data Access Layer Summary

- [x] Step 4.1: `aidlc-docs/construction/unit-05/code/data-access-layer-summary.md`を作成する（作成したコンポーネント一覧、責務、テスト結果）

### 5. Business Logic Generation

- [x] Step 5.1: `MasterDataService`（`cherry.mastermeister.masterdata`）を作成する（アクセス可能な接続一覧・テーブル/ビュー一覧の取得、`EffectivePermissionResolver`（UNIT-04）・`SchemaIntrospectionService`（UNIT-03）を直接呼び出し、BR-MASTER-01〜03・13）— `MasterDataTableNotAccessibleException`（404、存在有無と権限有無を区別しないフェイルクローズ）を追加
- [x] Step 5.2: 新規例外を作成する: `InvalidQueryConditionException`, `BatchSizeExceededException`（`cherry.mastermeister.common.exception`、既存パッケージ規約に合わせる）— Step 3で前倒し作成済み
- [x] Step 5.3: `RecordQueryService`・`RecordBatchService`から`AuditEventPublisher`経由でイベント発行を組み込む — `MasterDataService`に集約（閾値判定・成功時のみ発行の判断はビジネスロジック層の責務のため）

### 6. Business Logic Unit Testing

- [x] Step 6.1: `MasterDataServiceTest`を作成する（Mockito。アクセス可能判定（BR-MASTER-01・13）、VIEW/主キー無しテーブルの読み取り専用化（BR-MASTER-02・03）の境界値）— 11件
- [x] Step 6.2: `RawQueryConditionValidatorPropertyTest`をjqwikで作成する（business-logic-model.md §7.1: 安全性の不変条件、拒否の健全性）— 3件
- [x] Step 6.3: `RecordBatchServicePropertyTest`をjqwikで作成する（business-logic-model.md §7.2: オールオアナッシングの原子性・全件反映）— 2件
- [x] Step 6.4: 表示対象カラムの絞り込み（business-logic-model.md §7.3: 非表示の不変条件）— `MasterDataServiceColumnVisibilityPropertyTest`をjqwikで新設（RecordQueryServiceの実装では絞り込みの実際の判断主体ではないため、判断主体であるMasterDataServiceに対するPBTとした）

### 7. Business Logic Summary

- [x] Step 7.1: `aidlc-docs/construction/unit-05/code/business-logic-summary.md`を作成する（作成したサービス一覧、責務、PBTプロパティ実装内容、テスト結果）

### 8. API Layer Generation

- [x] Step 8.1: DTOを作成する（`cherry.mastermeister.masterdata.dto`: `AccessibleConnectionResponse`, `AccessibleTableResponse`, `RecordPageResponse`, `RecordColumnResponse`, `RecordFilterRequest`, `BatchOperationRequest`/`BatchOperationItemRequest`, `BatchOperationResultResponse`/`BatchOperationItemResultResponse`、logical-components.md §1）
- [x] Step 8.2: `MasterDataController`（`cherry.mastermeister.masterdata.api`）を作成する（logical-components.md §1のエンドポイント一覧）— `filter`クエリパラメータはJSON配列としてエンコード（`RecordFilterRequest`のリスト）する実装とした
- [x] Step 8.3: `GlobalExceptionHandler`（UNIT-02）に`InvalidQueryConditionException`/`BatchSizeExceededException`のハンドラを追加する（VALIDATION_ERROR/400、nfr-design-patterns.md §1.2）— 実装訂正: 両例外とも`ApiException`のサブクラスであり、既存の汎用`@ExceptionHandler(ApiException.class)`がそのまま処理するため、個別ハンドラの追加は不要と判明（Step 3で対応済み）
- [x] Step 8.4: SecurityFilterChain設定に`/api/master-data/**`（認証済みなら許可）の新規ルールを追加する（nfr-design-patterns.md §3.2）— 実装訂正: 既存の`SecurityConfig`には`/api/admin/**`（ADMIN限定）の次に`/api/**`→`authenticated()`という汎用ルールが既に存在しており、`/api/master-data/**`は`/api/admin/**`に一致しないためこの既存ルールがそのまま適用される。新規ルール追加は不要と判明し、`MasterDataControllerTest`で非ADMINユーザによるアクセス可能性を実証するに留めた
- [x] Step 8.5: OpenAPI/Swagger UIへの反映を確認する（既存の自動生成のみ、追加実装不要）— 既存コントローラ同様、springdocによる自動生成のみで追加のアノテーションは不要（Step 16の起動確認で最終確認）

### 9. API Layer Unit Testing

- [x] Step 9.1: `@WebMvcTest`で`MasterDataControllerTest`を作成する（一般ユーザ（非ADMIN）でもアクセス可能なことの確認、絞込パラメータ・SQL手入力パラメータのバリデーション、一括反映のバッチ上限超過エラー）— 10件成功。WebMvcTestスライスに`ObjectMapper`Beanが含まれないため、テスト用`@TestConfiguration`で明示提供

### 10. API Layer Summary

- [x] Step 10.1: `aidlc-docs/construction/unit-05/code/api-layer-summary.md`を作成する（エンドポイント一覧、テスト結果）

### 11. Frontend Components Generation

- [ ] Step 11.1: APIクライアント`frontend/src/api/masterData.ts`を作成する（接続一覧・テーブル一覧・レコード一覧・一括反映の各関数）
- [ ] Step 11.2: `MasterDataConnectionListPage`（`frontend/src/pages/`）を作成する（frontend-components.md §1）
- [ ] Step 11.3: `MasterDataTableListPage`（`frontend/src/pages/`）を作成する（frontend-components.md §2）
- [ ] Step 11.4: `MasterDataRecordListPage`（`frontend/src/pages/`）を作成する（frontend-components.md §3、絞込UI・インライン編集・一括反映・ページング）
- [ ] Step 11.5: `App.tsx`のルーティングに`/master-data`, `/master-data/:connectionId`, `/master-data/:connectionId/:schemaName/:tableName`を追加する（`ProtectedRoute`配下）
- [ ] Step 11.6: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'masterData'`を追加する（frontend-components.md §4。ナビ項目・パスはUNIT-01予約分をそのまま使用、変更不要）
- [ ] Step 11.7: i18nリソース（`common.json`の`ja`/`en`）に`masterData.*`関連キーを追加する

### 12. Frontend Components Unit Testing

- [ ] Step 12.1: `MasterDataConnectionListPage.test.tsx`・`MasterDataTableListPage.test.tsx`・`MasterDataRecordListPage.test.tsx`を作成する（Vitest + RTL、一覧表示・絞込・インライン編集・一括反映のAPI呼び出しモック）
- [ ] Step 12.2: `masterData.test.ts`（APIクライアント）を作成する
- [ ] Step 12.3: `HomePage.test.tsx`の実装済みバッジ数の変化を反映する

### 13. Frontend Components Summary

- [ ] Step 13.1: `aidlc-docs/construction/unit-05/code/frontend-summary.md`を作成する（作成した画面・コンポーネント一覧、テスト結果）

### 14. Documentation Generation

- [ ] Step 14.1: `backend/README.md`を更新する（UNIT-05概要: マスタメンテナンス、SQL手入力検証、一括反映のトランザクション制御を追記）
- [ ] Step 14.2: `frontend/README.md`を更新する（UNIT-05の新規画面をpages概要・冒頭説明に追記）

### 15. Deployment Artifacts

- [ ] Step 15.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に追加のインフラが不要であることを確認する（既存構成のまま変更なし）

### 16. 最終ビルド検証

- [ ] Step 16.1: **検証チェックポイント**: `./gradlew :backend:build`（全ユニットテスト成功、jqwikプロパティテスト含む）、`npm test`（frontend）、`npm run build`（frontend）がすべて成功することを確認する
- [ ] Step 16.2: devenvの実RDBMS（スキーマ取込済み接続）に対し、`java -jar`起動した実アプリへcurlで、一般ユーザとしてのアクセス可能接続一覧・テーブル一覧・レコード一覧（絞込・SQL手入力・ページング）取得、レコード作成・更新・削除の一括反映（正常系・オールオアナッシング失敗系の両方）を検証する。特に一括反映のトランザクション制御（`DataSourceTransactionManager`+`TransactionTemplate`）が実際に機能する（1件の失敗で全件ロールバックされる）ことを実機で確認する
- [ ] Step 16.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）は、UNIT-02〜04と同じくNVD APIキー未設定のため実施見送り（既知の制約として記録。新規追加のJSqlParser依存も次回実施時の対象に含める）

---

## Story Traceability

- STORY-4.1（テーブル/ビュー一覧・レコード一覧）: Step 5.1, 3.3, 8, 11.2〜11.4 で実装
- STORY-4.2（絞込・SQL入力検索）: Step 3.2〜3.3, 8, 11.4 で実装
- STORY-4.3（レコード編集・一括反映）: Step 3.4, 8, 11.4 で実装
- STORY-4.4（レコード作成・削除）: Step 3.4, 8, 11.4 で実装

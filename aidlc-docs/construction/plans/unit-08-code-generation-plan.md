# UNIT-08 クエリ履歴 - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-8.1（履歴一覧の表示・絞り込み）, STORY-8.2（履歴からの画面遷移）
- **対応要件**: FR-8.1〜FR-8.4
- **対応コンポーネント**: COMP-17（`QueryHistoryService`）
- **前提ユニット**: UNIT-01（`queryHistory`ナビ項目の仮予約）, UNIT-02（JWT認証、`User.role`クレーム、`GlobalExceptionHandler`）, UNIT-03（`RdbmsConnectionRepository`）, UNIT-04（本ユニットでは直接依存しない、記録の不変性のためアクセス権再判定を行わないため）, UNIT-06（`QueryExecutionRecord`エンティティ・テーブル、`SavedQueryRepository`、`QueryExecutionPage`/`SavedQueryEditorPage`/`QueryBuilderPage`への画面遷移先）
- **依存関係**: UNIT-04の`EffectivePermissionResolver`には依存しない（BR-QUERYHISTORY-04、記録は不変としてアクセス権を再判定しないため）
- **本ユニットが所有するデータ**: なし（`QueryExecutionRecord`はUNIT-06所有、本ユニットは参照専用。新規追加は`(connection_id, executed_at)`複合インデックスのみ）
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時の実装判断

- パッケージ構成は`cherry.mastermeister.queryhistory`単一。サービス・Specificationクラスはパッケージ直下、DTOは`{package}.dto`、コントローラは`{package}.api`に配置する
- Controller層で実行者スコープのロール判定（`principal.getClaimAsString("role")`）を行い、`executedByFilter`（`Long`、`null`なら全ユーザ対象）に変換してからService層へ渡す（nfr-design-patterns.md §1.2、logical-components.md）。この判定ロジックは3エンドポイント共通の`private`ヘルパーメソッドとして`QueryHistoryController`内に実装する
- `QueryHistoryService`が絞込・ページング・名前解決の3責務を担う。動的クエリ構築は`QueryHistorySpecifications`（静的ファクトリメソッド集）に分離する
- `QueryExecutionRecordRepository`（UNIT-06既存）に`JpaSpecificationExecutor<QueryExecutionRecord>`を追加実装し、DISTINCT取得用のカスタムクエリメソッド4種を追加する。既存のRepositoryインターフェースを直接修正する（新規ファイルは作らない）
- `SavedQueryRepository`（UNIT-06既存）に`findAllByIdIn(Collection<Long>)`を追加する（既存ファイルの修正）
- 新規マイグレーション`V17__add_index_query_execution_record_connection_executed_at.sql`を追加する（既存V16は変更しない）
- 新規例外クラスは作成しない。絞込パラメータの検証はBean Validation（`@Max`、enumバインド、`@AssertTrue`＋`@JsonIgnore`）で行う（UNIT-07で発見した「`@AssertTrue`がJacksonのgetter規則でレスポンスJSONに漏れる」教訓を踏まえ、`QueryHistorySearchRequest`にも`@JsonIgnore`を付与する）
- フロントエンドは接続選択画面（`/query-history`）→履歴一覧画面（`/query-history/:connectionId`）の2画面構成。UNIT-01で仮予約済みの`queryHistory`ナビ項目をそのまま使用する（navigation.tsへの変更は不要）
- 履歴一覧画面はdesign-system既存の`FilterBar`・`DataTable`・`Pagination`・`Modal`・`CodeBlock`を組み合わせる。新規UIコンポーネントの追加は最小限に留める
- フロントエンドのページ番号は`Pagination`（1-indexed）↔`Pageable`（0-indexed）の変換を`QueryHistoryPage`内の1箇所に限定する（business-logic-model.md §4）
- 実行者スコープSelectの変更時、履歴一覧APIに加えスキーマ名一覧APIも現在の`executedByScope`で再取得する（承認前レビューで追加した情報漏洩対策）
- 新規外部ライブラリ依存・Build Configuration変更なし。PBT対象外（stories.mdに明記済み）

## 計画チェックリスト

### 1. Business Logic Generation

- [x] Step 1.1: DTOクラス群を作成する（`cherry.mastermeister.queryhistory.dto`）: `QueryHistoryConnectionResponse`（`connectionId`, `displayName`）, `QueryHistoryRecordResponse`（`id`, `executedBy`, `executorDisplayName`, `connectionId`, `schemaName`, `sql`, `savedQueryId`, `savedQueryName`, `queryType`, `rowCount`, `durationMillis`, `executedAt`）, `QueryHistorySearchCriteria`（`executedAtFrom`, `executedAtTo`, `schemaName`, `sqlKeyword`。Service層内部用、`executedByScope`を含まない）, `QueryHistoryPageResponse`（新規、実装時追加。`Page<QueryHistoryRecordResponse>`をmasterdata.dto.RecordPageResponseと同様の独自の軽量ラッパーへ変換）。**実装時の判断**: 計画時点では`QueryHistorySearchRequest`（`@Valid`＋Bean Validation付きDTO）をGETのクエリパラメータバインド先とする想定だったが、既存プロジェクトのGETエンドポイント（`SavedQueryController`, `MasterDataController`）はいずれも個々の`@RequestParam`で受け取るパターンで統一されており、`@ModelAttribute`によるDTOバインドの前例がなかった（`@Valid @ModelAttribute`失敗時は`BindException`となり、既存の`GlobalExceptionHandler`は`MethodArgumentNotValidException`のみハンドリング済みで対応漏れが生じる）。既存パターンとの一貫性を優先し、`QueryHistorySearchRequest`は作成せず、Controller側で個々の`@RequestParam`を受け取り検証する方式に変更（Step 4.1参照）
- [x] Step 1.2: enumを作成する（`cherry.mastermeister.queryhistory.dto`）: `ExecutedByScope`（`ALL`/`MINE`）, `QueryType`（`SAVED`/`AD_HOC`）
- [x] Step 1.3: `QueryHistorySpecifications`（`cherry.mastermeister.queryhistory`）を作成する（`Specification<QueryExecutionRecord>`の静的ファクトリメソッド: `connectionIdEquals`, `executedByEquals`, `executedAtFrom`, `executedAtTo`, `schemaNameEquals`, `sqlContains`。nfr-design-patterns.md §2.1）
- [x] Step 1.4: `QueryExecutionRecordRepository`（UNIT-06既存、`cherry.mastermeister.query.repository`）を修正する: `JpaSpecificationExecutor<QueryExecutionRecord>`を追加実装、新規メソッド`findDistinctConnectionIdByExecutedBy(Long)`, `findDistinctConnectionId()`, `findDistinctSchemaNameByConnectionIdAndExecutedBy(Long, Long)`, `findDistinctSchemaNameByConnectionId(Long)`を追加（logical-components.md §2）
- [x] Step 1.5: `SavedQueryRepository`（UNIT-06既存、`cherry.mastermeister.query.repository`）を修正する: `findAllByIdIn(Collection<Long>)`を追加
- [x] Step 1.6: `QueryHistoryService`（`cherry.mastermeister.queryhistory`、COMP-17）を作成する
  - `listConnections(Long executedByFilter): List<QueryHistoryConnectionResponse>` — DISTINCT取得→`RdbmsConnectionRepository.findAllById`で表示名一括解決、見つからない場合は「(削除済み接続)」（business-logic-model.md §3-1）
  - `listSchemas(Long connectionId, Long executedByFilter): List<String>` — DISTINCT取得（business-rules.md BR-QUERYHISTORY-10）
  - `listHistory(Long connectionId, Long executedByFilter, QueryHistorySearchCriteria criteria, Pageable pageable): Page<QueryHistoryRecordResponse>` — `QueryHistorySpecifications`で動的組立→`findAll(spec, pageable)`→`savedQueryId`/`executedBy`の一括解決（`SavedQueryRepository.findAllByIdIn`, `UserRepository.findAllById`）→`QueryHistoryRecordResponse`へ変換（business-logic-model.md §2・§5〜6）

### 2. Business Logic Unit Testing

- [x] Step 2.1: `QueryHistoryServiceTest`を作成する（Mockito。`listConnections`: 実行者スコープによる絞込、削除済み接続のプレースホルダー表示。`listSchemas`: 実行者スコープによる絞込。`listHistory`: 各絞込条件の組み合わせ、保存クエリ名解決（正常・削除済み）、実行者名解決（正常・不明ユーザ）、queryType導出）— 9件
- [x] Step 2.2: `QueryHistorySpecificationsTest`を作成する（`@DataJpaTest`。各ファクトリメソッド単体、および複数条件の組み合わせでの絞込結果確認）— 6件
- [x] Step 2.3: `QueryExecutionRecordRepositoryTest`（UNIT-06既存）に、追加した新規カスタムクエリメソッドのテストケースを追加する（既存ファイルの修正）— 4件追加
- [x] Step 2.4: `SavedQueryRepositoryTest`（UNIT-06既存）に`findAllByIdIn`のテストケースを追加する（既存ファイルの修正）— 1件追加

### 3. Business Logic Summary

- [x] Step 3.1: `aidlc-docs/construction/unit-08/code/business-logic-summary.md`を作成する

### 4. API Layer Generation

- [x] Step 4.1: `QueryHistoryController`（`cherry.mastermeister.queryhistory.api`）を作成する（3エンドポイント: `GET /api/query-history/connections`, `GET /api/query-history/{connectionId}/schemas`, `GET /api/query-history/{connectionId}`。各エンドポイントで`@AuthenticationPrincipal Jwt principal`からロール判定→`executedByFilter`計算→Service呼び出し、logical-components.md §1）。**実装時の追加**: 新規例外`QueryHistoryInvalidParameterException`（400）を`cherry.mastermeister.common.exception`に追加し、ページサイズ上限（200件）・日時範囲の相関チェック違反時に送出（Step 1.1の実装判断の続き）
- [x] Step 4.2: `GlobalExceptionHandler`への追加要否を確認する — 新規例外は`ApiException`のサブクラスであり既存の汎用ハンドラで処理されるため追加不要と確認（UNIT-05〜07と同じ結論）
- [x] Step 4.3: SecurityFilterChain設定への`/api/query-history/**`ルール追加要否を確認する — 既存の`/api/**`→`authenticated()`ルールでカバーされるため追加不要と確認（UNIT-05〜07と同じ結論）
- [x] Step 4.4: OpenAPI/Swagger UIへの反映を確認する（既存の自動生成のみ、追加実装不要）

### 5. API Layer Unit Testing

- [x] Step 5.1: `@WebMvcTest`で`QueryHistoryControllerTest`を作成する（3エンドポイントの正常系、絞込パラメータ違反時400応答、一般ユーザが`executedByScope=ALL`を指定した場合のフェイルクローズ確認をMockitoの`verify`で実証）— 8件全件成功

### 6. API Layer Summary

- [x] Step 6.1: `aidlc-docs/construction/unit-08/code/api-layer-summary.md`を作成する

### 7. Repository Layer Generation

（Step 1.4〜1.5で完了済み。UNIT-06既存Repositoryの拡張のため独立ステップは設けない）

### 8. Frontend Components Generation

- [ ] Step 8.1: APIクライアント`frontend/src/api/queryHistory.ts`を作成する（接続一覧・スキーマ名一覧・履歴一覧取得の各関数、型定義）
- [ ] Step 8.2: `QueryHistoryConnectionListPage`（`frontend/src/pages/`）を作成する（frontend-components.md 画面1）
- [ ] Step 8.3: `QueryHistoryPage`（`frontend/src/pages/`）を作成する（frontend-components.md 画面2。`FilterBar`＋`DataTable`＋`Pagination`＋詳細`Modal`、`Pagination`↔`Pageable`のページ番号変換、実行者スコープSelect変更時のスキーマ一覧再取得）
- [ ] Step 8.4: `App.tsx`のルーティングに`/query-history`, `/query-history/:connectionId`を追加する（`ProtectedRoute`配下）
- [ ] Step 8.5: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'queryHistory'`を追加する
- [ ] Step 8.6: i18nリソース（`common.json`の`ja`/`en`）に`queryHistory.*`関連キーを追加する（`nav.queryHistory`はUNIT-01で追加済み）

### 9. Frontend Components Unit Testing

- [ ] Step 9.1: `queryHistory.test.ts`（APIクライアント）を作成する
- [ ] Step 9.2: `QueryHistoryConnectionListPage.test.tsx`を作成する
- [ ] Step 9.3: `QueryHistoryPage.test.tsx`を作成する（一覧表示、絞込条件変更、ページング、詳細モーダルからの3遷移、削除済み接続/保存クエリのプレースホルダー表示）

### 10. Frontend Components Summary

- [ ] Step 10.1: `aidlc-docs/construction/unit-08/code/frontend-summary.md`を作成する

### 11. Database Migration Scripts

- [ ] Step 11.1: `V17__add_index_query_execution_record_connection_executed_at.sql`を作成する（`(connection_id, executed_at)`複合インデックス、logical-components.md §7）

### 12. Documentation Generation

- [ ] Step 12.1: `backend/README.md`を更新する（UNIT-08概要: クエリ履歴、`/api/query-history/*`エンドポイント）
- [ ] Step 12.2: `frontend/README.md`を更新する（UNIT-08の新規画面をpages概要に追記）

### 13. Deployment Artifacts

- [ ] Step 13.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に追加のインフラが不要であることを確認する

### 14. 最終ビルド検証

- [ ] Step 14.1: **検証チェックポイント**: `./gradlew :backend:build`、`npm test`（frontend）、`npm run build`（frontend）を確認する
- [ ] Step 14.2: devenv（PostgreSQL・MySQL）に対し実アプリ（`bootWar`で明示的にビルド、`feedback_deployment_artifact.md`の運用ルールに従う）で、一般ユーザ・管理者それぞれのJWTを用いてAPI経由（curl）で接続一覧・スキーマ名一覧・履歴一覧（絞込・ページング）を確認する。実行者スコープのフェイルクローズ（一般ユーザが`executedByScope=ALL`を指定しても自分の履歴のみ返ること）、削除済み接続・非表示化済み保存クエリのプレースホルダー表示、`(connection_id, executed_at)`インデックスが実際に適用されていること（`EXPLAIN`等）を確認する
- [ ] Step 14.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）はUNIT-02〜07と同じくNVD APIキー未設定のため実施見送り

## Story Traceability

- STORY-8.1（履歴一覧の表示・絞り込み） — Step 1.1〜1.6, 8.1〜8.3
- STORY-8.2（履歴からの画面遷移） — Step 8.3

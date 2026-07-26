# UNIT-06 クエリ保存・実行 - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.query`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。

---

## 1. クエリドメイン（`cherry.mastermeister.query`）

### QueryController（COMP-14の一部、Q7=B）
ad-hoc実行・接続/スキーマ一覧を担当する。
- `GET /api/queries/connections` — アクセス可能な接続一覧（UNIT-05の`listAccessibleConnections`と同じ判定ロジック）
- `GET /api/queries/{connectionId}/schemas` — アクセス可能なスキーマ一覧（BR-QUERY-02）
- `POST /api/queries/{connectionId}/execute` — ad-hoc実行（BR-QUERY-01, BR-QUERY-04）

### SavedQueryController（COMP-15の一部、Q7=B）
保存クエリのCRUD・実行・非表示化を担当する。
- `GET /api/queries/{connectionId}/saved` — 保存クエリ一覧（BR-QUERY-05, BR-QUERY-08）
- `POST /api/queries/{connectionId}/saved` — 新規保存（BR-QUERY-05）
- `GET /api/queries/{connectionId}/saved/{savedQueryId}` — 取得（BR-QUERY-09、アクセス不可時404）
- `PUT /api/queries/{connectionId}/saved/{savedQueryId}` — 更新（作成者のみ、BR-QUERY-07）
- `POST /api/queries/{connectionId}/saved/{savedQueryId}/execute` — 実行（BR-QUERY-09）
- `POST /api/queries/{connectionId}/saved/{savedQueryId}/retire` — 非表示化（作成者のみ、BR-QUERY-08）

いずれのControllerも、新規SecurityFilterChainルール（nfr-design-patterns.md §3.2、または既存の汎用ルールで賄える場合はそのまま）により認証済みユーザ（ロール不問）がアクセス可能。

### QueryExecutionService（COMP-14）
- business-logic-model.md §4のクエリ実行フローを実装。`execute(userId, connectionId, sql, params, schema, page, pageSize)`（ad-hoc）と`executeSavedQuery(userId, savedQueryId, params, schema, page, pageSize)`（保存クエリ）の2メソッドを提供する
- 処理順序: (1) `QuerySqlAnalyzer`による読み取り専用検証、(2) スキーマ許可リスト判定（nfr-design-patterns.md §2.4）、(3) 接続確立・スキーマ切替（§2.1）、(4) パラメータバインドによる実行・ページング（§2.2〜2.3）、(5) `QueryExecutionRecord`永続化・`AuditLogEntry`記録（§3.4）
- `executeSavedQuery`は、対象の保存クエリを`SavedQueryService`経由で取得し（非表示化・公開範囲チェック、BR-QUERY-09）、そのSQLを固定入力として同じ処理フローに渡す

### SavedQueryService（COMP-15）
- business-logic-model.md §7〜8の保存・編集・非表示化フローを実装
- `saveQuery(userId, connectionId, name, sql, visibility)`: 保存前に`QuerySqlAnalyzer`で読み取り専用検証（BR-QUERY-01）を行い、`connectionId`を保存時点で固定する（BR-QUERY-02〜03、Q11）
- `updateQuery(userId, savedQueryId, name, sql, visibility)`: 作成者のみ実行可能（BR-QUERY-07）。SQL変更時は読み取り専用検証を再実行する
- `retireQuery(userId, savedQueryId)`: 作成者のみ実行可能（BR-QUERY-08）
- `getSavedQuery(userId, savedQueryId)`/`listSavedQueries(userId, connectionId, visibilityFilter, includeOwnRetired)`: BR-QUERY-05・BR-QUERY-08〜09のアクセス可否判定を行い、対象外の場合は`SavedQueryNotAccessibleException`を送出する

### QuerySqlAnalyzer（COMP-14の一部、Q6=A）
- nfr-design-patterns.md §3.1のSQL読み取り専用検証・パラメータ検出を実装
- コンストラクタでSQL文字列を受け取りJSqlParserで1回構文解析する。解析に失敗した場合、または結果が`Select`文でない場合は内部的に非読み取り専用と判定する
- `isReadOnly(): boolean`と`detectParameters(): List<String>`（`JdbcNamedParameter`ノードの走査結果）の2メソッドを提供する

### 例外クラス（新規、いずれも`ApiException`のサブクラスとして`GlobalExceptionHandler`の汎用ハンドラで自動処理される）
- `QuerySchemaNotAccessibleException`（403 FORBIDDEN、BR-QUERY-02）
- `NonReadOnlyQueryException`（400 BAD_REQUEST、BR-QUERY-01）
- `SavedQueryNotAccessibleException`（404 NOT_FOUND、BR-QUERY-09）
- `QueryExecutionTimeoutException`（408 REQUEST_TIMEOUT、`QueryTimeoutException`からの変換）
- `QueryResultSizeExceededException`（400 BAD_REQUEST、`BatchSizeExceededException`と同様の構成）

### DTO設計
- `AccessibleConnectionResponse`（UNIT-05と同一形状、`connectionId`, `displayName`）
- `AccessibleSchemaResponse`（`schemaName`）
- `QueryExecutionRequest`（`sql`, `schemaName`, `params: Map<String, String>`, `pagingEnabled`, `page`, `pageSize`）
- `QueryResultResponse`（`columns`, `rows`, `page`, `pageSize`, `totalCount`, `rowCount`, `durationMillis`）
- `SavedQueryRequest`（`name`, `sql`, `visibility`）— 作成・更新共通
- `SavedQuerySummaryResponse`（`id`, `name`, `visibility`, `createdBy`, `own: boolean`, `retired`, `createdAt`, `updatedAt`）
- `SavedQueryExecutionRequest`（`schemaName`, `params: Map<String, String>`, `pagingEnabled`, `page`, `pageSize`）

---

## 2. 依存関係の追加

なし。JSqlParserはUNIT-05で追加済みの依存関係を再利用する。`SingleConnectionDataSource`・`NamedParameterJdbcTemplate`はSpring Framework既存機能。

---

## 3. 設定（`AppProperties`拡張）

`application.yml`に以下を追加する。

```yaml
mm:
  app:
    query:
      execution-timeout-seconds: 30
      max-result-rows: 10000
```

`AppProperties`に新規レコード`Query(int executionTimeoutSeconds, int maxResultRows)`を追加し（`Masterdata`/`Audit`と同様のバリデーション付きコンパクトコンストラクタ）、`QueryExecutionService`から参照する。

---

## 4. Spring Security設定の変更（nfr-design-patterns.md §3.2）

UNIT-02で確立済みのSecurityFilterChain設定に、`/api/queries/**`へのルールを追加する。ただしUNIT-05のCode Generationで判明した前例（既存の`/api/admin/**`→ADMIN限定の次の`/api/**`→`authenticated()`という汎用ルールが既に適用範囲をカバーしていた）があるため、Code Generation着手時に実装を検証し、既存の汎用ルールで賄える場合は新規ルール追加を省略する。

---

## 5. 監査ログ連携

`AuditEventPublisher`（UNIT-02、`cherry.mastermeister.audit`）を通じて、domain-entities.md §6で定義した4種のイベント（`QUERY_EXECUTED`, `QUERY_SAVED`, `QUERY_UPDATED`, `QUERY_RETIRED`）を発行する。`AuditEventType.java`への追加はFunctional Design時点の決定を維持する。

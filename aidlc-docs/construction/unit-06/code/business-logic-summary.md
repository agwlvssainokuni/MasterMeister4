# UNIT-06 クエリ保存・実行 - Business Logic Summary

## 作成したコンポーネント

### QuerySqlAnalyzer（`cherry.mastermeister.query`）
- BR-QUERY-01（読み取り専用検証）・§2（パラメータ検出）を1クラスで実装。JSqlParserで1回だけ構文解析し、解析結果を`isReadOnly()`・`detectParameters()`の両方で再利用する
- **実装訂正**: 当初`CCJSqlParserUtil.parse(String)`のみで単一Select文かを判定する設計だったが、検証の過程で「SELECT 1; DELETE FROM x」のような複数ステートメントの入力が、先頭の"SELECT 1"のみを解析し後続の"DELETE FROM x"を無言で無視することを実機確認で発見した（多重ステートメント注入を見逃す重大な検証漏れ）。`CCJSqlParserUtil.parseStatements(String)`でステートメント数が1件であることまで確認する方式に訂正した
- パラメータ検出は、JSqlParserに専用のパラメータ収集ユーティリティが存在しないため、`TablesNamesFinder`（テーブル名収集用の全走査ユーティリティ）を継承し`visit(JdbcNamedParameter, S)`のみ追加処理する方式で実装。JOIN・サブクエリを含むSELECT文全体を漏れなく走査できることを確認済み

### 新規例外（`cherry.mastermeister.common.exception`）
`QuerySchemaNotAccessibleException`（403）、`NonReadOnlyQueryException`（400）、`SavedQueryNotAccessibleException`（404）、`QueryExecutionTimeoutException`（408）、`QueryResultSizeExceededException`（400）。いずれも`ApiException`のサブクラスで、既存の汎用`@ExceptionHandler(ApiException.class)`で自動処理される。`messages_ja.properties`/`messages_en.properties`にエラーメッセージを追加

### QueryExecutionService（COMP-14）
- `execute`（ad-hoc）・`executeSavedQuery`（保存クエリ経由）の2メソッド
- 処理順序: 読み取り専用検証 → スキーマ許可リスト判定（`EffectivePermissionResolver.resolvePrimary`のループ呼び出し、新規キャッシュ層なし）→ 物理接続確立・スキーマ切替（`SingleConnectionDataSource`でラップ）→ パラメータバインド実行・ページング（サブクエリラップ＋LIMIT/OFFSET、同一接続上でCOUNT→結果取得の順）→ `QueryExecutionRecord`永続化・`QUERY_EXECUTED`監査ログ記録
- `listAccessibleConnections`/`listAccessibleSchemas`も提供（`GET /api/queries/connections`・`GET /api/queries/{connectionId}/schemas`用）

### SavedQueryService（COMP-15）
- `saveQuery`/`updateQuery`/`retireQuery`/`getSavedQuery`/`listSavedQueries`
- BR-QUERY-05〜09のアクセス可否判定（公開範囲・非表示化・作成者限定）、`QUERY_SAVED`/`QUERY_UPDATED`/`QUERY_RETIRED`監査ログ記録
- **実機E2E検証で発見・修正した不具合**: `updateQuery`/`retireQuery`は当初`@Transactional`を付与しておらず、エンティティのミューテートのみでDBへの変更が永続化されないバグがあった（単体テストはMockitoスタブが同一Javaオブジェクトを返すため検出できなかった）。`GroupService.renameGroup`と同じ方式（`@Transactional`＋Hibernateダーティチェック）に修正し、全メソッドに一貫して`@Transactional`（読み取り系は`readOnly = true`）を付与した

## PBT（Property-Based Testing）実装

business-logic-model.md §9で識別した3プロパティをjqwikで実装（`QuerySqlAnalyzerPropertyTest`）:
1. 任意に生成した単一SELECT文（JOIN・WHERE句を含む）は常に読み取り専用として受理される
2. 任意に生成した非SELECT文（INSERT/UPDATE/DELETE）・複数ステートメントは常に拒否される
3. SQL文中の任意個数の`:param`トークン（文字列リテラル内のものを除く）が過不足なく検出される

**実行時の発見（既知の制限）**: プロパティテストの実行中、パラメータ名が`use`（SQL方言によっては`USE <database>`文で使われる予約語）の場合にJSqlParserが構文エラーとして拒否することを発見した。JSqlParserの構文解析はSQL標準の予約語を認識する一般的な設計のため、`:use`のように予約語と衝突する名前付きパラメータは、実際には無害なSELECT文であっても解析に失敗し`isReadOnly()`が`false`を返す（false negative）。これはJSqlParserライブラリの構文解析上の制限であり、本ユニットの実装で回避することは現実的でないと判断し、既知の制限として記録する（実務上、ユーザがSQL予約語と完全に一致するパラメータ名を選択するケースは極めて稀）。プロパティテストの生成器はこの既知の制限を踏まえ、SQL予約語との衝突を除外するフィルタを加えた

## テスト結果

- `QuerySqlAnalyzerTest`: 13件（許可SQL受理、禁止SQL拒否、パラメータ検出）
- `QuerySqlAnalyzerPropertyTest`: 4プロパティ（jqwik）
- `QueryExecutionServiceTest`: 8件（H2実テーブル使用。ページング・COUNT取得・パラメータバインド・スキーマ拒否・結果件数上限・監査ログ・保存クエリ経由実行）
- `SavedQueryServiceTest`: 14件（アクセス可否判定境界値、編集・非表示化の作成者限定チェック、一覧の絞込）
- 全件成功（`./gradlew :backend:test`、既存ユニット分含め全318件成功）

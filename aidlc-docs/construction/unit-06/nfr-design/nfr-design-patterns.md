# UNIT-06 クエリ保存・実行 - NFR Design Patterns

`unit-06-nfr-design-plan.md`の回答（Q1〜6=A、Q7=B）に基づく実装パターンを記載する。

---

## 1. Resilience

### 1.1 スキーマ非許可時のエラーハンドリング（Q1=A、BR-QUERY-02）
- 実行対象スキーマが許可リスト（BR-QUERY-02〜03）に含まれない場合、新規例外`QuerySchemaNotAccessibleException`（403 FORBIDDEN）を送出する
- スキーマ一覧自体は`GET /api/queries/{connectionId}/schemas`で既にアクセス可能な範囲のみを返却しているため、UNIT-05の`MasterDataTableNotAccessibleException`（404、テーブルの存在を隠すフェイルクローズ方針）とは異なり、403で明確にアクセス拒否であることを示す

### 1.2 SQL非読み取り専用時のエラーハンドリング（Q2=A、BR-QUERY-01）
- `QuerySqlAnalyzer`（§3.1、logical-components.md参照）がSQLを単一の`Select`文と認識できない場合（構文エラー・複数ステートメント・非SELECT文のいずれも含む）、新規例外`NonReadOnlyQueryException`（400 BAD_REQUEST）を送出する
- ad-hoc実行・保存クエリの保存時・保存クエリの編集時のいずれでも共通してこの検証・例外を適用する（BR-QUERY-01）

### 1.3 保存クエリアクセス不可時のエラーハンドリング（Q3=A、BR-QUERY-09）
- 保存クエリが非表示化済み（作成者以外）、またはPrivateで作成者以外がアクセスしようとした場合、新規例外`SavedQueryNotAccessibleException`（404 NOT_FOUND）を送出する
- UNIT-05の`MasterDataTableNotAccessibleException`と同じフェイルクローズ方針（存在有無とアクセス権限有無を区別しない）を踏襲する

### 1.4 タイムアウト・結果件数上限超過のエラーハンドリング（Q4=A、NFR Requirements Q3〜Q4）
- クエリ実行が`JdbcTemplate.setQueryTimeout`（tech-stack-decisions.md §3）で設定したタイムアウト秒数を超過した場合、Spring JDBCが送出する`org.springframework.dao.QueryTimeoutException`（内部的に`SQLTimeoutException`をラップ）をcatchし、新規例外`QueryExecutionTimeoutException`（408 REQUEST_TIMEOUT）に変換して送出する
- ページング無効時に結果件数が上限（`mm.app.query.max-result-rows`、デフォルト10,000件）を超える場合、新規例外`QueryResultSizeExceededException`（400 BAD_REQUEST、UNIT-05の`BatchSizeExceededException`と同様の構成）を送出する
- いずれも既存の汎用`Exception.class`ハンドラ（500、ログ出力のみ）に落とさず、クライアントに意味のあるエラー応答を返す

### 1.5 実効権限判定の安全側デフォルト（既存決定の継続適用、SECURITY-15）
- 本ユニットは`EffectivePermissionResolver`（UNIT-04）をそのまま利用する。判定対象の設定が一切存在しない場合はフェイルクローズ（`NONE`）となる既存の挙動をそのまま引き継ぐ

---

## 2. Performance

### 2.1 接続管理とスキーマ切替（NFR Requirements Q1=A、tech-stack-decisions.md §1）
- クエリ実行のたびに`RdbmsConnectionService.getDataSource(connectionId)`から`DataSource`を取得し、`DataSource.getConnection()`で物理接続を確立する
- `dialect.requiresSchemaSwitch()`が`true`の場合のみ`dialect.applySchemaSwitch(connection, schema)`を適用する
- 物理接続を`SingleConnectionDataSource(connection, true)`（`suppressClose=true`）でラップし、同一の`NamedParameterJdbcTemplate`インスタンスに渡す。`finally`句で物理接続を明示的にクローズする

### 2.2 COUNT取得と結果取得の実行順序（Q5=A）
- ページング有効時は、上記2.1で確立した同一の物理接続・同一の`NamedParameterJdbcTemplate`インスタンス上で、COUNTクエリ（`SELECT COUNT(*) FROM (<ユーザSQL>) AS mm_count`）→結果取得クエリ（`SELECT * FROM (<ユーザSQL>) AS mm_page LIMIT :pageSize OFFSET :offset`）の順に2回実行する
- スキーマ切替は接続確立時に1回のみ実施すればよい。読み取り専用のため、2クエリ間で同一トランザクションスナップショットである必要はないと判断する（UNIT-05の`RecordQueryService`と同じ方式）

### 2.3 ページング無効時の安全上限（tech-stack-decisions.md §4）
- ページング無効時も内部的に`LIMIT (mm.app.query.max-result-rows + 1)`を付与して取得し、件数が上限を超える場合は`QueryResultSizeExceededException`とする（全件取得してから件数判定するとメモリ保護の目的を果たせないため）

### 2.4 スキーマ許可リスト判定（NFR Requirements Q6=A）
- `EffectivePermissionResolver`への新規メソッド追加は行わない。対象接続配下の全テーブル/カラムに対して既存の`resolvePrimary`をループ呼び出しし、実効主権限`READ`以上を持つスキーマ名の集合を都度算出する（UNIT-04のCaffeineキャッシュがヒットする前提）

---

## 3. Security

### 3.1 SQL読み取り専用検証・パラメータ検出（Q6=A、BR-QUERY-01、§2）
- 新規クラス`QuerySqlAnalyzer`が、JSqlParserでSQL文字列を1回構文解析し、その解析結果（AST）を保持する
- `isReadOnly()`相当のメソッドで、パース結果が単一の`Select`文であることのみを検証する（式レベルの許可リスト検証は行わない、BR-QUERY-01）。パース不能・複数ステートメント・非SELECT文はすべて`false`（呼び出し側で`NonReadOnlyQueryException`を送出）
- `detectParameters()`相当のメソッドで、同一のASTを走査し`JdbcNamedParameter`ノードを収集してパラメータ名一覧を返す（§2、文字列リテラル内の`:`等の誤検出を避ける）
- 1回の解析結果を両方のメソッドで再利用するため、呼び出し側（`QueryExecutionService`/`SavedQueryService`）は`QuerySqlAnalyzer`のインスタンスを1つ生成してから両メソッドを呼び出す

### 3.2 `/api/queries/**`のアクセス制御（SECURITY-08）
- UNIT-02のSecurityFilterChain設定に、`/api/queries/**`を対象とする新規ルールを追加する: 認証済み（`APPROVED`状態のユーザ）であればロールを問わず許可する
- UNIT-05のCode Generationで判明した前例（既存の`/api/admin/**`→ADMIN限定の次に`/api/**`→`authenticated()`という汎用ルールが既に設定されており、新規ルール追加が不要だった）を踏まえ、Code Generation着手時に同様に既存の汎用ルールで賄えるか確認し、賄える場合は新規ルール追加を省略する

### 3.3 アクセス制御の粒度（既存決定の継続適用、BR-QUERY-04）
- 生SQL実行時のアクセス制御はスキーマ単位に限る（BR-QUERY-04、Functional Designで確定済み）。選択したスキーマ内であれば任意のテーブル/カラムを参照するSELECT文を実行できる

### 3.4 監査ログとの連携（既存決定の継続適用、SECURITY-03）
- クエリ実行のたびに`QueryExecutionRecord`（内部DB）と`AuditLogEntry`（`QUERY_EXECUTED`）の両方に記録する（BR-QUERY-10）
- 保存クエリの保存・編集・非表示化についても、それぞれ`QUERY_SAVED`/`QUERY_UPDATED`/`QUERY_RETIRED`を`AuditEventPublisher`（UNIT-02）経由で記録する

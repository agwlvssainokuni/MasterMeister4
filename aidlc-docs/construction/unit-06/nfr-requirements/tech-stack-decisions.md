# UNIT-06 クエリ保存・実行 - Tech Stack Decisions

`unit-06-nfr-requirements-plan.md`の回答（Q1〜Q6、全問A）に基づく。新規外部ライブラリの追加はなし（JSqlParserはUNIT-05で追加済みを再利用）。

---

## 1. スキーマ切替＋クエリ実行の接続管理方式（Q1=A）

`QueryExecutionService`のクエリ実行処理は、以下の手順で単一の物理JDBC接続上でスキーマ切替とクエリ実行を行う:

1. `DataSource dataSource = rdbmsConnectionService.getDataSource(connectionId)`（UNIT-03既存、HikariCPキャッシュ済み）
2. `Connection connection = dataSource.getConnection()`で物理接続を取得
3. `dialect.requiresSchemaSwitch()`が`true`の場合のみ`dialect.applySchemaSwitch(connection, schema)`を適用
4. `SingleConnectionDataSource(connection, true)`（`suppressClose=true`）で物理接続をラップし、`NamedParameterJdbcTemplate`に渡してクエリを実行
5. `finally`句で物理接続を明示的に`close()`する

読み取り専用の単発クエリであり、ロールバック等のトランザクション意味論を必要としないため、UNIT-05の`RecordBatchService`のような`DataSourceTransactionManager`＋`TransactionTemplate`は導入しない。この方式は`execute`（ad-hoc実行）・`executeSavedQuery`（保存クエリ実行）の両方で共通利用する。

## 2. ページングの適用方式（Q2=A）

ページング有効時は、ユーザの任意SELECT文をサブクエリとしてラップし、LIMIT/OFFSETを外側で付与する:

```sql
SELECT * FROM (<ユーザSQL>) AS mm_page LIMIT :pageSize OFFSET :offset
```

対象4方言（PostgreSQL/MySQL/MariaDB/H2）はいずれもLIMIT/OFFSET構文をサポートするため、方言分岐は不要（UNIT-05の`RecordQueryService`と同じLIMIT/OFFSET直書き方式を踏襲）。

**既知の制約（business-rules.mdに注記）**: 内側SQLにORDER BYが含まれていても、外側に別途ORDER BYを付与しないため、行順序の保持はSQL標準では厳密には保証されない。実務上は主要RDBMS実装（PostgreSQL/MySQL/MariaDB/H2）でLIMIT/OFFSETのみのサブクエリラップであれば内側の順序が保持されるが、100%の保証ではない旨をユーザに向けて明示しない（内部実装上の制約として文書化するに留める。要件上、明示的な代替策は求められていないため）。

総件数（`totalCount`）取得は、UNIT-05のQ3=A（毎回正確なCOUNT）と同じ方針で`SELECT COUNT(*) FROM (<ユーザSQL>) AS mm_count`を実行する。

## 3. クエリ実行タイムアウト（Q3=A）

`NamedParameterJdbcTemplate`の`JdbcTemplate.setQueryTimeout(秒数)`を設定する。デフォルト値は`application.yml`に`mm.app.query.execution-timeout-seconds`として設定可能とし、`AppProperties`経由で参照する（UNIT-02/03/05で確立した設定値管理方式を踏襲）。タイムアウト発生時は`SQLException`（`SQLTimeoutException`）がスローされ、UNIT-02のグローバル例外ハンドラで処理する。

## 4. 結果件数の安全上限（Q4=A）

ページング無効時の結果件数に上限を設ける。デフォルト10,000件とし、`application.yml`に`mm.app.query.max-result-rows`として設定可能とする（`AppProperties`経由）。超過時は400エラーで拒否し、ページングの有効化を促すメッセージを返す。判定方法は、ページング無効時も内部的に`LIMIT (上限+1)`を付与して取得し、件数が上限を超える場合はエラーとする（全件取得してから件数判定すると上限を設ける目的（メモリ保護）を果たせないため）。

## 5. 監査ログイベント（Q5=A）

UNIT-05のような閾値ベースの専用「大量データ取得」イベント種別は追加しない。`QUERY_EXECUTED`（BR-QUERY-10）が実行のたびにrowCountを含めて必ず記録される既存設計のままとする。`AuditEventType.java`への追加は`QUERY_EXECUTED`/`QUERY_SAVED`/`QUERY_UPDATED`/`QUERY_RETIRED`の4種のみ（Functional Design時点の決定を維持）。

## 6. スキーマ許可リスト判定（Q6=A）

`EffectivePermissionResolver`への新規メソッド追加は行わない。`QueryExecutionService`（または新設のヘルパー）が、対象接続配下の全テーブル/カラムに対して既存の`resolvePrimary`をループ呼び出しし、実効主権限`READ`以上を持つスキーマ名の集合を都度算出する。UNIT-04のCaffeineキャッシュ（テーブル/カラム単位、maximumSize=10,000, expireAfterWrite=30分）がヒットする前提のため、追加のキャッシュ層は不要と判断する。UNIT-05の`MasterDataService.listAccessibleConnections`/`listAccessibleTables`と同じ実装パターンを踏襲する。

## 7. 新規依存関係

なし。`NamedParameterJdbcTemplate`・`SingleConnectionDataSource`はSpring Framework既存機能、JSqlParserはUNIT-05で追加済みの依存関係を再利用する。

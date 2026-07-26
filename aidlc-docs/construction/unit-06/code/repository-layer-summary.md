# UNIT-06 クエリ保存・実行 - Repository Layer Summary

## マイグレーション

- `V15__create_saved_query_table.sql`: `saved_query`テーブル（`connection_id`はFK、`ON DELETE CASCADE`、`sql`はCLOB）
- `V16__create_query_execution_record_table.sql`: `query_execution_record`テーブル（`connection_id`/`saved_query_id`はFK制約なし、`sql`/`params`はCLOB）
- 内部DBは常にH2（`jdbc:h2:file:...`）のため、CLOB型を直接指定した（対象RDBMS側の方言は無関係）

## エンティティ

- `Visibility`（enum、`PUBLIC`/`PRIVATE`）
- `SavedQuery`: `connectionId`固定・スキーマ非依存（Q11）。`update()`（BR-QUERY-07）・`retire()`（BR-QUERY-08、un-retireなし）のドメインメソッドを持つ
- `QueryExecutionRecord`: 不変の実行記録。ゲッターのみ

## リポジトリ

- `SavedQueryRepository`: `findAllByConnectionId(Long)`を追加
- `QueryExecutionRecordRepository`: 追加メソッドなし（`save`のみが主用途、閲覧機能はUNIT-08が追加）

## テスト結果

- `SavedQueryRepositoryTest`（4件）: 全フィールド永続化、`connectionId`絞込、`RdbmsConnection`削除時のCASCADE削除実証、`update`/`retire`の反映
- `QueryExecutionRecordRepositoryTest`（2件）: ad-hoc実行（`savedQueryId`がnull）・保存クエリ経由実行（`params`あり）の永続化
- 全6件成功（`./gradlew :backend:test --tests "cherry.mastermeister.query.repository.*"`）

# UNIT-08 クエリ履歴 - Business Logic Summary

## 作成・修正したクラス

- **`queryhistory/dto/`**: `QueryHistoryConnectionResponse`, `QueryHistoryRecordResponse`, `QueryHistoryPageResponse`（新規、実装時追加、Spring Data JPAの`Page`をそのまま返さず独自の軽量ラッパーへ変換）, `QueryHistorySearchCriteria`（Service層内部用、`executedByScope`を含まない）, `ExecutedByScope`, `QueryType`
- **`queryhistory/QueryHistorySpecifications`**: `Specification<QueryExecutionRecord>`の静的ファクトリメソッド集（`connectionIdEquals`, `executedByEquals`, `executedAtFrom`, `executedAtTo`, `schemaNameEquals`, `sqlContains`）。プロジェクト内初のSpecification API採用
- **`queryhistory/QueryHistoryService`（COMP-17）**: `listConnections`, `listSchemas`, `listHistory`の3メソッド。絞込・ページング・名前解決（実行者名・保存クエリ名の一括解決、N+1回避）を担う。ロール判定ロジックは持たず、呼び出し元が計算した`executedByFilter`（`Long`、nullなら全ユーザ対象）を受け取るのみ
- **`query/repository/QueryExecutionRecordRepository`（UNIT-06既存、修正）**: `JpaSpecificationExecutor<QueryExecutionRecord>`を追加実装。新規メソッド4種（接続一覧・スキーマ名一覧それぞれの実行者スコープ別バリエーション、BR-QUERYHISTORY-10・11の情報漏洩対策を反映）
- **`query/repository/SavedQueryRepository`（UNIT-06既存、修正）**: `findAllByIdIn(Collection<Long>)`を追加

## テスト結果

- `QueryHistoryServiceTest`（Mockito）: 9件（接続一覧の実行者スコープ絞込・削除済み接続のプレースホルダー、スキーマ名一覧の実行者スコープ絞込、履歴一覧の保存クエリ名解決（正常・削除済み・AD_HOC）、実行者名解決（正常・不明ユーザ））
- `QueryHistorySpecificationsTest`（`@DataJpaTest`）: 6件（各ファクトリメソッド単体、複合条件のAND結合確認）
- `QueryExecutionRecordRepositoryTest`（UNIT-06既存、修正）: 新規4メソッドのテストケースを追加
- `SavedQueryRepositoryTest`（UNIT-06既存、修正）: `findAllByIdIn`のテストケースを追加

全件成功（`./gradlew :backend:test --tests "cherry.mastermeister.queryhistory.*" --tests "cherry.mastermeister.query.repository.*"`）

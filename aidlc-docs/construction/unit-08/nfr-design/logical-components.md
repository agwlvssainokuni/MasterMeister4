# UNIT-08 クエリ履歴 - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.queryhistory`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。

---

## 1. クエリ履歴ドメイン（`cherry.mastermeister.queryhistory`）

### QueryHistoryController（Q3=A）

単一のControllerに3エンドポイントをまとめる。

- `GET /api/query-history/connections?executedByScope=ALL|MINE`（省略可、デフォルト`ALL`） — 履歴実績ベースの接続一覧（`QueryHistoryService`に委譲、BR-QUERYHISTORY-11）
- `GET /api/query-history/{connectionId}/schemas?executedByScope=ALL|MINE`（同上） — 対象接続の履歴実績ベースのスキーマ名一覧（BR-QUERYHISTORY-10、承認前レビューで実行者スコープによるフィルタを追加）
- `GET /api/query-history/{connectionId}?executedByScope=...&executedAtFrom=...&executedAtTo=...&schemaName=...&sqlKeyword=...&page=...&pageSize=...` — 履歴一覧取得（絞込・ページング）

各エンドポイントで`@AuthenticationPrincipal Jwt principal`から`currentUserId(principal)`（`principal.getSubject()`）・ロール（`principal.getClaimAsString("role")`）を取得する（nfr-design-patterns.md §1.2）。既存の`SecurityConfig`の`.requestMatchers("/api/**").authenticated()`ルールでカバーされるため、新規のSecurityFilterChainルール追加は不要。

Controllerは、ロール判定の結果を「絞込対象を自分のみに限定するユーザID（`Long executedByFilter`、`null`なら全ユーザ対象）」という単一の値に変換してからService層へ渡す。一般ユーザは常に`executedByFilter = currentUserId(principal)`。管理者は`executedByScope=ALL`なら`executedByFilter = null`、`executedByScope=MINE`なら`executedByFilter = currentUserId(principal)`。**Serviceのシグネチャにロール（`isAdmin`等）そのものを渡さない**（nfr-design-patterns.md §1.2の方針どおり、ロール判定ロジックをServiceに持ち込まないため）。

### QueryHistoryService（COMP-17、Q2=A）

絞込・ページング・名前解決の3責務を1クラスに集約する。

- `listConnections(Long executedByFilter): List<QueryHistoryConnectionView>`
  - `executedByFilter`が非nullの場合は`executedBy = executedByFilter`、nullの場合は条件なしで、`QueryExecutionRecordRepository`から`connectionId`のDISTINCT一覧を取得（新規カスタムクエリメソッド、後述）
  - 取得した`connectionId`群を`RdbmsConnectionRepository.findAllById(...)`（UNIT-03既存）で一括解決し、表示名を付与。見つからない場合は「(削除済み接続)」のプレースホルダー
- `listSchemas(Long connectionId, Long executedByFilter): List<String>`
  - `executedByFilter`が非nullの場合は`executedBy = executedByFilter`、nullの場合は条件なしで、`QueryExecutionRecordRepository`から対象接続の`schemaName`のDISTINCT一覧を取得（新規カスタムクエリメソッド）。`listConnections`と同じ`executedByFilter`の受け渡し方針（承認前レビューでの是正: 当初`connectionId`のみを受け取る設計だったが、実行者スコープでフィルタしないと一般ユーザが他ユーザのスキーマ名を知りうる情報漏洩になるため追加）
- `listHistory(Long connectionId, Long executedByFilter, QueryHistorySearchCriteria criteria, Pageable pageable): Page<QueryHistoryRecordView>`
  - `QueryHistorySpecifications`（nfr-design-patterns.md §2.1）で動的に`Specification<QueryExecutionRecord>`を組み立てる。`connectionIdEquals(connectionId)`は常に付与し、`executedByFilter`が非nullの場合のみ`executedByEquals(executedByFilter)`を追加する（`executedByFilter`自体は実行者スコープの絞込結果であり、Serviceはこれを他の絞込条件と同列に扱うのみでロール判定には関与しない）
  - 組み立てた`Specification`で`QueryExecutionRecordRepository.findAll(spec, pageable)`（`JpaSpecificationExecutor`）を呼び出す
  - 取得した`Page<QueryExecutionRecord>`の内容から`savedQueryId`（非null）・`executedBy`をそれぞれユニークに集約し、`SavedQueryRepository.findAllByIdIn(...)`（新規）・`UserRepository.findAllById(...)`（既存標準メソッド）で一括取得
  - 上記を結合して`QueryHistoryRecordView`のページ結果に変換して返す

### QueryHistorySpecifications（新設、tech-stack-decisions.md §1・nfr-design-patterns.md §2.1）

`Specification<QueryExecutionRecord>`を生成する静的ファクトリメソッド集。

- `connectionIdEquals(Long connectionId)`
- `executedByEquals(Long userId)`
- `executedAtFrom(Instant from)` / `executedAtTo(Instant to)`
- `schemaNameEquals(String schemaName)`
- `sqlContains(String keyword)`（`CriteriaBuilder.like`、パラメータバインドでSQLインジェクションを構造的に防止）

---

## 2. Repository拡張

### QueryExecutionRecordRepository（既存、UNIT-06実装済み）

- `JpaSpecificationExecutor<QueryExecutionRecord>`を追加実装（Q1=A関連の動的クエリのため）
- 新規カスタムクエリメソッド:
  - `List<Long> findDistinctConnectionIdByExecutedBy(Long executedBy)` — 実行者スコープが「自分のみ」の接続一覧用
  - `List<Long> findDistinctConnectionId()` — 実行者スコープが「全ユーザ」の接続一覧用
  - `List<String> findDistinctSchemaNameByConnectionIdAndExecutedBy(Long connectionId, Long executedBy)` — 実行者スコープが「自分のみ」のスキーマ名一覧用
  - `List<String> findDistinctSchemaNameByConnectionId(Long connectionId)` — 実行者スコープが「全ユーザ」のスキーマ名一覧用

### SavedQueryRepository（既存、UNIT-06実装済み）

- 新規メソッド`List<SavedQuery> findAllByIdIn(Collection<Long> ids)`を追加（Spring Data JPAの命名規約に基づく標準的な`In`句メソッド）

### UserRepository（既存、UNIT-02実装済み）

- 変更なし。標準`JpaRepository.findAllById(Iterable<Long>)`をそのまま利用

### RdbmsConnectionRepository（既存、UNIT-03実装済み）

- 変更なし。標準`JpaRepository.findAllById(Iterable<Long>)`をそのまま利用

---

## 3. DTO設計

- `QueryHistoryConnectionResponse`（`connectionId`, `displayName`） — 接続一覧APIレスポンス1件
- `QueryHistoryRecordResponse`（`QueryHistoryRecordView`のフィールドに対応: `id`, `executedBy`, `executorDisplayName`, `connectionId`, `schemaName`, `sql`, `savedQueryId`, `savedQueryName`, `queryType`, `rowCount`, `durationMillis`, `executedAt`） — 履歴一覧APIレスポンス1件
- `QueryHistorySearchRequest`（`executedByScope`, `executedAtFrom`, `executedAtTo`, `schemaName`, `sqlKeyword`, `page`, `pageSize` — Controller層でクエリパラメータをバインドするリクエストDTO。`executedAtFrom`≤`executedAtTo`の相関検証を`@AssertTrue`＋`@JsonIgnore`で実装、nfr-design-patterns.md §1.1）
- `QueryHistorySearchCriteria`（`executedAtFrom`, `executedAtTo`, `schemaName`, `sqlKeyword` — Service層（`QueryHistoryService.listHistory`）に渡すDTO。`QueryHistorySearchRequest`との違いは`executedByScope`を含まない点で、これはControllerが`executedByFilter`（`Long`、上記QueryHistoryController節参照）へ変換済みのため、Service層のDTOには持ち込まない）

---

## 4. 依存関係の追加

なし。Spring Data JPAの`JpaSpecificationExecutor`は既存の`spring-boot-starter-data-jpa`に含まれる標準機能。

---

## 5. 設定（`AppProperties`拡張）

新規設定項目なし。ページサイズ上限はBean Validationの`@Max`アノテーションでDTOに直接埋め込むため、`application.yml`外部化は不要と判断する（他ユニットの運用時調整可能な設定値とは性質が異なる固定的な安全上限のため）。

---

## 6. Spring Security設定の変更

なし。既存の`.requestMatchers("/api/**").authenticated()`ルールが`/api/query-history/**`をカバーする（UNIT-05/06/07のCode Generation時の前例と同様の確認結果）。

---

## 7. マイグレーション

新規マイグレーション`V17__add_index_query_execution_record_connection_executed_at.sql`を追加する（既存V16は変更しない）。

```sql
CREATE INDEX idx_query_execution_record_connection_executed_at
    ON query_execution_record (connection_id, executed_at);
```

---

## 8. 監査ログ連携

なし。本ユニットはDB更新を伴わない読み取り専用の閲覧処理のみであり、`AuditEventPublisher`への新規イベント発行は行わない。

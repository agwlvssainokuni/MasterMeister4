# UNIT-08 クエリ履歴 - Tech Stack Decisions

`unit-08-nfr-requirements-plan.md`の回答（Q1〜Q5、推奨どおり全問A）に基づく。新規外部ライブラリの追加はなし。

---

## 1. 絞込クエリの実装方式（Q1=A）

`QueryExecutionRecordRepository`に`JpaSpecificationExecutor<QueryExecutionRecord>`を実装させ、`Specification<QueryExecutionRecord>`を動的に組み立てる（プロジェクト内初のSpring Data JPA Specification API採用）。絞込条件（実行日時範囲・実行者スコープ・対象スキーマ・SQLテキスト）はいずれも任意指定のため、指定された条件に対応する`Predicate`のみを`Specification.where(...).and(...)`で連結する。未指定の条件は`Specification`自体を追加しない（`null`分岐によるANDスキップ）ことで対応する。

```java
// イメージ（詳細実装はCode Generation時に確定）
Specification<QueryExecutionRecord> spec = Specification.where(connectionIdEquals(connectionId));
if (executedByScope == MINE) {
    spec = spec.and(executedByEquals(userId));
}
if (executedAtFrom != null) {
    spec = spec.and(executedAtGreaterThanOrEqual(executedAtFrom));
}
// ...以下同様
```

## 2. インデックス設計（Q2=A）

新規マイグレーション（V17予定、Code Generation時に確定）で、`(connection_id, executed_at)`の複合インデックスを`query_execution_record`テーブルに追加する。

```sql
CREATE INDEX idx_query_execution_record_connection_executed_at
    ON query_execution_record (connection_id, executed_at);
```

本ユニットの主要クエリパターン（`connection_id`で絞り込み`executed_at`降順にソート）をこのインデックスでカバーする。既存の`executed_by`・`executed_at`・`saved_query_id`単独インデックスは変更しない（他の絞込条件との組み合わせにも引き続き寄与するため）。

## 3. 絞込パラメータの入力検証（Q3=A、SECURITY-05）

- ページサイズ: 上限値を設け、超過時は400エラー（Bean Validationまたはコントローラでの明示的検証、具体的な上限値はCode Generation計画時に確定）
- 日時範囲: `executedAtFrom`が`executedAtTo`より後の場合は400エラー
- `executedByScope`: 許容値（`ALL`/`MINE`）以外は400エラー（enumバインドの標準的な失敗応答）
- SQLキーワード: JPA Criteria API（`CriteriaBuilder.like`相当、Specification経由）のパラメータバインドで扱われるため、追加のサニタイズ処理は不要（文字列連結による組み立てを行わない）

## 4. 実行者スコープの権限判定レイヤー（Q4=A、SECURITY-06）

`QueryHistoryController`で、JWTの`role`クレーム（`SecurityConfig`で確立済みの`Jwt principal`からのロール解決、UNIT-05/06と同じパターン）を判定する。一般ユーザ（`Role.USER`）が`executedByScope=ALL`を指定した場合、Service層の呼び出し前にController側で`MINE`へ強制する。管理者（`Role.ADMIN`）のみ`ALL`を有効な指定として扱う。

## 5. 実行者名・保存クエリ名の解決方式（Q5=A）

`QueryHistoryService`（仮称）が一覧取得結果（`Page<QueryExecutionRecord>`）から、`executedBy`・`savedQueryId`（非null）をそれぞれユニークに集約し、`UserRepository.findAllById(...)`・`SavedQueryRepository`への新規`findAllByIdIn(Collection<Long>)`メソッドで一括取得する。取得結果を`Map<Long, String>`に変換し、各`QueryExecutionRecord`を`QueryHistoryRecordView`へ変換する際に名前を解決する。新規のキャッシュ層（Caffeine等）は導入しない。

## 6. 新規依存関係

なし。Spring Data JPAの`JpaSpecificationExecutor`は既存の`spring-boot-starter-data-jpa`に含まれる標準機能であり、追加のライブラリ依存は発生しない。

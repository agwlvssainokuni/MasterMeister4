# UNIT-08 クエリ履歴 - NFR Design Patterns

`unit-08-nfr-design-plan.md`の回答（Q1〜Q4、推奨どおり全問A）に基づく実装パターンを記載する。

---

## 1. Security（入力検証・エラーハンドリング）

### 1.1 絞込パラメータの検証エラー表現（Q1=A、SECURITY-05）

- ページサイズ上限、`executedByScope`の許容値（`ALL`/`MINE`）はBean Validation標準の制約アノテーション（`@Max`、enumバインド）で検証する
- 日時範囲の相関検証（`executedAtFrom`≤`executedAtTo`）は、リクエストDTOに`@AssertTrue`メソッド（例: `isDateRangeValid()`）を設け検証する。UNIT-07で発見済みの教訓（`@AssertTrue`メソッドがJacksonのgetter規則に合致しレスポンスJSONへ漏れる問題）を踏まえ、本ユニットのリクエストDTOに`@AssertTrue`を追加する際は`@JsonIgnore`を忘れず付与する
- 検証失敗時はUNIT-02〜07で確立済みの標準400エラー応答（`MethodArgumentNotValidException`のグローバルハンドラ処理）とする。専用例外は新設しない

### 1.2 実行者スコープのフェイルクローズ（Q4=A、SECURITY-06、重要）

`QueryHistoryController`で、`@AuthenticationPrincipal Jwt principal`から`principal.getClaimAsString("role")`を取得し判定する（tech-stack-decisions.md §4）。

- 一般ユーザ（`role != "ADMIN"`）が`executedByScope=ALL`を指定した場合、Controller側で`MINE`へ強制してからService層を呼び出す
- Serviceのメソッドシグネチャには「ロール」ではなく「絞込済みの実行者ID（適用する場合のみ、`Optional<Long>`または`null`許容）」を渡す。ロール判定ロジック自体はServiceに一切持ち込まない（関心の分離、テスト容易性のため）
- 多層防御（Service層での再判定）は行わない。本ユニットの規模・リスクに対して過剰と判断（NFR Design Q4=A）

---

## 2. Performance（動的クエリ・インデックス）

### 2.1 Specificationによる動的絞込（tech-stack-decisions.md §1の詳細化）

`QueryHistorySpecifications`（静的ファクトリメソッド集、UNIT-05/06に前例のないパターンのため専用クラスとして新設）に、各条件に対応する`Specification<QueryExecutionRecord>`生成メソッドを用意する: `connectionIdEquals(Long)`、`executedByEquals(Long)`、`executedAtFrom(Instant)`、`executedAtTo(Instant)`、`schemaNameEquals(String)`、`sqlContains(String)`。`QueryHistoryService`がこれらを条件の有無に応じて`Specification.where(...).and(...)`で連結する。

### 2.2 インデックス（NFR Requirements Q2=A）

新規マイグレーション`V17__add_index_query_execution_record_connection_executed_at.sql`で`(connection_id, executed_at)`の複合インデックスを追加する（Code Generation時に実施、既存V16は変更しない）。

---

## 3. Logical Components（配置方針の要約、詳細はlogical-components.md）

- 絞込・ページング・名前解決: `QueryHistoryService`（Q2=A、3責務を1クラスに集約）
- 動的クエリ構築: `QueryHistorySpecifications`（新設、静的ファクトリメソッド集）
- Controller: 単一の`QueryHistoryController`（Q3=A、3エンドポイント）

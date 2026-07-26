# UNIT-08 クエリ履歴 - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（JpaSpecificationExecutorによる動的絞込、`(connection_id, executed_at)`複合インデックス新設、絞込パラメータの入力検証、Controller層でのロール判定によるフェイルクローズ、findAllByIdInによる名前解決）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## Scalability Patterns（前提確認）

requirements.mdの前提（同時利用者約10名規模）により、新規のスケーリング機構は不要（N/A、UNIT-01〜07と同様）。

## Reliability Patterns（既に決定済みの事項の確認）

新規マイグレーション`V17__add_index_query_execution_record_connection_executed_at.sql`をCode Generation時に追加する（既存のV16マイグレーションを修正せず、新規ファイルとして追加。Flywayの原則どおり適用済みマイグレーションは変更しない）。

## Security Patterns（既に決定済みの事項の確認）

`/api/query-history/**`は、既存の`SecurityConfig`の`.requestMatchers("/api/**").authenticated()`ルールでカバーされる（UNIT-05/06/07と同様、一般ユーザ向け機能でロール不問）。`/api/admin/**`のような専用ルール追加は不要と判断する（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全4問、推奨どおり全問Aで確定）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 曖昧な回答なし。ただしFunctional Design遡及修正（接続選択画面も履歴実績ベースに変更）を受け、Q3の対象エンドポイント数を2→3に修正済み
- [x] Step C: `nfr-design-patterns.md`（エラー表現・例外設計、絞込・権限判定の詳細パターン）を作成する
- [x] Step D: `logical-components.md`（新設する論理コンポーネント、Controller構成）を作成する
- [x] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Security Patterns）
絞込パラメータ（ページサイズ超過、日時範囲の開始＞終了、`executedByScope`の不正値）の検証エラー表現は？

A) Bean Validation（`@Valid`＋制約アノテーション、`executedAtFrom`≤`executedAtTo`のような相関検証は`@AssertTrue`）でリクエストDTOレベルの検証を行い、UNIT-02〜07で確立済みの標準400エラー応答（`MethodArgumentNotValidException`ハンドリング）で統一する。専用例外は新設しない

B) 専用の`ApiException`サブクラスを新設する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 2（Logical Components、重要）
履歴一覧の絞込・ページング・名前解決（実行者名・保存クエリ名の一括解決）ロジックの配置は？

A) 新規`QueryHistoryService`を新設し、絞込・ページング・名前解決の3責務を1クラスに集約する（UNIT-06の`QueryExecutionService`と同程度の粒度の単一サービスクラス）

B) 絞込・ページングと名前解決を別クラスに分離する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 3（Logical Components）
Controller構成は？（Functional Design遡及修正により、接続選択画面用の一覧取得APIもUNIT-06既存を再利用せず新規追加になったため、対象は3エンドポイントに変更）

A) 単一の`QueryHistoryController`に3エンドポイント（履歴一覧取得・履歴記録済み接続一覧取得・履歴記録済みスキーマ名一覧取得）をまとめる（UNIT-05のMasterDataController、UNIT-06のQueryControllerと同程度の規模）

B) 複数のControllerに分割する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 4（Security Patterns、SECURITY-06・重要）
実行者スコープのフェイルクローズ（一般ユーザは「全ユーザ」指定不可、tech-stack-decisions.md §4で「Controller層で判定」と決定済み）の実装パターンは？

A) Controller層でのみ判定する。一般ユーザが`ALL`を指定した場合はController側で`MINE`へ強制したうえでService層のメソッドへ渡す。Serviceのシグネチャは「絞込済みスコープ（実際に適用するuserIdフィルタの有無）」のみを受け取るシンプルな形にし、ロール判定ロジック自体をServiceに持ち込まない

B) Controller層の判定に加え、Service層でも呼び出し元のロールを再確認する多層防御を行う

C) Other (please describe after [Answer]: tag below)

[Answer]: A

# UNIT-08 クエリ履歴 Functional Design Plan

## 対象ユニット概要

- **対応エピック**: Epic 8、**対応ストーリー**: STORY-8.1, 8.2
- **対応要件**: FR-8.1〜FR-8.4
- **対応コンポーネント**: COMP-17
- **責務**: クエリ実行履歴の一覧・絞込、履歴からの画面遷移（実行/保存/ビルダー）
- **前提ユニット**: UNIT-01〜UNIT-04, UNIT-06, UNIT-07（実行記録の蓄積と、遷移先である実行・保存・ビルダーが揃っている必要があるため）
- **パッケージ**: `cherry.mastermeister.queryhistory`
- **PBT対象**: なし（STORY-8.1, 8.2ともにPBT対象外と明記）

## 既存資産の確認結果

- UNIT-06で`QueryExecutionRecord`エンティティ・テーブル（`query_execution_record`、V16マイグレーション）が実装済み。フィールド: `id`, `executedBy`, `connectionId`, `schemaName`, `sql`, `params`（JSON文字列、nullable）, `savedQueryId`（nullable、ad-hocならnull）, `rowCount`, `durationMillis`, `executedAt`。インデックスは`executedBy`・`executedAt`・`savedQueryId`の3本
- `QueryExecutionRecordRepository`は`JpaRepository`のみでカスタムfinderなし。絞込・ページング用メソッドの追加が本ユニットの主要作業
- **重要な制約**: 現状`QueryExecutionRecord`は**成功した実行のみ**記録される（例外発生時は`persistExecutionRecord`に到達せず記録されない）。成功/失敗を区別するフィールド自体が存在しない
- フロントエンドのナビゲーション項目`queryHistory`（`path: '/query-history'`）はUNIT-01で仮予約済み、ページ実体は未実装
- UNIT-06/07で確立済みのパターン: 接続選択画面→機能画面の2画面構成、router state経由でのSQL引き継ぎによる画面間遷移

## 実行計画

- [x] Step 1: ユニット定義・関連ストーリー・既存コンポーネント（UNIT-06 QueryExecutionRecord）の再確認（完了、本ファイル冒頭に反映）
- [x] Step 2: 本計画ファイルの作成・質問の提示
- [x] Step 3: ユーザからの回答収集・曖昧性チェック（全8問推奨どおりA、曖昧な回答なし）
- [x] Step 4: `business-logic-model.md` 作成（履歴一覧取得・絞込ロジック、ページング方式）
- [x] Step 5: `business-rules.md` 作成（BR-QUERYHISTORY-01〜09、絞込条件・アクセス制御・失敗記録の扱い等のルール化）
- [x] Step 6: `domain-entities.md` 作成（既存QueryExecutionRecordの参照、QueryHistorySearchCriteria/QueryHistoryRecordViewの新規DTO定義）
- [x] Step 7: `frontend-components.md` 作成（画面構成、一覧・絞込UIコンポーネント階層、状態管理、API連携ポイント）
- [x] Step 8: 完了メッセージ提示・承認待ち

---

## 質問

以下の質問に回答してください。各質問は文字（A, B, C...）で回答し、最後の選択肢（Other）を選ぶ場合は自由記述してください。

## Question 1
現状`QueryExecutionRecord`は成功した実行のみ記録され、失敗（アクセス不可・タイムアウト・結果過大等の例外発生時）は記録されない設計です。本ユニットでの扱いは？

A) 現状の設計を踏襲する（成功した実行のみを「実行履歴」として扱う。`QueryExecutionRecord`テーブル・UNIT-06の記録ロジックへの変更は行わず、本ユニットは閲覧・絞込機能のみを追加する）

B) 失敗した実行も履歴に含めるよう、テーブル・UNIT-06の記録ロジックまで遡って拡張する（成功/失敗フラグ・エラー内容の追加が必要）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 2
画面構成（対象接続の扱い）は？

A) UNIT-05/06/07と同じ「接続選択画面→対象接続の履歴一覧画面」の2画面構成（`connectionId`で絞り込む）

B) 接続非依存で、アクセス可能な全接続の履歴を横断的に一覧表示する（接続選択画面を設けない）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 3
STORY-8.1の受け入れ基準にある「実行者（全ユーザ／自分のみ）」による絞込の権限モデルは？

A) 管理者のみ「全ユーザ」の履歴を閲覧・絞込可能。一般ユーザは常に自分の実行履歴のみ閲覧可能（「全ユーザ」絞込オプション自体を管理者にのみ表示する）

B) ロールに関わらず全ユーザが、対象接続へのアクセス権さえあれば他人の実行履歴も含めて閲覧可能

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 4
履歴に記録された接続・スキーマへのアクセス権が、閲覧時点で失われていた場合（UNIT-04で権限剥奪等）の扱いは？

A) 記録は不変として扱い、閲覧時点のアクセス権に関わらず自分が実行した履歴は常に閲覧可能とする（UNIT-02監査ログと同じ「記録の不変性」の考え方）

B) 閲覧時点で対象接続・スキーマへのアクセス権を`EffectivePermissionResolver`で再判定し、権限がなければ一覧から除外する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 5
SQLテキスト検索（FR-8.3）の実装方式は？

A) 単純な部分一致（`LIKE '%keyword%'`）によるSQL全文検索。CLOB型に対する検索でインデックスは持たず、小〜中規模想定のパフォーマンスとして許容する（UNIT-04/06で確認済みの規模感を踏襲）

B) 全文検索エンジン等、高度な検索機能を別途導入する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 6
`savedQueryId`がある履歴（保存クエリ経由の実行）の一覧表示方式は？

A) `savedQueryId`が指す`SavedQuery`を都度取得し、保存クエリ名を一覧に表示する。対象の保存クエリが既に非表示化（retire）・削除されていた場合は「(削除済み)」等のプレースホルダー表示にする

B) 保存クエリ経由か直接入力か（FR-8.2の種別表示）のみ示し、具体的な保存クエリ名までは表示しない

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 7
履歴からの画面遷移（FR-8.4、STORY-8.2）の実装方式は？

A) UNIT-06/07で確立したrouter state経由のパターンを踏襲する（クエリ実行画面・保存クエリ新規作成画面・クエリビルダー画面へ、SQL・スキーマをrouter state経由で引き継いで遷移）

B) 異なる方式にする

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 8
一覧のページング実装方式は？

A) Spring Data JPAの標準`Pageable`/`Page`機構を用いる（`QueryExecutionRecordRepository`への新規finderメソッドとして自然に実装できるため）

B) UNIT-06のクエリ実行結果一覧と同じ、サブクエリラップ方式の独自LIMIT/OFFSETページングを踏襲する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

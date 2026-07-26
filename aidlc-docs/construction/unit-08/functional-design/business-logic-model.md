# UNIT-08 クエリ履歴 - Business Logic Model

## 1. 概要

本ユニットは、UNIT-06で先行実装済みの`QueryExecutionRecord`（クエリ実行1回分の記録）を対象に、閲覧・絞込・ページングを提供する。新規のデータ記録処理は持たない（Q1=A）。

## 2. 履歴一覧取得フロー（FR-8.1〜FR-8.3）

1. 接続選択画面で、ユーザがアクセス可能な接続一覧（UNIT-06既存の`GET /api/queries/connections`を再利用）から対象接続を1つ選択する（Q2=A）
2. 履歴一覧画面で、絞込条件（実行日時範囲・実行者スコープ・対象スキーマ・SQLテキスト）とページ番号を指定し、一覧を取得する
3. Service層は以下の順で処理する:
   a. 呼び出しユーザのロールを判定する（`User.getRole()`）
   b. 実行者スコープの妥当性検証（後述§3）
   c. `QueryExecutionRecordRepository`への新規絞込クエリメソッドを、`Pageable`（Spring Data JPA標準、Q8=A）付きで呼び出す
   d. 取得した`QueryExecutionRecord`一覧のうち、`savedQueryId`が非nullのものをユニークに集約し、`SavedQueryRepository`から一括取得する（N+1回避）
   e. 取得した`executedBy`（ユーザID）をユニークに集約し、`UserRepository`から一括取得し、表示用の実行者名（`fullName`）に解決する
   f. 上記を結合し、`QueryHistoryRecordView`（表示用DTO、§5参照）のページ結果として返す

## 3. 絞込ロジック（FR-8.3、STORY-8.1）

すべての絞込条件はANDで組み合わされる（複数条件の同時指定が可能）。

- **対象接続**: 画面構成上、常に1つの`connectionId`に固定される（Q2=A）
- **実行日時範囲**: `executedAt`が指定範囲内（開始のみ・終了のみの片側指定も許容）
- **実行者スコープ**（Q3=A）:
  - 一般ユーザ（`Role.USER`）は常に自分の実行履歴のみが対象となる。リクエストで「全ユーザ」を指定してもサーバ側で自分のみに強制する（フェイルクローズ、UNIT-04/05で確立した「疑わしい場合はより厳格な側」の方針を踏襲）
  - 管理者（`Role.ADMIN`）は「全ユーザ」または「自分のみ」を選択可能。「全ユーザ」を選んだ場合は`executedBy`条件を付けない。「自分のみ」を選んだ場合は`executedBy = 自分のuserId`で絞り込む
- **対象スキーマ**: `schemaName`の完全一致
- **SQLテキスト**: `sql`列に対する部分一致検索（`LIKE '%keyword%'`、Q5=A）。大文字小文字の区別はDB方言のデフォルト照合順序に従う（明示的な大文字小文字無視処理は行わない）

## 4. ページング（Q8=A）

Spring Data JPAの`Pageable`/`Page`を新規に導入する（プロジェクト内初の採用。UNIT-06のクエリ実行結果ページング（サブクエリラップ方式）とは独立した仕組みであり、混同しない）。

- デフォルトソート順: `executedAt`降順（新しい実行が先頭）
- ページサイズはUNIT-06のクエリ実行結果一覧と同程度の既定値を踏襲する（NFR Requirements段階で確定）

## 5. 表示用データの結合

- `QueryHistoryRecordView`（DTO、永続化なし）: `QueryExecutionRecord`の全フィールド + `executorDisplayName`（`User.fullName`から解決、削除済みユーザの場合は後述§6） + `savedQueryName`（`savedQueryId`非nullの場合に`SavedQuery.name`から解決、対象が非表示化・削除済みの場合は「(削除済み)」等のプレースホルダー、Q6=A） + `queryType`（`savedQueryId`の有無から導出する種別、FR-8.2）

## 6. 参照整合性の扱い（既存記録の不変性、Q4=Aと同じ考え方の延長）

- `executedBy`が指すユーザが退会・削除等で存在しなくなっていた場合（現状のUser削除機能はUNIT-02スコープ外だが将来のための備え）、`executorDisplayName`解決に失敗するため「(不明なユーザ)」等のプレースホルダーで表示する
- `savedQueryId`が指す`SavedQuery`が非表示化（`retired=true`）されている場合、非表示化済みであってもレコード自体は存在するため名前解決は可能。名前に「(非表示)」等の補助表示を付けるかはCode Generation時にUIデザインとして決定する。物理削除された場合（`SavedQueryRepository`に削除APIは存在しないため現状発生しない想定だが、念のため）は「(削除済み)」のプレースホルダーとする

## 7. 履歴からの画面遷移（FR-8.4、STORY-8.2、Q7=A）

UNIT-06/07で確立済みのrouter state経由のパターンをそのまま踏襲する。履歴一覧の各行（または詳細）から、以下3つの遷移を提供する。

- **クエリ実行画面へ**: `navigate('/query-execution/{connectionId}', { state: { sql, schemaName } })`
- **保存クエリ新規作成画面へ**: `navigate('/saved-queries/{connectionId}/new', { state: { sql, schemaName } })`
- **クエリビルダー画面へ**: `navigate('/query-builder/{connectionId}', { state: { sql, schemaName } })`

いずれも遷移先ページは既にrouter stateの受信ロジックを実装済み（UNIT-06/07のCode Generationで対応）であるため、本ユニットからは送信側の実装のみを追加する。

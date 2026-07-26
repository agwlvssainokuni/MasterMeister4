# UNIT-08 クエリ履歴 - Business Logic Model

## 1. 概要

本ユニットは、UNIT-06で先行実装済みの`QueryExecutionRecord`（クエリ実行1回分の記録）を対象に、閲覧・絞込・ページングを提供する。新規のデータ記録処理は持たない（Q1=A）。

## 2. 履歴一覧取得フロー（FR-8.1〜FR-8.3）

1. 接続選択画面で、ユーザは**履歴に実際に記録されている接続**の一覧（新規API、§3-1参照）から対象接続を1つ選択する（Q2=A）。UNIT-06既存の`GET /api/queries/connections`（現在アクセス可能な接続のみ）は使わない。理由はスキーマ絞込セレクタと同じで、BR-QUERYHISTORY-04（記録は不変、閲覧時点のアクセス権を問わない）を貫徹するため（承認前レビューでの発見・是正、後述BR-QUERYHISTORY-11）
2. 履歴一覧画面で、絞込条件（実行日時範囲・実行者スコープ・対象スキーマ・SQLテキスト）とページ番号を指定し、一覧を取得する
3. Service層は以下の順で処理する:
   a. 呼び出しユーザのロールを判定する（`User.getRole()`）
   b. 実行者スコープの妥当性検証（後述§3）
   c. `QueryExecutionRecordRepository`への新規絞込クエリメソッドを、`Pageable`（Spring Data JPA標準、Q8=A）付きで呼び出す
   d. 取得した`QueryExecutionRecord`一覧のうち、`savedQueryId`が非nullのものをユニークに集約し、`SavedQueryRepository`から一括取得する（N+1回避）
   e. 取得した`executedBy`（ユーザID）をユニークに集約し、`UserRepository`から一括取得し、表示用の実行者名（`fullName`）に解決する
   f. 上記を結合し、`QueryHistoryRecordView`（表示用DTO、§5参照）のページ結果として返す

## 3-1. 接続一覧取得ロジック（新規、承認前レビューでの追加）

接続選択画面用の接続一覧は、`QueryExecutionRecordRepository`から取得した`connectionId`のDISTINCT一覧を基に構築する。実行者スコープの範囲はBR-QUERYHISTORY-03と同じロール判定に従う（一般ユーザは自分の実行履歴に含まれる接続のみ、管理者は全ユーザの実行履歴に含まれる接続）。取得した`connectionId`ごとに、UNIT-03の`RdbmsConnection`から表示名を解決する。対象の接続が既に削除されていた場合は「(削除済み接続)」等のプレースホルダーで表示する（§6参照）。

## 3. 絞込ロジック（FR-8.3、STORY-8.1）

すべての絞込条件はANDで組み合わされる（複数条件の同時指定が可能）。

- **対象接続**: 画面構成上、常に1つの`connectionId`に固定される（Q2=A）。選択肢は§3-1の履歴実績ベースの接続一覧から得られる
- **実行日時範囲**: `executedAt`が指定範囲内（開始のみ・終了のみの片側指定も許容）
- **実行者スコープ**（Q3=A）:
  - 一般ユーザ（`Role.USER`）は常に自分の実行履歴のみが対象となる。リクエストで「全ユーザ」を指定してもサーバ側で自分のみに強制する（フェイルクローズ、UNIT-04/05で確立した「疑わしい場合はより厳格な側」の方針を踏襲）
  - 管理者（`Role.ADMIN`）は「全ユーザ」または「自分のみ」を選択可能。「全ユーザ」を選んだ場合は`executedBy`条件を付けない。「自分のみ」を選んだ場合は`executedBy = 自分のuserId`で絞り込む
- **対象スキーマ**: `schemaName`の完全一致。絞込セレクタの選択肢は「現在アクセス可能なスキーマ」ではなく、対象接続の履歴に実際に記録されている`schemaName`のDISTINCT一覧とする（BR-QUERYHISTORY-10）。BR-QUERYHISTORY-04によりアクセス権を失ったスキーマの履歴も閲覧対象となるため、絞込の選択肢もそれに合わせる必要がある。**この一覧も実行者スコープでフィルタする**（承認前レビューでの追加）: 一般ユーザは自分の実行履歴に含まれるスキーマ名のみ、管理者は選択中の実行者スコープ（全ユーザ/自分のみ）に応じたスキーマ名のみを選択肢とする。フィルタしないと、一般ユーザが他ユーザの実行履歴に含まれるスキーマ名を、絞込セレクタ経由で知ることができてしまう（情報漏洩）
- **SQLテキスト**: `sql`列に対する部分一致検索（`LIKE '%keyword%'`、Q5=A）。大文字小文字の区別はDB方言のデフォルト照合順序に従う（明示的な大文字小文字無視処理は行わない）

## 4. ページング（Q8=A）

Spring Data JPAの`Pageable`/`Page`を新規に導入する（プロジェクト内初の採用。UNIT-06のクエリ実行結果ページング（サブクエリラップ方式）とは独立した仕組みであり、混同しない）。

- デフォルトソート順: `executedAt`降順（新しい実行が先頭）
- ページサイズはUNIT-06のクエリ実行結果一覧と同程度の既定値を踏襲する（NFR Requirements段階で確定）
- **フロントエンドのページ番号表現との対応**: design-system既存の`Pagination`コンポーネントは1-indexed（`page=1`が先頭ページ、`page<=1`で「前へ」を無効化）である一方、Spring Data JPAの`Pageable`は0-indexed（`PageRequest.of(0, ...)`が先頭ページ）である。バックエンドAPIのリクエスト/レスポンスは`Pageable`の0-indexedな値をそのまま使用し、フロントエンドの`QueryHistoryPage`側で`Pagination`コンポーネントに渡す直前に1-indexedへ変換する（表示用の`page + 1`、APIリクエスト時は`page - 1`）。UNIT-06の`QueryEditorPanel`では両基準が混在したまま実装されている前例があるため、本ユニットでは変換点をQueryHistoryPage側の1箇所に限定し曖昧にしない

## 5. 表示用データの結合

- `QueryHistoryRecordView`（DTO、永続化なし）: `QueryExecutionRecord`の全フィールド + `executorDisplayName`（`User.fullName`から解決、削除済みユーザの場合は後述§6） + `savedQueryName`（`savedQueryId`非nullの場合に`SavedQuery.name`から解決、対象が非表示化・削除済みの場合は「(削除済み)」等のプレースホルダー、Q6=A） + `queryType`（`savedQueryId`の有無から導出する種別、FR-8.2）

## 6. 参照整合性の扱い（既存記録の不変性、Q4=Aと同じ考え方の延長）

- `executedBy`が指すユーザが退会・削除等で存在しなくなっていた場合（現状のUser削除機能はUNIT-02スコープ外だが将来のための備え）、`executorDisplayName`解決に失敗するため「(不明なユーザ)」等のプレースホルダーで表示する
- `savedQueryId`が指す`SavedQuery`が非表示化（`retired=true`）されている場合、非表示化済みであってもレコード自体は存在するため名前解決は可能。名前に「(非表示)」等の補助表示を付けるかはCode Generation時にUIデザインとして決定する。物理削除された場合（`SavedQueryRepository`に削除APIは存在しないため現状発生しない想定だが、念のため）は「(削除済み)」のプレースホルダーとする
- `connectionId`が指す`RdbmsConnection`が削除されていた場合（UNIT-03の接続削除機能により実際に発生しうる）、接続選択画面（§3-1）では表示名を「(削除済み接続)」のプレースホルダーとする。削除済み接続であっても選択・履歴閲覧自体は可能とする（記録の不変性、BR-QUERYHISTORY-04と同じ考え方）

## 7. 履歴からの画面遷移（FR-8.4、STORY-8.2、Q7=A）

UNIT-06/07で確立済みのrouter state経由のパターンをそのまま踏襲する。履歴一覧の各行（または詳細）から、以下3つの遷移を提供する。

- **クエリ実行画面へ**: `navigate('/query-execution/{connectionId}', { state: { sql, schemaName } })`
- **保存クエリ新規作成画面へ**: `navigate('/saved-queries/{connectionId}/new', { state: { sql, schemaName } })`
- **クエリビルダー画面へ**: `navigate('/query-builder/{connectionId}', { state: { sql, schemaName } })`

いずれも遷移先ページは既にrouter stateの受信ロジックを実装済み（UNIT-06/07のCode Generationで対応）であるため、本ユニットからは送信側の実装のみを追加する。

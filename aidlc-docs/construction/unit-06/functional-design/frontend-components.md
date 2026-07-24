# UNIT-06 クエリ保存・実行 - Frontend Components

UNIT-02〜UNIT-05で確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき実装する。

**画面フローの訂正（レビュー指摘の反映）**: 当初は「保存クエリ一覧」「クエリ実行（ad-hoc/保存クエリ共用）」の2画面構成としていたが、レビューで以下2点の指摘を受け、Flow A（保存クエリ管理）とFlow B（ad-hocクエリ実行）を独立した画面フローに分離し、いずれもUNIT-05と同様に接続選択画面を起点とする構成に訂正した:
1. 保存クエリ一覧はグローバルな一覧ではなく、UNIT-05と同様に接続を選択したうえでその接続に紐づく保存クエリのみを表示する（保存クエリは接続に紐付くため、Q11）
2. ad-hoc実行はクエリ保存とは独立した画面フローとして設ける。ad-hoc実行画面から「名前を付けて保存」した場合のみ、保存クエリ作成画面へ遷移する

**ナビゲーション**:
- Flow A: UNIT-01で仮予約済みのナビ項目（`key: 'savedQueries'`, `labelKey: 'nav.savedQueries'`, `path: '/saved-queries'`）をそのまま使用する
- Flow B: UNIT-01時点では未予約のため、新規ナビ項目`key: 'queryExecution'`, `labelKey: 'nav.queryExecution'`, `path: '/query-execution'`を追加する（`savedQueries`の直前に配置。UNIT-02のダッシュボード統合と同様、UNIT-01時点の仮予約一覧を実装ユニット側で確定・追加する前例に倣う）

`queryBuilder`（UNIT-07）・`queryHistory`（UNIT-08）は別ユニットで対応するため本ユニットでは触れない。

**バックエンドAPIパス・パッケージ構成**: UNIT-05に続く一般ユーザ向け機能のため、新規のトップレベル名前空間`/api/queries/*`を新設する（管理者ロールを要求しない）。パッケージは`cherry.mastermeister.query`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。

---

## Flow A: 保存クエリ管理（ナビ項目`savedQueries`）

### A-1. 接続選択画面（`/saved-queries`、`AppShell`）

```
SavedQueryConnectionListPage (AppShell)
└ PageHeader（タイトル「保存クエリ」）
  ├ DataTable（列: 接続の表示名のみ。UNIT-05の接続選択画面と同じ判定・表示方針）
  │  └ 行クリックで保存クエリ一覧画面（A-2）へ遷移
  ├ EmptyState（アクセス可能な接続が0件の場合）
  └ Alert（取得失敗時）
```

**State**: `connections: AccessibleConnection[]`, `loading: boolean`, `errorMessage: string | null`

**API連携**: `GET /api/queries/connections` — アクセス可能な接続一覧（UNIT-05の`listAccessibleConnections`と同じ判定ロジック）

---

### A-2. 保存クエリ一覧画面（`/saved-queries/:connectionId`、`AppShell`）

```
SavedQueryListPage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「保存クエリ一覧」、右上に「追加」ボタン）
  ├ FilterBar（公開範囲: すべて/Public/Private、「自分の非表示化済みを含める」トグル。Q9=B）
  ├ DataTable（列: クエリ名, 公開範囲バッジ, 作成者（自分/他ユーザ）, アクション）
  │  ├ 行クリックまたは「実行」アクションで既存保存クエリ実行画面（A-4）へ遷移
  │  └ 「非表示化」アクション（作成者のみ表示、ConfirmDialog経由）
  ├ 「追加」ボタン → 新規保存クエリ画面（A-3）へ遷移
  ├ EmptyState（対象接続に表示対象の保存クエリが0件の場合）
  └ Alert（取得・非表示化失敗時）
```

**State**: `connectionId`（ルートパラメータ）、`queries: SavedQuerySummary[]`, `visibilityFilter: 'ALL' | 'PUBLIC' | 'PRIVATE'`, `includeOwnRetired: boolean`, `loading: boolean`, `errorMessage: string | null`

**API連携**:
- `GET /api/queries/saved?connectionId=...&visibility=...&includeOwnRetired=...` — 対象接続に紐づく可視な保存クエリ一覧取得（BR-QUERY-05, BR-QUERY-08）
- `POST /api/queries/saved/{id}/retire` — 非表示化（作成者のみ、BR-QUERY-07〜08）

---

### A-3. 新規保存クエリ画面（`/saved-queries/:connectionId/new`、`AppShell`）

Flow Bのad-hoc実行画面（B-2）から「名前を付けて保存」で遷移してきた場合、実行済みのSQL・スキーマ・パラメータ値を引き継いで初期表示する（router stateで受け渡し）。A-2の「追加」ボタンから遷移した場合は空のSQLから開始する。

```
SavedQueryEditorPage (AppShell, mode='new')
└ PageHeader（タイトル: 接続の表示名＋「新規保存クエリ」）
  ├ スキーマセレクタ（対象接続内でアクセス可能なスキーマの一覧。実行のたびに選択、BR-QUERY-02〜03）
  ├ SQL入力欄（編集可）
  ├ パラメータ入力フォーム（SQL入力欄の内容から動的に検出した`:param`ごとに入力欄を生成）
  ├ ページング設定（有効/無効トグル＋1ページあたり件数）
  ├ 「実行」ボタン（スキーマ未選択時は非活性。保存前の動作確認用）
  ├ 「保存」ボタン → ダイアログ（クエリ名・公開範囲を入力）→ 保存確定
  ├ DataTable（実行結果）／Pagination（ページング有効時）
  └ Alert（構文エラー・実行エラー時）
```

**State**: `connectionId`（ルートパラメータ）、`prefill: { sql, schemaName, paramValues } | null`（router stateから受領）、`schemas: string[]`, `selectedSchema: string | null`, `sql: string`, `detectedParams: string[]`, `paramValues: Record<string, string>`, `pagingEnabled: boolean`, `pageSize: number`, `result: QueryResult | null`, `loading: boolean`, `errorMessage: string | null`

**API連携**:
- `GET /api/queries/connections/{connectionId}/schemas` — アクセス可能なスキーマ一覧（BR-QUERY-02）
- `POST /api/queries/execute` — 保存前の実行確認（BR-QUERY-01, BR-QUERY-04）
- `POST /api/queries/saved` — 新規保存（`connectionId`, `name`, `sql`, `visibility`。BR-QUERY-05）

---

### A-4. 既存保存クエリ実行画面（`/saved-queries/:connectionId/:savedQueryId`、`AppShell`）

A-3と同じ内部コンポーネント構成（スキーマセレクタ・SQL入力欄・パラメータフォーム・ページング設定・結果表）を`mode='existing'`で再利用する。

```
SavedQueryEditorPage (AppShell, mode='existing')
└ PageHeader（タイトル: 保存クエリ名）
  ├ スキーマセレクタ（対象接続内でアクセス可能なスキーマの一覧。保存クエリはスキーマ非依存のため実行のたびに選択、BR-QUERY-02〜03）
  ├ SQL入力欄（既定は読み取り専用。FR-7.9。「編集」ボタン押下時のみ編集可）
  ├ パラメータ入力フォーム
  ├ ページング設定
  ├ 「実行」ボタン
  ├ 「編集」ボタン（作成者のみ表示。SQL入力欄を編集可能に切り替え、「更新」ボタンに置き換わる）
  ├ 「更新」ボタン（編集モード時、作成者のみ。名前・公開範囲も合わせて変更可能なダイアログを開く）
  ├ 「非表示化」ボタン（作成者のみ、ConfirmDialog経由）
  ├ DataTable（実行結果）／Pagination
  └ Alert（アクセス拒否・構文エラー・実行エラー時）
```

**State**: `connectionId`, `savedQueryId`（ルートパラメータ）、`savedQuery: SavedQuerySummary | null`（名前・公開範囲・作成者情報。編集権限の判定・更新ダイアログの初期値に使う）、`editing: boolean`、他はA-3と共通（スキーマ・SQL・パラメータ・ページング・結果）

**API連携**:
- `GET /api/queries/saved/{id}` — 保存クエリの取得（アクセス可否はBR-QUERY-09で判定、拒否時は403）
- `GET /api/queries/connections/{connectionId}/schemas`
- `POST /api/queries/saved/{id}/execute` — 実行（BR-QUERY-09）
- `PUT /api/queries/saved/{id}` — 更新（作成者のみ、BR-QUERY-07）
- `POST /api/queries/saved/{id}/retire` — 非表示化（作成者のみ、BR-QUERY-08）

---

## Flow B: ad-hocクエリ実行（ナビ項目`queryExecution`、新規追加）

### B-1. 接続選択画面（`/query-execution`、`AppShell`）

A-1と同一のAPI・判定ロジックを用いる独立画面（ナビ項目が異なるため別ページとして実装するが、内部的に接続一覧を表示する部分は共通コンポーネント化できる）。

```
QueryExecutionConnectionListPage (AppShell)
└ PageHeader（タイトル「クエリ実行」）
  ├ DataTable（列: 接続の表示名のみ）
  │  └ 行クリックでad-hoc実行画面（B-2）へ遷移
  ├ EmptyState（アクセス可能な接続が0件の場合）
  └ Alert（取得失敗時）
```

**State/API連携**: A-1と同じ（`GET /api/queries/connections`）

---

### B-2. ad-hoc実行画面（`/query-execution/:connectionId`、`AppShell`）

```
QueryExecutionPage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「クエリ実行」）
  ├ スキーマセレクタ（対象接続内でアクセス可能なスキーマの一覧）
  ├ SQL入力欄（編集可）
  ├ パラメータ入力フォーム
  ├ ページング設定
  ├ 「実行」ボタン
  ├ 「名前を付けて保存」ボタン → 新規保存クエリ画面（A-3、`/saved-queries/:connectionId/new`）へ、現在のSQL・スキーマ・パラメータ値をrouter state経由で引き継いで遷移（保存自体はA-3側で行う。B-2自体には保存操作を持たない）
  ├ DataTable（実行結果）／Pagination
  └ Alert（構文エラー・実行エラー時）
```

**State**: `connectionId`（ルートパラメータ）、`schemas: string[]`, `selectedSchema: string | null`, `sql: string`, `detectedParams: string[]`, `paramValues: Record<string, string>`, `pagingEnabled: boolean`, `pageSize: number`, `result: QueryResult | null`, `loading: boolean`, `errorMessage: string | null`

**API連携**:
- `GET /api/queries/connections/{connectionId}/schemas`
- `POST /api/queries/execute` — ad-hoc実行（BR-QUERY-01, BR-QUERY-04）

---

## 新規コンポーネントの要否

UNIT-01〜05で構築済みの共通コンポーネント（`DataTable`, `Pagination`, `FilterBar`, `PageHeader`, `EmptyState`, `ErrorAlert`/`Alert`, `ConfirmDialog`）を再利用し、本ユニット固有の新規共通コンポーネントは不要と見込む。SQL入力欄（テキストエリア＋パラメータ自動検出）は、A-3/A-4/B-2で共通利用するローカルコンポーネントとして実装する（UNIT-07のクエリビルダーとは異なる、単純なテキスト入力）。接続選択画面（A-1/B-1）も、内部のDataTable表示部分は共通ローカルコンポーネントとして抽出し、ページ側（ナビ項目・遷移先）だけを差し替える実装とする。

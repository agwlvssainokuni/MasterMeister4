# UNIT-06 クエリ保存・実行 - Frontend Components

UNIT-02〜UNIT-05で確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき実装する。

**ナビゲーション**: UNIT-01で仮予約済みのナビ項目（`key: 'savedQueries'`, `labelKey: 'nav.savedQueries'`, `path: '/saved-queries'`）を「保存クエリ一覧」画面のルートとして使用する。UNIT-05（`masterData`ナビ項目が接続選択→テーブル一覧→レコード一覧の3画面に展開）と同様、1つのナビ項目の配下に複数画面（一覧画面＋実行画面）を展開する。`queryBuilder`（UNIT-07）・`queryHistory`（UNIT-08）は別ユニットで対応するため本ユニットでは触れない。

**バックエンドAPIパス・パッケージ構成**: UNIT-05に続く一般ユーザ向け機能のため、新規のトップレベル名前空間`/api/queries/*`を新設する（管理者ロールを要求しない）。パッケージは`cherry.mastermeister.query`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。

---

## 1. 保存クエリ一覧画面（`/saved-queries`、`AppShell`）

### コンポーネント構造
```
SavedQueryListPage (AppShell)
└ PageHeader（タイトル「保存クエリ」、右上に「新規クエリ実行」ボタン）
  ├ FilterBar（公開範囲: すべて/Public/Private、「自分の非表示化済みを含める」トグル。Q9=B）
  ├ DataTable（列: クエリ名, 接続の表示名, 公開範囲バッジ, 作成者（自分/他ユーザ）, アクション）
  │  ├ 行クリックまたは「実行」アクションで実行画面（該当savedQueryId）へ遷移
  │  ├ 「編集」アクション（作成者のみ表示）で実行画面（編集モード）へ遷移
  │  └ 「非表示化」アクション（作成者のみ表示、ConfirmDialog経由）
  ├ EmptyState（表示対象の保存クエリが0件の場合）
  └ Alert（取得・非表示化失敗時）
```

### State
- `queries: SavedQuerySummary[]`, `visibilityFilter: 'ALL' | 'PUBLIC' | 'PRIVATE'`, `includeOwnRetired: boolean`, `loading: boolean`, `errorMessage: string | null`

### API連携
- `GET /api/queries/saved?visibility=...&includeOwnRetired=...` — 可視な保存クエリ一覧取得（BR-QUERY-05, BR-QUERY-08）
- `POST /api/queries/saved/{id}/retire` — 非表示化（作成者のみ、BR-QUERY-07〜08）

---

## 2. クエリ実行画面（`/saved-queries/execute`＝ad-hoc、`/saved-queries/execute/:savedQueryId`＝保存クエリ、`AppShell`）

同一コンポーネントが2つのモードを扱う（Q7=A）。

### コンポーネント構造
```
QueryExecutionPage (AppShell)
└ PageHeader（タイトル: ad-hocは「クエリ実行」、保存クエリは保存クエリ名）
  ├ [ad-hocモードのみ] 接続セレクタ（アクセス可能な接続の一覧から選択、Q11）
  ├ [保存クエリモードのみ] 接続名の読み取り専用表示（保存時に固定された接続、Q11）
  ├ スキーマセレクタ（選択済み/固定済みの接続に対して、実行者自身がアクセス可能なスキーマの一覧。接続未選択のad-hocモードでは非活性）
  ├ SQL入力欄（ad-hocモードまたは編集モード時は編集可、保存クエリの通常実行時は読み取り専用。FR-7.9）
  ├ パラメータ入力フォーム（SQL入力欄の内容から動的に検出した`:param`ごとに入力欄を生成）
  ├ ページング設定（有効/無効トグル＋1ページあたり件数）
  ├ アクションボタン群:
  │  ├ 「実行」（スキーマ未選択時は非活性）
  │  ├ 「名前を付けて保存」（ad-hocモードのみ。ダイアログでクエリ名・公開範囲を入力）
  │  ├ 「編集」（保存クエリモード、作成者のみ表示。SQL入力欄を編集可能に切り替える）
  │  ├ 「更新」（編集モード時、作成者のみ）
  │  └ 「非表示化」（保存クエリモード、作成者のみ、ConfirmDialog経由）
  ├ DataTable（実行結果。列は動的、cellStates等は使用しない読み取り専用表示）
  ├ Pagination（ページング有効時のみ）
  └ Alert（構文エラー・実行エラー時）
```

### State
- `mode: 'ad-hoc' | 'saved'`、`savedQueryId`（ルートパラメータ、savedモードのみ）
- `connections: AccessibleConnection[]`（ad-hocモードのみ取得）, `selectedConnectionId: number | null`
- `schemas: string[]`, `selectedSchema: string | null`
- `sql: string`, `editable: boolean`（savedモードは初期`false`、「編集」操作で`true`に切替）
- `detectedParams: string[]`, `paramValues: Record<string, string>`
- `pagingEnabled: boolean`, `pageSize: number`
- `result: QueryResult | null`, `loading: boolean`, `errorMessage: string | null`
- `savedQuery: SavedQuerySummary | null`（savedモードで取得した保存クエリ本体。名前・公開範囲・作成者情報を保持し、編集権限の判定・保存ダイアログの初期値に使う）

### API連携
- `GET /api/queries/connections` — アクセス可能な接続一覧（ad-hocモードの接続セレクタ用）
- `GET /api/queries/connections/{connectionId}/schemas` — 指定接続内でアクセス可能なスキーマ一覧（両モード共通、BR-QUERY-02）
- `GET /api/queries/saved/{id}` — 保存クエリの取得（savedモード初期表示、アクセス可否はBR-QUERY-09で判定、拒否時は403）
- `POST /api/queries/execute` — ad-hoc実行（BR-QUERY-01, BR-QUERY-04）
- `POST /api/queries/saved/{id}/execute` — 保存クエリ実行（BR-QUERY-09）
- `POST /api/queries/saved` — 新規保存（BR-QUERY-05）
- `PUT /api/queries/saved/{id}` — 更新（作成者のみ、BR-QUERY-07）

---

## 3. 新規コンポーネントの要否

UNIT-01〜05で構築済みの共通コンポーネント（`DataTable`, `Pagination`, `FilterBar`, `PageHeader`, `EmptyState`, `ErrorAlert`/`Alert`, `ConfirmDialog`）を再利用し、本ユニット固有の新規共通コンポーネントは不要と見込む。SQL入力欄（テキストエリア＋パラメータ自動検出）は本画面固有のローカルコンポーネントとして実装する（UNIT-07のクエリビルダーとは異なる、単純なテキスト入力）。

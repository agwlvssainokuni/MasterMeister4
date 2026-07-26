# UNIT-08 クエリ履歴 - Frontend Components

UNIT-02〜UNIT-07で確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき実装する。

**ナビゲーション**: UNIT-01で仮予約済みのナビ項目（`key: 'queryHistory'`, `labelKey: 'nav.queryHistory'`, `path: '/query-history'`）をそのまま使用する（`navigation.ts`に追加作業は不要）。

**バックエンドAPIパス・パッケージ構成**: パッケージは`cherry.mastermeister.queryhistory`（unit-of-work.mdのユニット→パッケージ対応表のとおり）。

**接続一覧はUNIT-06の既存APIを再利用しない**（承認前レビューでの是正、BR-QUERYHISTORY-11）: 現在アクセス可能な接続一覧ではなく、履歴に実際に記録されている接続の一覧を新規APIで取得する。

**APIパス規約（UNIT-05/06/07の確立済み規約を踏襲）**: 新規のトップレベル名前空間`/api/query-history/*`を新設する（管理者ロールを要求しない。実行者スコープの絞込はエンドポイント内部でロール判定する、BR-QUERYHISTORY-03）。

- `GET /api/query-history/connections?executedByScope=ALL|MINE`（`executedByScope`省略可、デフォルト`ALL`。一般ユーザは常に`MINE`へ強制、BR-QUERYHISTORY-03） — 履歴に実際に記録されている接続の一覧（DISTINCT取得、BR-QUERYHISTORY-11）
- `GET /api/query-history/{connectionId}?executedByScope=ALL|MINE&executedAtFrom=...&executedAtTo=...&schemaName=...&sqlKeyword=...&page=0&pageSize=...` — 履歴一覧取得（絞込・ページング、FR-8.1〜8.3）
- `GET /api/query-history/{connectionId}/schemas?executedByScope=ALL|MINE`（同上、省略可・デフォルト`ALL`・一般ユーザは`MINE`強制） — 対象接続の履歴に実際に記録されているスキーマ名の一覧（DISTINCT取得、スキーマ絞込セレクタ用、BR-QUERYHISTORY-10）。**`executedByScope`でも実行者スコープに応じてフィルタする**（承認前レビューでの追加）: 一般ユーザは自分の実行履歴のスキーマ名のみ、他ユーザの実行履歴に含まれるスキーマ名を絞込セレクタ経由で知ることはできない。フロントエンドは履歴一覧画面の実行者スコープSelectが変更されるたびに、このAPIを現在の`executedByScope`で再取得する

---

## 画面構成

### 1. 接続選択画面（`/query-history`、`AppShell`）

UNIT-05/06/07と同様の画面構造パターンを踏襲するが、一覧の取得元はUNIT-06既存APIではなく新規APIとする（BR-QUERYHISTORY-11）。

```
QueryHistoryConnectionListPage (AppShell)
└ PageHeader（タイトル「クエリ履歴」）
  ├ DataTable（列: 接続の表示名。削除済み接続は「(削除済み接続)」のプレースホルダー表示）
  │  └ 行クリックで履歴一覧画面（2）へ遷移
  ├ EmptyState（履歴に記録された接続が0件の場合）
  └ Alert（取得失敗時）
```

**State**: `connections: QueryHistoryConnectionView[]`, `loading: boolean`, `errorMessage: string | null`

**API連携**: `GET /api/query-history/connections`（新規。履歴実績ベースの接続一覧、BR-QUERYHISTORY-11。`executedByScope`は指定せずデフォルト`ALL`のまま呼び出す。実際に返る範囲はサーバ側でロールに応じ自動決定される：一般ユーザは自分の履歴、管理者は全ユーザの履歴に含まれる接続）

---

### 2. クエリ履歴一覧画面（`/query-history/:connectionId`、`AppShell`）

```
QueryHistoryPage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「クエリ履歴」）
  ├ FilterBar（design-system既存コンポーネント）
  │  ├ 検索欄（searchValue/onSearchChange。SQLテキストキーワード、BR-QUERYHISTORY-05）
  │  └ children（追加フィルタ）
  │     ├ 実行日時範囲（開始・終了の日時入力2件、片側指定可）
  │     ├ 対象スキーマセレクタ（選択肢は「現在アクセス可能なスキーマ」ではなく、対象接続の履歴に
  │     │  実際に記録されているスキーマ名の一覧（DISTINCT取得、BR-QUERYHISTORY-10）。BR-04により
  │     │  現在アクセス権のないスキーマの履歴も閲覧可能なため、絞込の選択肢もそれに合わせる、任意。
  │     │  実行者スコープSelectの選択に応じて選択肢を再取得する（他ユーザのスキーマ名を漏らさない））
  │     └ 実行者スコープSelect（管理者のみ表示。「全ユーザ」「自分のみ」の2択。
  │        一般ユーザには表示せず常に自分のみで固定、BR-QUERYHISTORY-03）
  ├ DataTable（design-system既存コンポーネント。列: 実行日時、種別（保存/直接入力、FR-8.2）、
  │  保存クエリ名またはSQL冒頭、実行者（管理者が「全ユーザ」表示時のみ列を表示）、対象スキーマ、
  │  結果件数、実行時間）
  │  └ 行クリックで詳細（SQL全文と3つの遷移ボタンを表示するモーダルまたは展開行）を表示
  ├ Pagination（design-system既存コンポーネント。page/totalPages/onChange）
  └ Alert（取得失敗時）
```

**行詳細・画面遷移（FR-8.4、STORY-8.2、BR-QUERYHISTORY-07）**: 行クリックで開く詳細（Modal）に、SQL全文（読み取り専用コードブロック）と以下3つの遷移ボタンを配置する。

- 「実行へ」ボタン → `navigate('/query-execution/{connectionId}', { state: { sql, schemaName } })`
- 「保存へ」ボタン → `navigate('/saved-queries/{connectionId}/new', { state: { sql, schemaName } })`
- 「ビルダーで開く」ボタン → `navigate('/query-builder/{connectionId}', { state: { sql, schemaName } })`

**State**: `connectionId`（ルートパラメータ）、`records: QueryHistoryRecordView[]`、`page: number`、`totalPages: number`、絞込条件一式（`sqlKeyword`, `executedAtFrom`, `executedAtTo`, `schemaName`, `executedByScope`）、`selectedRecord: QueryHistoryRecordView | null`（詳細モーダル用）、`loading: boolean`、`errorMessage: string | null`

**API連携**:
- `GET /api/query-history/{connectionId}/schemas`（新規。対象接続の履歴に実際に記録されているスキーマ名の一覧をDISTINCT取得。UNIT-06の`GET /api/queries/{connectionId}/schemas`＝現在アクセス可能なスキーマ一覧とは意味が異なるため別エンドポイントとする、BR-QUERYHISTORY-10）
- `GET /api/query-history/{connectionId}`（絞込・ページング一覧取得）

**管理者判定**: 現在ログイン中ユーザのロールは、UNIT-02で確立済みの認証コンテキスト（JWTクレームまたは`AuthContext`）から取得する。実行者スコープSelectの表示・非表示はこの情報に基づきフロントエンド側でも制御するが、実際のアクセス制御はサーバ側で行う（BR-QUERYHISTORY-03、フロントエンド側の表示制御はUX向上のためであり権限境界としては機能しない）。

---

## i18n・共通コンポーネント方針

- タイトル・ラベル等は`common.json`（ja/en）に`queryHistory.*`名前空間で追加する
- 既存の`DataTable`・`Pagination`・`FilterBar`・`Modal`・`CodeBlock`（design-system）をそのまま利用し、新規UIコンポーネントの追加は最小限に留める

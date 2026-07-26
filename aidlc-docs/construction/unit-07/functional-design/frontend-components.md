# UNIT-07 クエリビルダー - Frontend Components

UNIT-02〜UNIT-06で確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき実装する。

**ナビゲーション**: UNIT-01で仮予約済みのナビ項目（`key: 'queryBuilder'`, `labelKey: 'nav.queryBuilder'`, `path: '/query-builder'`）をそのまま使用する（`navigation.ts`に追加作業は不要）。

**バックエンドAPIパス・パッケージ構成**: パッケージは`cherry.mastermeister.querybuilder`（unit-of-work.mdのユニット→パッケージ対応表のとおり）。新規のトップレベル名前空間`/api/query-builder/*`を新設する（管理者ロールを要求しない）。

**接続一覧・スキーマ一覧はUNIT-06の既存APIをそのまま再利用する（重複実装しない）**:
- `GET /api/queries/connections`（UNIT-06既存） — アクセス可能な接続一覧
- `GET /api/queries/{connectionId}/schemas`（UNIT-06既存） — 対象接続内でアクセス可能なスキーマ一覧

**APIパス規約（UNIT-05/06の確立済み規約を踏襲）**: 上記2エンドポイント以外は、すべて`/api/query-builder/{connectionId}/...`にネストする。`generate`（SQL生成）はテーブル/カラムの実在確認等のDBアクセスを伴わない純粋な変換だが、パス規約の一貫性を優先し`{connectionId}`配下に統一する。

- `GET /api/query-builder/{connectionId}/tables?schemaName=...` — アクセス可能テーブル/カラム一覧（BR-QUERYBUILDER-01）
- `POST /api/query-builder/{connectionId}/generate` — `QueryBuilderState` → SQL生成（FR-5.5、PBT対象）
- `POST /api/query-builder/{connectionId}/parse` — SQL → `QueryBuilderState`（リバースエンジニアリング、FR-5.7）。失敗時は専用エラーレスポンス（BR-QUERYBUILDER-07）

---

## 画面構成

### 1. 接続選択画面（`/query-builder`、`AppShell`）

UNIT-05/06と同様の接続選択画面パターンを踏襲する。

```
QueryBuilderConnectionListPage (AppShell)
└ PageHeader（タイトル「クエリビルダー」）
  ├ DataTable（列: 接続の表示名のみ）
  │  └ 行クリックでクエリビルダー画面（2）へ遷移
  ├ EmptyState（アクセス可能な接続が0件の場合）
  └ Alert（取得失敗時）
```

**State**: `connections: AccessibleConnection[]`, `loading: boolean`, `errorMessage: string | null`

**API連携**: `GET /api/queries/connections`（UNIT-06既存を再利用）

---

### 2. クエリビルダー画面（`/query-builder/:connectionId`、`AppShell`）

他画面（クエリ実行画面・保存クエリ編集画面）からの逆遷移・相互遷移時は、遷移元のSQL文字列をrouter state経由で受け取り、画面表示時に自動的にリバースエンジニアリング（`parse`）を試行してタブへ反映する（BR-QUERYBUILDER-12）。失敗時はタブを初期状態（空のSELECT/FROM等）のまま表示し、Alertでエラーメッセージを表示する。

```
QueryBuilderPage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「クエリビルダー」）
  ├ スキーマセレクタ（対象接続内でアクセス可能なスキーマの一覧。選択変更のたびにテーブル/カラム候補を再取得）
  ├ Tabs（design-systemの既存Tabsコンポーネントを使用）
  │  ├ SELECTタブ（SelectItemBuilder: 列参照または集計関数適用の追加・削除、AS別名入力）
  │  ├ FROMタブ（FromTableBuilder: 起点テーブル1件の選択、エイリアス入力（任意））
  │  ├ JOINタブ（JoinBuilder: JOIN一覧の追加・削除、種別（INNER/LEFT/RIGHT）・結合先テーブル・
  │  │  結合条件（等価結合、複数可）の指定）
  │  ├ WHEREタブ（ConditionListBuilder: 条件の追加・削除、列参照・演算子（列のデータ型分類に応じて
  │  │  選択肢を絞り込み）・比較値の指定。WHERE/HAVINGで共通コンポーネントとして再利用）
  │  ├ GROUP BYタブ（ColumnListBuilder: 列参照の追加・削除）
  │  ├ HAVINGタブ（ConditionListBuilder再利用。列参照に加え集計関数適用も選択可能）
  │  ├ ORDER BYタブ（OrderByListBuilder: 列参照または集計関数適用の追加・削除、ASC/DESC選択）
  │  └ LIMIT OFFSETタブ（数値入力2件）
  ├ SQLプレビュー（読み取り専用コードブロック。タブの内容が変わるたびにデバウンスして`generate`
  │  APIを呼び出し、生成結果を表示。GROUP BY整合性違反等の検証エラーはこの領域にAlertで表示）
  ├ 「保存へ」ボタン（生成成功時のみ活性）→ 保存クエリ新規作成画面（UNIT-06 Flow A-3）へ、
  │  生成SQL・対象接続・対象スキーマをrouter state経由で引き継いで遷移
  ├ 「実行へ」ボタン（生成成功時のみ活性）→ クエリ実行画面（UNIT-06 Flow B-2）へ、同様に引き継いで遷移
  └ Alert（テーブル/カラム候補取得失敗時、リバースエンジニアリング失敗時）
```

**State**: `connectionId`（ルートパラメータ）、`schemaName: string`、`accessibleTables: AccessibleBuilderTable[]`、`builderState: QueryBuilderState`、`generatedSql: string | null`、`generating: boolean`、`errorMessage: string | null`

**API連携**:
- `GET /api/queries/{connectionId}/schemas`（UNIT-06既存を再利用）
- `GET /api/query-builder/{connectionId}/tables?schemaName=...`
- `POST /api/query-builder/{connectionId}/generate`
- `POST /api/query-builder/{connectionId}/parse`（逆遷移時の初期反映のみで使用）

---

## 逆遷移・相互遷移の実装方針（BR-QUERYBUILDER-12）

1. **クエリ実行画面（UNIT-06 `QueryExecutionPage`）からの逆遷移**: 「クエリビルダーで編集」ボタンを追加する（`QueryEditorPanel`共通コンポーネントの外側、`QueryExecutionPage`固有の追加ボタンとして配置）。現在のSQL・対象接続・対象スキーマをrouter state経由で`QueryBuilderPage`へ引き継ぐ
2. **新規保存クエリ画面（UNIT-06 `SavedQueryEditorPage`、`mode='new'`）からの逆遷移**: 同様に「クエリビルダーで編集」ボタンを追加する
3. **保存クエリ編集画面（UNIT-06 `SavedQueryEditorPage`、`mode='edit'`）との相互遷移**: 同様に「クエリビルダーで編集」ボタンを追加し、`QueryBuilderPage`側の「保存へ」ボタンは、編集モードからの遷移であった場合は新規作成画面ではなく元の編集画面へ戻り、SQLのみを更新した状態で表示する（router stateに`editMode`・`savedQueryId`を含める）
4. 上記3画面の変更はUNIT-06の既存コンポーネントへの追加修正となるため、Code Generation時にUNIT-06の該当ファイル（`QueryExecutionPage.tsx`・`SavedQueryEditorPage.tsx`）を本ユニットの一部として更新する

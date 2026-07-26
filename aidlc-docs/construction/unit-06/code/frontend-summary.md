# UNIT-06 クエリ保存・実行 - Frontend Summary

## ナビゲーション

- `NAV_ROUTES`に新規ナビ項目`queryExecution`（`/query-execution`）を`savedQueries`の直前に追加
- `HomePage.tsx`の`IMPLEMENTED_KEYS`に`savedQueries`・`queryExecution`を追加（未実装バッジ数 4→3）

## APIクライアント

- `frontend/src/api/query.ts`: 接続一覧・スキーマ一覧・ad-hoc実行・保存クエリCRUD・保存クエリ実行・非表示化の各関数

## 画面

### Flow A（保存クエリ管理、ナビ項目`savedQueries`）
- `SavedQueryConnectionListPage`（A-1、`/saved-queries`）: 接続選択
- `SavedQueryListPage`（A-2、`/saved-queries/:connectionId`）: 公開範囲フィルタ・自分の非表示化済み含めるトグル・一覧・非表示化
- `SavedQueryEditorPage`（A-3/A-4共用、`mode='new'|'existing'`、`/saved-queries/:connectionId/new`・`/saved-queries/:connectionId/:savedQueryId`）: 新規保存（router stateからのprefill対応）／既存クエリ実行（SQL既定読み取り専用、作成者のみ編集・更新・非表示化）

### Flow B（ad-hocクエリ実行、新規ナビ項目`queryExecution`）
- `QueryExecutionConnectionListPage`（B-1、`/query-execution`）: 接続選択
- `QueryExecutionPage`（B-2、`/query-execution/:connectionId`）: ad-hoc実行、「名前を付けて保存」でA-3へrouter state経由の遷移

### 共有コンポーネント
- `QueryEditorPanel`（`frontend/src/pages/QueryEditorPanel.tsx`）: スキーマセレクタ・SQL入力欄（`:param`検出はクライアント側の簡易正規表現によるUIヒント。実際の安全性検証はバックエンドのAST走査が担う）・パラメータフォーム・ページング設定・実行・結果表を1コンポーネントに集約し、A-3/A-4/B-2で共通利用する（frontend-components.mdの方針どおり）

## i18n

- `design-system.json`（ja/en）: `nav.queryExecution`
- `common.json`（ja/en）: `savedQuery.*`・`queryExecution.*`・`home.card.queryExecution`

## テスト結果

- `SavedQueryConnectionListPage.test.tsx`（3件）・`QueryExecutionConnectionListPage.test.tsx`（3件）
- `SavedQueryListPage.test.tsx`（6件）
- `SavedQueryEditorPage.test.tsx`（7件、new/existing両モード）
- `QueryExecutionPage.test.tsx`（4件）
- `query.test.ts`（9件）
- `HomePage.test.tsx`更新（既存バッジ数変更確認＋新規カード遷移テスト追加）
- `npx tsc -b`（型チェック）・`npm run lint`（oxlint、新規warning・error なし）・`npm run build`（成功）
- `npm test`: 全52ファイル・203件成功（既存分含め回帰なし）

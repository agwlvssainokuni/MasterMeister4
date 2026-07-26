# UNIT-07 クエリビルダー - Frontend Summary

## 作成した画面・コンポーネント

- **`api/queryBuilder.ts`**: アクセス可能テーブル/カラム一覧取得・SQL生成・リバースエンジニアリングの各APIクライアント関数、バックエンドDTOに対応する型定義一式
- **`QueryBuilderConnectionListPage`**: 接続選択画面（frontend-components.md 画面1、UNIT-05/06と同じパターン）
- **`QueryBuilderPage`**: クエリビルダー本体画面（画面2）。スキーマセレクタ、design-system既存の`Tabs`コンポーネントで8タブを構成、デバウンス付きSQLプレビュー（`CodeBlock`）、「保存へ」「実行へ」ボタン、router state経由の逆遷移時リバースエンジニアリング対応
- **タブサブコンポーネント7種**: `QueryBuilderSelectTab`, `QueryBuilderFromTab`, `QueryBuilderJoinTab`, `QueryBuilderConditionList`（WHERE/HAVING共通）, `QueryBuilderColumnListTab`（GROUP BY）, `QueryBuilderOrderByTab`, `QueryBuilderLimitOffsetTab`
- **`QueryBuilderOperandPicker`**（実装時に追加）: SELECT/HAVING/ORDER BYタブで共通利用する、列参照または集計関数適用のいずれかを選択する共有部品。3タブでの重複実装を避けるため抽出

## 逆遷移・相互遷移の実装（BR-QUERYBUILDER-12）

- `QueryExecutionPage.tsx`・`SavedQueryEditorPage.tsx`（new/editモード双方）に「クエリビルダーで編集」ボタンを追加し、現在のSQL・スキーマをrouter state経由でクエリビルダー画面へ引き継ぐ
- `SavedQueryEditorPage.tsx`は、クエリビルダー画面から「保存へ」で戻ってきた場合に備え、`location.key`を監視するeffectを追加した。同一パスへの再ナビゲーションでもReact Routerが同一コンポーネントインスタンスを維持するため、`useState`の初期値だけではrouter stateの変更を検知できない（実装時の発見）。`location.key`はナビゲーションのたびに変化するため、これを依存配列にすることで確実に反応する

## ルーティング・ナビゲーション

- `App.tsx`に`/query-builder`・`/query-builder/:connectionId`を追加
- `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'queryBuilder'`を追加
- `queryBuilder`ナビ項目自体はUNIT-01で仮予約済みのため`navigation.ts`への変更は不要

## i18n

- `common.json`（`ja`/`en`）に`queryBuilder.*`名前空間を追加（タブ名・ボタンラベル・演算子表示名等）
- **実装時の発見**: `action.remove`キーが存在しなかった（既存は`action.delete`のみ）ため追加。リスト項目の削除操作は永続データの破壊的削除ではないため、意味的に区別する

## テスト結果

- `queryBuilder.test.ts`: 3件（APIクライアント）
- `QueryBuilderConnectionListPage.test.tsx`: 3件
- `QueryBuilderPage.test.tsx`: 6件（テーブル/カラム一覧読み込み、SQL生成プレビュー、保存/実行への遷移、リバースエンジニアリングの成功・失敗）
- `QueryExecutionPage.test.tsx`: 既存5件に「クエリビルダーで編集」ボタンのテスト1件を追加
- `SavedQueryEditorPage.test.tsx`: 既存7件に「クエリビルダーで編集」ボタンのテスト2件（new/editモード双方）を追加
- `HomePage.test.tsx`: 実装済みバッジ数の変化（3→2）とqueryBuilderカードのテストを反映

全件成功（`npm test -- --run`、既存ユニット分含め全55ファイル219件成功）

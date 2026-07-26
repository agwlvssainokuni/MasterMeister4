# UNIT-07 クエリビルダー - Frontend Summary

## 作成した画面・コンポーネント

- **`api/queryBuilder.ts`**: アクセス可能テーブル/カラム一覧取得・SQL生成・リバースエンジニアリングの各APIクライアント関数、バックエンドDTOに対応する型定義一式
- **`QueryBuilderConnectionListPage`**: 接続選択画面（frontend-components.md 画面1、UNIT-05/06と同じパターン）
- **`QueryBuilderPage`**: クエリビルダー本体画面（画面2）。スキーマセレクタ、design-system既存の`Tabs`コンポーネントで7タブを構成（承認前レビュー対応、下記参照）、デバウンス付きSQLプレビュー（`CodeBlock`）、「保存へ」「実行へ」ボタン、router state経由の逆遷移時リバースエンジニアリング対応
- **タブサブコンポーネント6種**: `QueryBuilderFromTab`（起点テーブル選択＋JOIN一覧、承認前レビュー対応6で1コンポーネントに統合、下記参照）, `QueryBuilderSelectTab`, `QueryBuilderConditionList`（WHERE/HAVING共通）, `QueryBuilderColumnListTab`（GROUP BY）, `QueryBuilderOrderByTab`, `QueryBuilderLimitOffsetTab`

## 承認前レビュー対応1（タブ構成の見直し）

Code Generation完了報告後、ユーザーから「FROM/JOINタブは統合、タブ順序をFROM/SELECT/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSETに」という指摘を受け、以下を修正した:

- `QueryBuilderPage.tsx`のtabs配列: 独立していた`select`/`from`/`join`の3タブを、`from`キー1つ（`QueryBuilderFromTab`と`QueryBuilderJoinTab`を同一content内に並べる）に統合し、配列順序を`from, select, where, groupBy, having, orderBy, limitOffset`に変更
- デフォルトの`activeTab`初期値を`'select'`から`'from'`に変更（クエリ組み立ての最初のステップとしてFROMタブを起点にするため）
- `common.json`（ja/en）の`queryBuilder.tab.join`キーを削除（タブラベルとして不要になったため。`queryBuilder.join.*`＝JOIN機能自体のラベルは引き続き使用するため残置）
- `frontend-components.md`のタブ構成記述を同内容に修正済み
- `QueryBuilderPage.test.tsx`は元々FROMタブのみを明示的にクリックする実装だったため、テストコード自体の修正は不要だった（全6件そのまま成功）
- 修正後、`npx tsc --noEmit`・`npm run lint`・`npm test -- --run`（全55ファイル219件）・`npm run build`をすべて実行し成功を確認

## 承認前レビュー対応2（FROM/JOINタブのレイアウト見直し）

続けて「FROMタブのテーブル名とエイリアス名、JOINのテーブル名とエイリアス名と削除ボタン、結合条件の左辺と比較演算子と右辺と削除ボタん、を一行に納めてほしい」という指摘を受けた。design-systemの`FilterBar.module.css`と同じ横並びレイアウトパターン（`display:flex; align-items; gap; flex-wrap:wrap`）を踏襲し、以下を追加した:

- `QueryBuilderFromTab.module.css`（新規）: `.row`でテーブル選択とエイリアス入力の2つの`FormField`を横並びに
- `QueryBuilderJoinTab.module.css`（新規）: `.joinRow`でJOIN種別・結合先テーブル・エイリアス・削除ボタンを横並びに、`.conditionRow`で結合条件の左辺・`=`・右辺・削除ボタンを横並びに
- いずれもコンポーネント構造（`Select`/`TextInput`が直接`<select>`/`<input>`をレンダリングする実装）は変更せず、ラップするdivにCSS Moduleクラスを付与するのみ
- 修正後、`npx tsc --noEmit`・`npm run lint`・`npm test -- --run`（全55ファイル219件）・`npm run build`をすべて実行し成功を確認
- **`QueryBuilderOperandPicker`**（実装時に追加）: SELECT/HAVING/ORDER BYタブで共通利用する、列参照または集計関数適用のいずれかを選択する共有部品。3タブでの重複実装を避けるため抽出

## 承認前レビュー対応3（SELECT/WHERE/HAVING/ORDER BYタブのレイアウト見直し）

続けて「SELECTタブも1行に1カラムずつ、WHEREタブも1行に1条件ずつ、HAVINGタブも1行に1条件ずつ、ORDER BYタブも1行に1つずつ」という指摘を受けた。この4タブはいずれも共通部品`QueryBuilderOperandPicker`（列参照/集計関数選択）を利用しており、行内の要素構成が同型（オペランド＋α＋削除ボタン）だったため、共通CSS Moduleとして整理した:

- `QueryBuilderOperandPicker.module.css`（新規）: `QueryBuilderOperandPicker`自体が返す`<span>`を`.picker`（`display:inline-flex`）にし、内部のモード切替・集計関数・列選択の各`Select`を横並びに
- `QueryBuilderItemRow.module.css`（新規）: SELECT/WHERE・HAVING/ORDER BYの4箇所で共通利用する行コンテナ`.row`（`display:flex`）。`QueryBuilderOperandPicker`が返す`span`は`.row > span`として広め（`flex:2`）に、`Select`/`TextInput`は`.row > select`/`.row > input`として`flex:1`に配分
- `QueryBuilderSelectTab.tsx`・`QueryBuilderConditionList.tsx`（WHERE/HAVING共通）・`QueryBuilderOrderByTab.tsx`の各行divに`styles.row`を付与するのみで、コンポーネント構造自体は変更なし
- 修正後、`npx tsc --noEmit`・`npm run lint`・`npm test -- --run`（全55ファイル219件）・`npm run build`をすべて実行し成功を確認

## 承認前レビュー対応4（FROMタブの駆動表とJOINの間隔）

続けて「FROMタブについて。FROMの駆動表とJOINの縦方向のスペースを開けて」という指摘を受けた。`QueryBuilderPage.tsx`のFROMタブcontentは`QueryBuilderFromTab`と`QueryBuilderJoinTab`をReact Fragment（`<>...</>`）で並べていたため間隔がなかった。`QueryBuilderPage.module.css`（新規）に`.fromSection`（`display:flex; flex-direction:column; gap:var(--mm-space-4)`）を追加し、Fragmentをdivに置き換えて縦の間隔を確保した。修正後、`npx tsc --noEmit`・`npm run lint`・`npm test -- --run`（全55ファイル219件）・`npm run build`をすべて実行し成功を確認

## 承認前レビュー対応5（JOIN条件右辺の候補列を左辺と同一化）

「FROMタブについて。JOIN条件の右辺にJOIN句のカラムしか出さないのは意図した挙動ですか？」という質問を受けた。実装を確認したところ、`QueryBuilderJoinTab.tsx`の`rightColumnsFor`関数により右辺の候補列がそのJOIN行自体の結合先テーブルの列のみに限定されており、左辺（`leftColumns`＝FROM起点テーブル＋それまでの全JOIN済みテーブルの列）とは非対称だった。これはCode Generation時の暗黙の実装判断であり、`business-rules.md`（BR-QUERYBUILDER-03）等には明記されていない。SQLの等価結合自体は左右対称なので表現力に制限はないが、ユーザーの意向を確認したところ「左辺と同じ選択肢にする」で確定した。

- `QueryBuilderJoinTab.tsx`: `rightColumnsFor`関数を削除。propsの`leftColumns`を`columns`にリネーム（左辺・右辺で同一の候補列を使うようになったため）。左辺・右辺いずれのSelectも`columns`（FROM起点テーブル＋全JOIN済みテーブルの列）から選べるように統一
- `QueryBuilderPage.tsx`: `<QueryBuilderJoinTab leftColumns={availableColumns} .../>`を`columns={availableColumns}`に変更
- `QueryBuilderPage.test.tsx`: 新規テストケース「JOIN条件の右辺にもFROM句・JOIN済みテーブルの列を選択できる」を追加（2テーブル・JOIN追加・結合条件追加のうえ、右辺selectの選択肢にFROM側の列が含まれることを確認）
- 修正後、`npx tsc --noEmit`・`npm run lint`・`npm test -- --run`（全55ファイル220件、新規1件含む）・`npm run build`をすべて実行し成功を確認

## 承認前レビュー対応6（FROM/JOINタブの実装を1ファイル・1コンポーネントに統合）

「FROMタブの実装が2ファイルに分かれている。1ファイルにできるか？」という指摘を受けた。承認前レビュー対応1でタブUIとしては1つに統合していたが、実装コンポーネント自体は`QueryBuilderFromTab`と`QueryBuilderJoinTab`の2つに分かれたままだった。これを1コンポーネントに統合した:

- `QueryBuilderFromTab.tsx`: `QueryBuilderJoinTab`が持っていたJOIN一覧の追加・削除・結合条件のロジック（`addJoin`/`updateJoin`/`removeJoin`/`addCondition`/`removeCondition`）を取り込み、propsを`schemaName, tables, columns, from, joins, onChangeFrom, onChangeJoins`に拡張。返り値のdivに（旧`QueryBuilderPage.module.css`の）`.fromSection`を適用し、駆動表とJOIN一覧の縦間隔もこのコンポーネント内で完結させた
- `QueryBuilderFromTab.module.css`: `QueryBuilderJoinTab.module.css`の`.join`/`.joinRow`/`.conditions`/`.conditionRow`と、`QueryBuilderPage.module.css`の`.fromSection`を統合
- `QueryBuilderJoinTab.tsx`・`QueryBuilderJoinTab.module.css`・`QueryBuilderPage.module.css`を削除
- `QueryBuilderPage.tsx`: `<QueryBuilderFromTab .../>`と`<QueryBuilderJoinTab .../>`の2呼び出しを、`<QueryBuilderFromTab schemaName=... tables=... columns=... from=... joins=... onChangeFrom=... onChangeJoins=... />`の1呼び出しに統合
- data-testid（`query-builder-join-*`等）はコンポーネント統合後も変更なし、既存テストは無修正のまま成功
- 修正後、`npx tsc --noEmit`・`npm run lint`・`npm test -- --run`（全55ファイル220件）・`npm run build`をすべて実行し成功を確認

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
- `QueryBuilderPage.test.tsx`: 7件（テーブル/カラム一覧読み込み、SQL生成プレビュー、保存/実行への遷移、リバースエンジニアリングの成功・失敗、承認前レビュー対応5で追加したJOIN条件右辺の選択肢確認）
- `QueryExecutionPage.test.tsx`: 既存5件に「クエリビルダーで編集」ボタンのテスト1件を追加
- `SavedQueryEditorPage.test.tsx`: 既存7件に「クエリビルダーで編集」ボタンのテスト2件（new/editモード双方）を追加
- `HomePage.test.tsx`: 実装済みバッジ数の変化（3→2）とqueryBuilderカードのテストを反映

全件成功（`npm test -- --run`、既存ユニット分含め全55ファイル220件成功）

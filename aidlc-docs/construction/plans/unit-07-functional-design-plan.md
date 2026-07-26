# UNIT-07 クエリビルダー Functional Design Plan

## 対象ユニット概要

- **対応エピック**: Epic 5、**対応ストーリー**: STORY-5.1〜5.3
- **対応要件**: FR-5.1〜FR-5.7
- **対応コンポーネント**: COMP-16（QueryBuilderService）
- **責務**: タブUI（SELECT / FROM / JOIN / WHERE / GROUP BY / HAVING / ORDER BY / LIMIT OFFSET）によるSQL組み立て、スキーマ非修飾SQLの生成（PBT対象）、実行・保存への連携、既存SQLからのリバースエンジニアリング
- **前提ユニット**: UNIT-01〜UNIT-04, UNIT-06（**UNIT-05は前提に含まれない** — unit-of-work.mdの記述どおり、Master Data用の`MasterDataService`には依存せず、UNIT-03の`SchemaIntrospectionService`・UNIT-04の`EffectivePermissionResolver`を直接組み合わせて独自にテーブル/カラムのアクセス可否判定を実装する方針とする）
- **PBT対象**: SQL生成ロジック（FR-5.5）— STORY-5.2でオラクル比較またはラウンドトリップ性質の検証対象と明記

## 実行計画

- [ ] Step 1: ユニット定義・関連ストーリー・既存コンポーネント（UNIT-03/04/06）の再確認（完了、本ファイル冒頭に反映）
- [x] Step 2: 本計画ファイルの作成・質問の提示
- [x] Step 3: ユーザからの回答収集・曖昧性チェック（Q1-8=A、Q9=B、Q10=A+逆遷移/相互遷移の追加、曖昧な回答なし）
- [x] Step 4: `business-logic-model.md` 作成（QueryBuilderStateの構造、SQL生成アルゴリズム、リバースエンジニアリングアルゴリズム）
- [x] Step 5: `business-rules.md` 作成（BR-QUERYBUILDER-01〜12、アクセス制御粒度・JOIN制約・条件構造等のルール化）
- [x] Step 6: `domain-entities.md` 作成（QueryBuilderState関連モデル、永続化なしの明記）
- [x] Step 7: `frontend-components.md` 作成（画面構成、タブUIコンポーネント階層、状態管理、API連携ポイント）
- [x] Step 8: 完了メッセージ提示・承認待ち

---

## 質問

以下の質問に回答してください。各質問は文字（A, B, C...）で回答し、最後の選択肢（Other）を選ぶ場合は自由記述してください。

## Question 1
FROM/JOINタブでの選択可能テーブル一覧、および他タブでの選択可能カラム一覧（FR-5.3）を絞り込むアクセス制御の粒度は？

A) UNIT-04/05と同じ列単位の実効権限フィルタリング（`EffectivePermissionResolver`で実効主権限がNONEの列・アクセス不可テーブルを一覧から除外する）

B) UNIT-06のad-hoc実行と同じスキーマ単位（アクセス可能なスキーマ内の全テーブル・全列を無条件に表示し、列単位の絞り込みは行わない）

C) 両方の組み合わせ（テーブル候補の絞り込みはスキーマ単位、カラム表示は列単位の実効権限でさらに絞り込む）

D) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 2
JOINタブでサポートするJOIN種別は？（対象RDBMSはMySQL/MariaDB/PostgreSQL/H2）

A) INNER JOIN / LEFT JOIN / RIGHT JOINのみ（対象RDBMS全種で共通してサポートされるためFULL JOINは除外）

B) INNER JOIN / LEFT JOIN / RIGHT JOIN / FULL JOINすべて（MySQL/MariaDBはFULL JOIN非対応のため、選択時に実行時エラーとなる可能性をUIで許容）

C) INNER JOINのみ（シンプルな組み合わせに限定）

D) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 3
JOIN条件（結合条件）の指定方式は？

A) 構造化された等価結合のみ（`左テーブルエイリアス.列 = 右テーブルエイリアス.列`の形式、複数条件はANDで結合）

B) 自由記述の結合条件式（任意のブール式をテキスト入力、FR-4.4のWHERE手入力に近い方式）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 4
WHERE/HAVINGタブでの条件指定の構造は？

A) フラットなリスト（すべての条件をANDで結合するのみ、グルーピング・ORなし。シンプルさ優先）

B) AND/ORの入れ子グルーピングをサポート（複雑な条件式に対応、UIとSQL生成の複雑度が増す）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 5
WHERE/HAVINGタブでの演算子・値型の扱いは？

A) UNIT-05 Master Dataの`FilterOperator`・`ColumnDataTypeCategory`と同じ体系を踏襲する（一貫性重視。UNIT-05への依存はせず、同じ設計思想で独自に定義し直す）

B) クエリビルダー専用の新しい演算子セット・型分類を定義する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 6
SELECT/GROUP BY/ORDER BYタブでの列参照の曖昧性回避方針（複数テーブルをJOINした場合、同名列が存在しうる）は？

A) 常にテーブルエイリアス修飾（`alias.column`）で統一して表示・SQL生成する（JOINの有無に関わらず一貫した挙動）

B) 単一テーブル参照時は非修飾、JOIN時のみエイリアス修飾に切り替える

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 7
リバースエンジニアリング（FR-5.7、`parseToBuilderState`）で、タブUIの構造で表現できない複雑なSQL（サブクエリ・UNION・CASE式・ウィンドウ関数等）を検出した場合の挙動は？

A) 専用の例外を返しリバースエンジニアリング自体を拒否する（呼び出し元画面は「クエリビルダーに反映できません」等のエラー表示に留め、手入力SQL編集の継続を促す）

B) 解析可能な範囲のみ部分的にタブへ反映し、非対応部分は無言で無視する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 8
`QueryBuilderState`（タブUIの指定内容を表すモデル）の永続化方針は？

A) 永続化しない。フロントエンドの画面状態、およびSQL生成API呼び出し時のリクエスト/レスポンスDTOとしてのみ存在する（生成された結果のSQL文字列のみがUNIT-06連携先（保存クエリ・ad-hoc実行）に渡される）

B) バックエンドDBに新規テーブルとして永続化する（ビルダー状態の一時保存・再開機能等を想定）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 9
SELECT/HAVING/ORDER BYタブで使用可能な集計関数（FR-5.4）の範囲は？

A) 標準5種のみ（COUNT / SUM / AVG / MIN / MAX）

B) 上記に加え、COUNT(DISTINCT column)等のバリエーションも含む

C) Other (please describe after [Answer]: tag below)

[Answer]: B（標準5種に加え、COUNT(DISTINCT column)等のバリエーションも含む）

## Question 10
画面構成・遷移フローは？

A) UNIT-06 Flow B（ad-hocクエリ実行）と同様のパターン：接続一覧画面→クエリビルダー画面（画面内で対象スキーマを選択し、タブUIでクエリを組み立てる）。SQL生成後は「保存」（Flow AのA-3新規保存クエリ画面へ遷移）・「実行」（Flow BのQueryExecutionPageへ遷移）の導線を提供する

B) 異なる画面構成にする

C) Other (please describe after [Answer]: tag below)

[Answer]: A（基本パターンは採用。加えて以下の逆遷移・相互遷移を追加する）
- クエリ実行画面（Flow B QueryExecutionPage）からクエリビルダーへの逆遷移（現在入力中のSQLをリバースエンジニアリングしてタブへ反映）
- 新規保存クエリ画面（Flow A-3）からクエリビルダーへの逆遷移（同上）
- 保存クエリ編集画面（Flow A、既存保存クエリの編集）との相互遷移（編集中のSQLをクエリビルダーへ渡して調整→編集画面へ戻す、の双方向）

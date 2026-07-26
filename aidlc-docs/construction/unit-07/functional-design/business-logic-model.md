# UNIT-07 クエリビルダー - Business Logic Model

`unit-07-functional-design-plan.md`の回答（Q1〜Q10、Q9=B・Q10=A+逆遷移追加以外は推奨どおりA）に基づく。技術非依存を原則とし、DBMS別の具体的な方言実装（JOIN構文の細かい差異等）はNFR Design/Code Generationで扱う。

対応コンポーネント: COMP-16 QueryBuilderService
前提: UNIT-03の`SchemaIntrospectionService`（構造メタデータ）、UNIT-04の`EffectivePermissionResolver`（実効権限判定）、UNIT-06の`QueryExecutionService`（アクセス可能な接続/スキーマ一覧の再利用）を利用する。**UNIT-05の`MasterDataService`には依存しない**（unit-of-work.mdの前提ユニット定義どおり）。

---

## 1. アクセス可能なテーブル/カラム一覧の取得（FR-5.2、FR-5.3、Q1=A）

FROM/JOINタブでのテーブル選択候補、および他タブ（SELECT/WHERE/GROUP BY/HAVING/ORDER BY）での選択可能カラム候補を提供する。

1. 対象接続・対象スキーマは、UNIT-06の`QueryExecutionService.listAccessibleConnections`/`listAccessibleSchemas`をそのまま呼び出して候補一覧を得る（BR-QUERY-02の「実効主権限READ以上を持つスキーマ」判定を再利用。新規の接続/スキーマ一覧ロジックは実装しない）
2. 選択されたスキーマについて、`SchemaIntrospectionService.getSchema(connectionId)`で構造メタデータ（`SchemaSnapshot`）を取得し、対象スキーマ名で絞り込んだ`SchemaTable`一覧を得る
3. 各テーブルの可視判定は、UNIT-05 `MasterDataService.isTableVisible()`と同じOR条件のロジックを踏襲する: `resolvePrimary(userId, connectionId, schemaName, tableName, null)`（テーブル単位の実効主権限）が**NONE**でない、**または**テーブル内のいずれか1列でも`resolvePrimary(userId, connectionId, schemaName, tableName, columnName)`（列単位の実効主権限）が**NONE**でない場合、そのテーブルを候補に含める。テーブル単位・スキーマ単位の設定がNONEでも、個別の列単位設定でREAD以上が付与されているケース（例:「テーブル全体は原則アクセス不可だが特定の列のみ閲覧可」）を正しく拾うため、テーブル単位の判定のみで除外してはならない
4. 候補に含まれた各テーブルについて、テーブル内の全カラムを`resolvePrimary(userId, connectionId, schemaName, tableName, columnName)`で列単位に評価する。実効主権限が**NONE**のカラムは、そのテーブルのカラム候補一覧から除外する
5. UNIT-06のBR-QUERY-04（ad-hoc生SQL実行時はスキーマ単位の粒度に留める）とは異なり、本ユニットではUI上で参照テーブル・参照カラムが常に明示的に特定できる（自由記述のSQL解析が不要）ため、列単位の実効権限判定が技術的に無理なく実現できる。両者の粒度の違いはこの技術的制約の有無に起因する（business-rules.md BR-QUERYBUILDER-01）
6. 列単位フィルタリングはあくまで**ビルダー画面での選択候補の絞り込み**であり、生成されたSQLをUNIT-06経由で実行する際は、BR-QUERY-04のスキーマ単位のアクセス制御が別途・独立して適用される（ビルダーの列単位フィルタと実行時のスキーマ単位ゲートは二重の防御だが、実行時のゲートが最終的な権限保証を担う）

---

## 2. QueryBuilderStateの構造（FR-5.1、domain-entities.md参照）

タブUIでの指定内容を表す、永続化しないメモリ上の状態モデル。フロントエンドの画面状態、およびSQL生成/リバースエンジニアリングAPIのリクエスト/レスポンスDTOとしてのみ存在する（Q8=A）。

- **FROM**: 起点テーブル1件（スキーマ名・テーブル名・エイリアス。エイリアス未指定時は自動生成する。§4参照）
- **JOIN**: 0件以上。各JOINは、結合種別（INNER/LEFT/RIGHT）・結合先テーブル（スキーマ名・テーブル名・エイリアス）・結合条件（等価結合条件のリスト、ANDで結合。§3参照）を持つ
- **SELECT**: 1件以上の選択項目。各項目は、列参照（テーブルエイリアス.列名）または集計関数適用（関数種別＋列参照、DISTINCT修飾可。§5参照）、および列別名（AS、任意）を持つ
- **WHERE**: 0件以上の条件（フラットなリスト、ANDのみで結合。Q4=A）。各条件は、列参照・演算子・比較値（リテラルのみ、パラメータ化はしない。UNIT-06のようなプレースホルダ機構は本ユニットでは扱わない）を持つ
- **GROUP BY**: 0件以上の列参照
- **HAVING**: 0件以上の条件（WHEREと同じ構造。集計関数適用の結果に対する比較も許容する）
- **ORDER BY**: 0件以上の項目。各項目は、列参照（または集計関数適用の結果）・昇順/降順を持つ
- **LIMIT/OFFSET**: LIMIT件数（任意）・OFFSET件数（任意）

---

## 3. JOIN条件の構造（FR-5.2、Q2=A、Q3=A）

1. サポートするJOIN種別はINNER/LEFT/RIGHTの3種のみ（business-rules.md BR-QUERYBUILDER-02）
2. JOIN条件は構造化された等価結合のみをサポートする。「左テーブルエイリアス.列 = 右テーブルエイリアス.列」の形式の条件を1件以上指定する（複数条件はANDで結合。任意のブール式・不等号結合・OR結合はサポートしない）
3. JOIN対象の右テーブルは、FROM起点テーブルまたは既存の左側JOIN群の中から、結合元となるテーブルエイリアスを指定して結合する（複数テーブルの連鎖的JOINを表現できる）

---

## 4. テーブルエイリアスと列参照の曖昧性回避（Q6=A）

1. FROM/JOINで指定されたテーブルには、ユーザが明示的にエイリアスを指定しない場合、テーブル名そのもの、またはテーブル名が同一スキーマ内で複数回参照される場合は連番を付与したエイリアス（例: `t1`, `t2`）を自動生成する
2. SELECT/WHERE/GROUP BY/HAVING/ORDER BYタブでの列参照は、JOINの有無に関わらず**常に**`エイリアス.列名`の形式で統一して表示・SQL生成する（単一テーブル参照時も同様。生成ロジックを単純化し、リバースエンジニアリングとのラウンドトリップにおける正準形を一定に保つため。business-rules.md BR-QUERYBUILDER-06）

---

## 5. 集計関数（FR-5.4、Q9=B）

1. サポートする集計関数はCOUNT / SUM / AVG / MIN / MAXの標準5種に加え、`COUNT(DISTINCT column)`のようなDISTINCT修飾のバリエーションを含む
2. DISTINCT修飾はCOUNTに限らずSUM/AVGにも適用可能とする（標準SQLの構文上有効なため、UIでの選択を妨げない）
3. 集計関数はSELECT/HAVING/ORDER BYタブで使用可能（FR-5.4）。WHERE/GROUP BYタブでは使用不可（集計前の行に対する条件指定・グルーピングキー指定であるため、標準SQLのセマンティクスに従う）

---

## 6. SQL生成アルゴリズム（FR-5.5、FR-5.6、PBT対象）

1. `QueryBuilderState`から、スキーマ非修飾のSQL文字列を組み立てる（`SELECT ... FROM ... [JOIN ...] [WHERE ...] [GROUP BY ...] [HAVING ...] [ORDER BY ...] [LIMIT ... OFFSET ...]`の順に、指定された内容のみを含める）
2. 生成されるSQLはテーブル名にスキーマ修飾子を含めない（対象スキーマは実行時に指定するため、FR-5.5・requirements.md §5.7）
3. SELECT句に集計関数を含む場合、GROUP BYに含まれない非集計列がSELECT句に存在してはならない（標準SQLのGROUP BY整合性制約）。この制約に違反する`QueryBuilderState`が渡された場合は、生成前に専用の検証エラーを返す（business-rules.md BR-QUERYBUILDER-11）
4. 生成したSQLは、そのままUNIT-06の保存クエリ作成画面（Flow A-3）またはad-hoc実行画面（Flow B-2）へ連携できる（FR-5.6）。連携時、生成後のSQLに対する追加のパラメータ化（`:param`形式への変換）は行わない。ユーザが連携先画面で必要に応じて手動編集する
5. **PBT対象（STORY-5.2）**: 「`QueryBuilderState`→SQL生成→SQLをリバースエンジニアリング→得られた`QueryBuilderState`が元の状態と等価である」というラウンドトリップ性質を検証する。厳密な文字列一致ではなく、構造化された状態同士の等価性（テーブル/エイリアス/条件/集計関数等の意味的な一致）で比較する。演算子の優先順位・空白の差異等、SQL文字列表現のゆれは比較対象外とする。具体的なプロパティ定義・テストフレームワークはNFR Requirements/Designで確定する

---

## 7. リバースエンジニアリングアルゴリズム（FR-5.7、Q7=A）

1. 入力されたSQL文字列をJSqlParserで構文解析する（UNIT-05/06で確立したパターンを踏襲）
2. パース結果が単一の`Select`文であり、かつタブUIで表現可能な構造（FROM起点テーブル1件、0件以上のJOIN（INNER/LEFT/RIGHTのみ、等価結合条件のみ）、フラットなWHERE/HAVING（ANDのみ）、GROUP BY、ORDER BY、LIMIT/OFFSET、対応済み集計関数のみを含むSELECT/HAVING/ORDER BY）に完全一致する場合のみ、対応する`QueryBuilderState`を構築して返す
3. 以下のいずれかに該当する場合は、タブUIへの反映を行わず、専用の例外（`QueryBuilderReverseEngineeringFailedException`、business-rules.md BR-QUERYBUILDER-07）を送出する。部分的な反映（対応可能な部分のみタブへ反映し、非対応部分を無言で無視する）は行わない（誤って一部の条件が欠落した状態のクエリを、ユーザが元のSQLと同一だと誤認するリスクを避けるため）
   - サブクエリ・UNION・CASE式・ウィンドウ関数等、タブUIで表現できない構文要素を含む
   - OR結合・不等号結合等、サポート対象外のJOIN条件・WHERE/HAVING条件構造を含む
   - FULL JOIN等、サポート対象外のJOIN種別を含む
   - 未対応の集計関数、または集計関数の入れ子等の複雑な式を含む
   - パース結果が参照するテーブル・カラムが、対象スキーマの構造メタデータ（`SchemaIntrospectionService`）に存在しない、またはユーザの実効権限（`EffectivePermissionResolver`）でREAD以上を持たない（§1と同じアクセス制御の粒度を、リバースエンジニアリング時にも一貫して適用する）
4. リバースエンジニアリングの呼び出し元（クエリ実行画面・保存クエリ編集画面等）は、例外発生時に「クエリビルダーに反映できません」等のエラー表示に留め、遷移元の手入力SQL編集画面への留まりを継続する（frontend-components.md参照）

---

## 8. Testable Properties（PBT-01、Property-Based Testing拡張）

本ユニットのコンポーネント（COMP-16 QueryBuilderService）について、テスト可能プロパティを識別する。

| 対象メソッド | プロパティ分類 | プロパティの内容 |
|---|---|---|
| `generateSql` / `parseToBuilderState`の組（§6・§7） | **Round-trip**（そして戻ってくる） | `QueryBuilderState`→`generateSql`→`parseToBuilderState`で得られる`QueryBuilderState`が、構造的に元の状態と等価である（§6のPBT対象の記載を参照。タブUIで表現可能な範囲の`QueryBuilderState`を生成する専用ジェネレータを用いる） |
| `generateSql`（§6） | **Invariant**（不変条件） | GROUP BY整合性制約（BR-QUERYBUILDER-11）に違反する`QueryBuilderState`は常に検証エラーとなり、有効なSQL文字列を生成しない（生成される場合は常に構文的に妥当なSELECT文である） |
| アクセス可能テーブル/カラム一覧の取得（§1） | **Invariant**（不変条件） | 返却される全てのカラムについて、実効主権限がREAD以上である（BR-QUERYBUILDER-01）。返却される全てのテーブルについて、含まれるカラムが1件以上存在する |
| `parseToBuilderState`の失敗判定（§7） | **No PBT properties identified** | 失敗条件（サポート対象外の構文要素の網羅的な検出）は、個々の構文要素ごとの分岐処理であり、汎用的に成立する不変条件・ラウンドトリップ性質としては表現しにくい。各失敗パターン（サブクエリ・UNION・CASE式・FULL JOIN等）は例示ベーステストで個別に検証する |

具体的なプロパティの厳密な定義（同値性の判定方法、ジェネレータの構造）およびテストフレームワーク選定はNFR Requirementsで確定する（PBT-09）。

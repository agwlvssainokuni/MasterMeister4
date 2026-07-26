# UNIT-07 クエリビルダー - Business Rules

`unit-07-functional-design-plan.md`の回答に基づく。BR-QUERYBUILDER-01〜11として定義する。

---

### BR-QUERYBUILDER-01: アクセス制御の粒度（Q1=A、重要な設計判断）

クエリビルダーのFROM/JOINタブでのテーブル候補、および他タブでのカラム候補は、列単位の実効権限（`EffectivePermissionResolver`）でフィルタリングする。実効主権限がNONEのカラムは候補から除外し、候補カラムが1件も残らないテーブルは候補自体から除外する。

これはUNIT-06のBR-QUERY-04（ad-hoc生SQL実行はスキーマ単位の粒度に留める）とは異なる粒度だが、矛盾ではない。BR-QUERY-04がスキーマ単位に留める理由は「自由記述のSQLに対する参照テーブル/カラムの網羅的な静的解析が技術的に大きな負担となるため」であり、クエリビルダーはUI上のタブ選択によって参照テーブル/カラムが常に明示的に特定できるため、この技術的制約が存在しない。したがって列単位の実効権限判定を無理なく適用できる。

なお、この列単位フィルタリングはビルダー画面での選択候補の絞り込みに留まる。生成されたSQLをUNIT-06経由で実行する時点では、BR-QUERY-04のスキーマ単位のアクセス制御が独立して適用され、最終的な権限保証を担う。

---

### BR-QUERYBUILDER-02: サポートするJOIN種別（Q2=A）

INNER JOIN / LEFT JOIN / RIGHT JOINの3種のみをサポートする。FULL JOINは対象RDBMS4種のうちMySQL/MariaDBが非対応のため、サポート対象外とする。

---

### BR-QUERYBUILDER-03: JOIN条件の構造（Q3=A）

JOIN条件は構造化された等価結合（`左エイリアス.列 = 右エイリアス.列`、複数条件はAND結合）のみをサポートする。自由記述の結合条件式（任意のブール式、不等号結合等）はサポートしない。

---

### BR-QUERYBUILDER-04: WHERE/HAVING条件の構造（Q4=A）

WHERE/HAVINGタブでの条件指定は、すべての条件をANDで結合するフラットなリストのみをサポートする。AND/ORの入れ子グルーピングはサポートしない。より複雑な条件式が必要な場合は、UNIT-06のad-hoc実行画面（Flow B-2）での手入力SQL編集で代替する。

---

### BR-QUERYBUILDER-05: 演算子・型分類の体系（Q5=A）

WHERE/HAVINGタブでの演算子・値型は、UNIT-05 Master Dataの`FilterOperator`・`ColumnDataTypeCategory`と同じ設計思想（列のデータ型に応じた演算子の絞り込み等）を踏襲する。ただし、UNIT-07はUNIT-05に依存しない方針（unit-of-work.md）のため、`MasterDataService`・既存の`FilterOperator`/`ColumnDataTypeCategory`クラスを再利用（import）はせず、クエリビルダー独自のクラスとして同じ設計思想で定義し直す。

---

### BR-QUERYBUILDER-06: 列参照の曖昧性回避（Q6=A）

SELECT/WHERE/GROUP BY/HAVING/ORDER BYタブでの列参照は、JOINの有無に関わらず常に`テーブルエイリアス.列名`の形式で統一して表示・SQL生成する。FROM/JOINタブで指定されたテーブルにユーザがエイリアスを明示しない場合は、テーブル名（同一スキーマ内で複数回参照される場合は連番付きの自動生成エイリアス）をエイリアスとして自動採用する。

---

### BR-QUERYBUILDER-07: リバースエンジニアリング失敗時の挙動（Q7=A）

`parseToBuilderState`がタブUIで表現できない構文（サブクエリ・UNION・CASE式・ウィンドウ関数、サポート対象外のJOIN種別・JOIN条件・WHERE/HAVING構造、未対応の集計関数）、または参照するテーブル/カラムがBR-QUERYBUILDER-01の実効権限（READ以上）を満たさない場合、専用の例外を送出しリバースエンジニアリング自体を拒否する。解析可能な範囲のみを部分的にタブへ反映する（非対応部分を無言で無視する）ことは行わない。ユーザが元のSQLと異なる（一部の条件が欠落した）状態のクエリをタブUI上で誤認するリスクを避けるため。

---

### BR-QUERYBUILDER-08: QueryBuilderStateの永続化方針（Q8=A）

`QueryBuilderState`はバックエンドDBに永続化しない。フロントエンドの画面状態、およびSQL生成/リバースエンジニアリングAPI呼び出し時のリクエスト/レスポンスDTOとしてのみ存在する。生成された結果のSQL文字列のみが、UNIT-06の連携先（保存クエリ・ad-hoc実行）に渡される。

---

### BR-QUERYBUILDER-09: サポートする集計関数の範囲（Q9=B）

COUNT / SUM / AVG / MIN / MAXの標準5種に加え、`COUNT(DISTINCT column)`のようなDISTINCT修飾のバリエーションをサポートする。DISTINCT修飾はCOUNT/SUM/AVGに適用可能とする（標準SQLの構文上有効なため）。集計関数はSELECT/HAVING/ORDER BYタブでのみ使用可能とする（FR-5.4）。

---

### BR-QUERYBUILDER-10: 生成SQLのスキーマ非修飾（FR-5.5）

生成するSQLは、テーブル名にスキーマ修飾子を含めない。対象スキーマは、生成されたSQLをUNIT-06で実行する時点で別途指定する（requirements.md §5.7）。

---

### BR-QUERYBUILDER-11: GROUP BY整合性の検証

SELECT句に集計関数を含む場合、GROUP BYに含まれない非集計列がSELECT句に存在してはならない（標準SQLのGROUP BY整合性制約）。この制約に違反する`QueryBuilderState`に対しては、SQL生成前に専用の検証エラーを返し、不正なSQLの生成を防ぐ。

---

### BR-QUERYBUILDER-12: 画面遷移・連携方針（Q10=A、逆遷移・相互遷移を含む）

1. クエリビルダー画面は、UNIT-06 Flow B（ad-hocクエリ実行）と同様のパターンを踏襲する: 接続一覧画面→クエリビルダー画面（画面内で対象スキーマを選択し、タブUIでクエリを組み立てる）
2. SQL生成後は、「保存」（Flow AのA-3新規保存クエリ画面へ、生成SQLを引き継いで遷移）・「実行」（Flow BのQueryExecutionPageへ、生成SQLを引き継いで遷移）の導線を提供する
3. **逆遷移・相互遷移（追加要件）**:
   - クエリ実行画面（Flow B QueryExecutionPage）から、現在入力中のSQLをクエリビルダーへ逆遷移できる（§7のリバースエンジニアリングを適用し、タブへ反映する）
   - 新規保存クエリ画面（Flow A-3）から、同様にクエリビルダーへ逆遷移できる
   - 保存クエリ編集画面（Flow A、既存保存クエリの編集）とクエリビルダーは相互遷移する（編集中のSQLをクエリビルダーへ渡して調整し、調整結果を編集画面へ戻す、の双方向）
4. いずれの逆遷移・相互遷移でも、リバースエンジニアリングが失敗した場合（BR-QUERYBUILDER-07）は、遷移元の手入力SQL編集画面に留まりエラー表示するのみとし、遷移自体は行わない

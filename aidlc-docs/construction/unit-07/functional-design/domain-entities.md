# UNIT-07 クエリビルダー - Domain Entities

business-rules.mdで定義したルールに対応するドメインモデルを定義する。永続化技術の詳細はNFR Design／Code Generationステージで確定する。

**内部DBへの新規永続化エンティティなし**（BR-QUERYBUILDER-08、Q8=A）: 本ユニットは`QueryBuilderState`を含め、新規のDB永続化エンティティを持たない。以下はすべて、SQL生成/リバースエンジニアリングAPIのリクエスト/レスポンスDTO、およびフロントエンドの画面状態としてのみ存在するメモリ上のモデルである。

---

## 1. QueryBuilderState（FR-5.1、業務ロジックモデル §2）

タブUIでの指定内容全体を表すルートモデル。

| 属性 | 型 | 説明 |
|---|---|---|
| `from` | FromClause | 起点テーブル（必須、1件） |
| `joins` | List\<JoinClause\> | JOIN一覧（0件以上） |
| `selectItems` | List\<SelectItem\> | SELECT項目一覧（1件以上） |
| `whereConditions` | List\<Condition\> | WHERE条件一覧（0件以上、ANDのみ） |
| `groupByColumns` | List\<ColumnRef\> | GROUP BY列一覧（0件以上） |
| `havingConditions` | List\<Condition\> | HAVING条件一覧（0件以上、ANDのみ） |
| `orderByItems` | List\<OrderByItem\> | ORDER BY項目一覧（0件以上） |
| `limit` | Integer（nullable） | LIMIT件数 |
| `offset` | Integer（nullable） | OFFSET件数 |

---

## 2. FromClause / JoinClause（BR-QUERYBUILDER-02・03・06）

### FromClause
| 属性 | 型 | 説明 |
|---|---|---|
| `schemaName` | String | 対象スキーマ名 |
| `tableName` | String | テーブル名 |
| `alias` | String | テーブルエイリアス（未指定時は自動生成、BR-QUERYBUILDER-06） |

### JoinClause
| 属性 | 型 | 説明 |
|---|---|---|
| `joinType` | JoinType | `INNER` / `LEFT` / `RIGHT`（BR-QUERYBUILDER-02） |
| `schemaName` | String | 結合先テーブルのスキーマ名 |
| `tableName` | String | 結合先テーブル名 |
| `alias` | String | 結合先テーブルエイリアス |
| `onConditions` | List\<JoinCondition\> | 結合条件（1件以上、ANDで結合。BR-QUERYBUILDER-03） |

### JoinCondition
| 属性 | 型 | 説明 |
|---|---|---|
| `leftColumn` | ColumnRef | 左辺列参照（既存のFROM/JOIN済みテーブルのエイリアスを参照） |
| `rightColumn` | ColumnRef | 右辺列参照（このJOINの結合先テーブルのエイリアスを参照） |

---

## 3. ColumnRef（BR-QUERYBUILDER-06）

列参照の共通モデル。常にエイリアス修飾で保持する。

| 属性 | 型 | 説明 |
|---|---|---|
| `tableAlias` | String | テーブルエイリアス |
| `columnName` | String | 列名 |

---

## 4. SelectItem（FR-5.4、BR-QUERYBUILDER-09）

| 属性 | 型 | 説明 |
|---|---|---|
| `column` | ColumnRef（nullable） | 単純な列参照の場合に指定（`aggregate`と排他） |
| `aggregate` | AggregateExpression（nullable） | 集計関数適用の場合に指定（`column`と排他） |
| `alias` | String（nullable） | 列別名（AS） |

### AggregateExpression
| 属性 | 型 | 説明 |
|---|---|---|
| `function` | AggregateFunction | `COUNT` / `SUM` / `AVG` / `MIN` / `MAX` |
| `column` | ColumnRef | 集計対象列 |
| `distinct` | boolean | DISTINCT修飾の有無（COUNT/SUM/AVGのみ有効、BR-QUERYBUILDER-09） |

---

## 5. Condition（WHERE/HAVING共通、BR-QUERYBUILDER-04・05）

| 属性 | 型 | 説明 |
|---|---|---|
| `column` | ColumnRef（nullable） | 比較対象列（`aggregate`と排他、HAVINGでのみ`aggregate`を許容） |
| `aggregate` | AggregateExpression（nullable） | 比較対象が集計結果の場合に指定（HAVINGでのみ使用） |
| `operator` | ConditionOperator | 演算子（UNIT-05の設計思想を踏襲した独自enum、BR-QUERYBUILDER-05） |
| `value` | String（nullable） | 比較値（リテラル。IS NULL/IS NOT NULL等、値不要の演算子ではnull） |

---

## 6. OrderByItem

| 属性 | 型 | 説明 |
|---|---|---|
| `column` | ColumnRef（nullable） | 並び替え対象列（`aggregate`と排他） |
| `aggregate` | AggregateExpression（nullable） | 並び替え対象が集計結果の場合に指定 |
| `direction` | SortDirection | `ASC` / `DESC` |

---

## 7. アクセス可能テーブル/カラム一覧のレスポンスモデル（業務ロジックモデル §1）

FROM/JOINタブ・他タブでの選択候補提示に使用する（DBには永続化しない、参照専用の一時的なレスポンスモデル）。

### AccessibleBuilderTable
| 属性 | 型 | 説明 |
|---|---|---|
| `tableName` | String | テーブル名 |
| `tableType` | TableType | `TABLE` / `VIEW`（UNIT-03の既存enumを再利用） |
| `columns` | List\<AccessibleBuilderColumn\> | 実効主権限READ以上を持つカラムのみ（BR-QUERYBUILDER-01） |

### AccessibleBuilderColumn
| 属性 | 型 | 説明 |
|---|---|---|
| `columnName` | String | 列名 |
| `dataTypeCategory` | ColumnDataTypeCategory | 演算子絞り込みに使う型分類（UNIT-05の設計思想を踏襲した独自enum、BR-QUERYBUILDER-05） |

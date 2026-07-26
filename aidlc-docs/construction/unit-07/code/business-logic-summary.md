# UNIT-07 クエリビルダー - Business Logic Summary

## 作成したコンポーネント

### DTO・enum（`cherry.mastermeister.querybuilder.dto`）
`QueryBuilderState`の各要素（`ColumnRefDto`, `FromClauseDto`, `JoinClauseDto`, `JoinConditionDto`, `AggregateExpressionDto`, `SelectItemDto`, `ConditionDto`, `OrderByItemDto`）、リクエスト/レスポンスラッパー（`QueryBuilderStateRequest`/`Response`）、enum（`JoinType`, `AggregateFunction`, `ConditionOperator`, `ColumnDataTypeCategory`, `SortDirection`）。DB永続化を持たないため、UNIT-05/06のようなmodel/dto分離をせず入れ子DTOを共用する設計とした（Part 1計画参照）。

**実装時の発見**: `generateSql`はDBアクセスを伴わない純粋な変換だが、比較値の型安全なリテラル変換には列のデータ型分類が必要なため、`ConditionDto`に`dataTypeCategory`フィールドを追加した（フロントエンドがテーブル/カラム一覧取得APIで既に取得済みの値をそのまま送信する）。

`SelectItemDto`/`ConditionDto`/`OrderByItemDto`には`@AssertTrue`でcolumn/aggregateの排他性を検証するメソッドを追加した。

### QueryBuilderColumnTypeMapper（`cherry.mastermeister.querybuilder`）
UNIT-05の`ColumnDataTypeMapper`と同じ設計思想（`NormalizedType`からのマッピング）を独自実装。

### 新規例外（`cherry.mastermeister.common.exception`）
`QueryBuilderInvalidGroupByException`（400）、`QueryBuilderUnsupportedSqlException`（422）、`QueryBuilderReferenceNotAccessibleException`（403）、`QueryBuilderInvalidLiteralException`（400）。いずれも`ApiException`のサブクラスで既存の汎用ハンドラで自動処理される。UNIT-06の`QuerySchemaNotAccessibleException`（403）は新規定義せずそのままimportして再利用する。`messages_ja.properties`/`messages_en.properties`にエラーメッセージを追加。

### QueryBuilderAccessResolver（`cherry.mastermeister.querybuilder`）
アクセス可能テーブル/カラム一覧取得（`listAccessibleTables`）と参照テーブル/カラムの存在・アクセス可否確認（`isColumnAccessible`）を担当。UNIT-06の`listAccessibleSchemas`でスキーマ許可リストを確認したうえで、UNIT-05の`isTableVisible`と同じOR条件（テーブル単位・列単位いずれかの実効主権限が非NONE）でテーブル候補を判定する。

### QueryBuilderService（COMP-16）
`generateSql`（`QueryBuilderStateRequest`→SQL文字列）・`parseToBuilderState`（SQL文字列→`QueryBuilderStateResponse`）の2メソッド。JSqlParserのオブジェクトモデル（`PlainSelect`/`Table`/`Join`/`SelectItem`/`GroupByElement`/`OrderByElement`等）を構築・走査する。

**実機テストで発見・修正した実装バグ（3件、いずれも単体テストの実行時に発見）**:

1. **エイリアスと実テーブル名の取り違え**: WHERE/HAVING条件の列参照（例: `t1.id`）の実効権限チェック時、SQL上のテーブルエイリアス`t1`をそのまま実テーブル名として`EffectivePermissionResolver`に渡してしまっていた。FROM/JOIN句からエイリアス→実テーブル名のマッピング（`ParseCtx.aliasToTable`）を構築し、権限チェック直前に解決するよう修正した。
2. **JSqlParserのリテラルクラスの検証タイミングの誤解**: `LongValue(String)`は実際には値を検証せず、`getValue()`呼び出し時に遅延パースするのみ（不正な数値文字列がそのまま生成SQLへ埋め込まれてしまう）。`BooleanValue(String)`も`Boolean.parseBoolean`を使うため不正な値が無条件に`false`になる。コンストラクタ呼び出し前に明示的な数値・真偽値検証を追加した。また`DateValue`の`toString()`はJDBC escape構文`{d '...'}`になり対象4方言への直接実行時の移植性に懸念があったため不採用とし、日時は単純な文字列リテラル（例: `'2026-01-01'`）として埋め込む方式に変更した（対象4方言はいずれも文字列からの暗黙変換を受け付ける）。
3. **HAVING句の集計関数オペランド未対応**: 比較の左辺が集計関数（例: `COUNT(t1.id) > 5`）の場合に対応できていなかった（単純な列参照のみを想定していた）。左辺が列参照/集計関数のいずれでも解析できるよう`parseCondition`をリファクタリングした。

いずれもJSqlParserの実クラス・単体テストの実行結果から発見したものであり、事前のAPI調査だけでは気づけなかった（特に1・2はライブラリの実際の挙動が事前の想定と異なっていたことによる）。

## PBT（Property-Based Testing）実装

business-logic-model.md §8で識別したラウンドトリップ性質をjqwikで実装（`QueryBuilderServicePropertyTest`）: `QueryBuilderState`→`generateSql`→`parseToBuilderState`のラウンドトリップが元の状態と構造的に等価である。`QueryBuilderAccessResolver`をMockでスタブ化し常にアクセス可能として扱うことで、実際のスキーマ・権限判定への依存を切り離し、SQL生成/解析の構文的な往復性のみを検証する。1000回試行、全件成功。

GROUP BY整合性の不変条件・アクセス可能テーブル/カラム一覧のREAD以上不変条件は、`QueryBuilderServiceTest`/`QueryBuilderAccessResolverTest`の例示ベーステストで個別に検証した（jqwikのプロパティとしては実装していない。理由: これらは特定の分岐条件下での挙動確認であり、汎用的な入力空間全体で成立する性質としての追加検証価値が低いと判断）。

## テスト結果

- `QueryBuilderColumnTypeMapperTest`: 5件
- `QueryBuilderAccessResolverTest`: 7件
- `QueryBuilderServiceTest`: 26件（`generateSql`の各タブ組み合わせ、`parseToBuilderState`の正常系・異常系）
- `QueryBuilderServicePropertyTest`: 1プロパティ（1000回試行）
- 全件成功（`./gradlew :backend:test --tests "cherry.mastermeister.querybuilder.*"`）

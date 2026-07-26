# UNIT-07 クエリビルダー - NFR Design Patterns

`unit-07-nfr-design-plan.md`の回答（Q1〜Q5、推奨どおり全問A）に基づく実装パターンを記載する。

---

## 1. Resilience / Security（エラーハンドリング）

### 1.1 GROUP BY整合性違反時のエラーハンドリング（Q1=A、BR-QUERYBUILDER-11）
- SELECT句に集計関数を含み、かつGROUP BYに含まれない非集計列がSELECT句に存在する`QueryBuilderState`が`generateSql`に渡された場合、新規例外`QueryBuilderInvalidGroupByException`（400 BAD_REQUEST）を送出する
- UNIT-05/06の1業務ルール1例外クラスの既存方針を踏襲する。Bean Validationのフィールドレベル検証（Q5相当のリスト件数上限）とは別に、クロスフィールドの業務ルール検証として`QueryBuilderService`内で明示的にチェックする

### 1.2 リバースエンジニアリング失敗時のエラーハンドリング（Q2=A、BR-QUERYBUILDER-07、重要）
BR-QUERYBUILDER-07が列挙する失敗要因を、性質の異なる2種類に分けてそれぞれ専用例外を送出する:

- **構文的に非対応**（サブクエリ・UNION・CASE式・ウィンドウ関数、OR結合・不等号結合等の非対応条件構造、FULL JOIN等の非対応JOIN種別、未対応の集計関数）: 新規例外`QueryBuilderUnsupportedSqlException`（422 UNPROCESSABLE_ENTITY）
- **アクセス権限不足**（参照するテーブル・カラムが対象スキーマの構造メタデータに存在しない、またはユーザの実効権限でREAD以上を持たない）: 新規例外`QueryBuilderReferenceNotAccessibleException`（403 FORBIDDEN）

呼び出し元画面（frontend-components.md）は、例外の種別に応じてエラーメッセージを出し分ける（「この構文はクエリビルダーで編集できません」／「アクセス権限のないテーブル・カラムを参照しています」）。いずれの例外も、部分的な`QueryBuilderState`の返却は行わない（BR-QUERYBUILDER-07のフェイルクローズ方針）。

### 1.3 スキーマアクセス不可時のエラーハンドリング（Q3=A、business-logic-model.md §1）
- テーブル/カラム一覧取得（`GET /api/query-builder/{connectionId}/tables`）で指定された`schemaName`が、UNIT-06の`QueryExecutionService.listAccessibleSchemas`が返すアクセス可能なスキーマ一覧に含まれない場合、UNIT-06の既存例外`QuerySchemaNotAccessibleException`（403 FORBIDDEN、`cherry.mastermeister.common.exception`パッケージ）をそのままimportして送出する
- 本ユニットは元々UNIT-06のスキーマ許可リスト判定ロジックをそのまま利用する方針（business-logic-model.md §1）であり、同一の失敗モードに対して新規の例外クラスを重複定義しない

### 1.4 リクエストサイズ上限超過時のエラーハンドリング（NFR Requirements Q5=A）
- `QueryBuilderState`の各リスト（`selectItems`/`joins`/`whereConditions`/`groupByColumns`/`havingConditions`/`orderByItems`）の件数上限は、Bean Validationの`@Size`アノテーションで強制する。超過時はUNIT-02〜06で確立済みの標準的なバリデーションエラー応答（400、フィールドごとのエラーメッセージ）とする。専用例外は設けない

---

## 2. Security（SQL生成時の安全なリテラル埋め込み）

### 2.1 型安全なリテラル構築（tech-stack-decisions.md §2の詳細化）
- `QueryBuilderService`内に、`ColumnDataTypeCategory`と比較値の文字列表現を受け取り、対応するJSqlParserのExpressionオブジェクト（`LongValue`/`DoubleValue`/`StringValue`/`DateValue`/`TimestampValue`/`BooleanValue`）を構築する専用のプライベートメソッド（例: `buildLiteralExpression(ColumnDataTypeCategory, String)`）を設ける
- `NUMERIC`は値に小数点を含むかで`LongValue`/`DoubleValue`を判定する。数値として解釈できない入力（例: 非数値文字列）は、Bean Validationの`@Pattern`等でリクエスト受理前に弾くのではなく、`buildLiteralExpression`内で捕捉し`QueryBuilderInvalidLiteralException`（新規、400 BAD_REQUEST）を送出する（列のデータ型分類と実際の入力値が矛盾するケースへの対応）
- IS NULL/IS NOT NULL演算子は値を要求しないため、`buildLiteralExpression`を呼び出さず`IsNullExpression`を直接構築する

---

## 3. Performance

### 3.1 アクセス可能テーブル/カラム一覧取得（NFR Requirements Q6=A、business-logic-model.md §1）
- `QueryBuilderAccessResolver`（§Q4、logical-components.md参照）が、対象スキーマ内の全テーブル・全カラムに対して既存の`EffectivePermissionResolver.resolvePrimary`をループ呼び出しする
- UNIT-04のCaffeineキャッシュ（`effectivePermission`、テーブル/カラム単位）がヒットする前提のため、新規の専用キャッシュ層は追加しない

### 3.2 SQL生成・リバースエンジニアリングの処理コスト
- いずれもDBアクセスを伴わない（生成）、または軽量なJSqlParser構文解析＋既存キャッシュ参照（解析）であるため、追加の性能最適化は不要

---

## 4. Logical Components（配置方針の要約、詳細はlogical-components.md）

- SQL生成・リバースエンジニアリング: `QueryBuilderService`（Q4=A、2責務に専念）
- アクセス可能テーブル/カラム一覧取得: `QueryBuilderAccessResolver`（Q4=A、新設・分離）
- Controller: 単一の`QueryBuilderController`（Q5=A、3エンドポイント）

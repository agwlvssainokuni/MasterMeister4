# UNIT-07 クエリビルダー - Tech Stack Decisions

`unit-07-nfr-requirements-plan.md`の回答（Q1〜Q6、推奨どおり全問A）に基づく。新規外部ライブラリの追加はなし（JSqlParserはUNIT-05で追加済みを再利用）。

---

## 1. WHERE/HAVING比較値の生成方式（Q1=A）

WHERE/HAVINGタブでの比較値は、リテラル値として直接SQL文字列へ埋め込む（例: `WHERE t1.age > 30`）。UNIT-06の`:param`プレースホルダ機構とは独立して動作し、生成のたびに指定値を含む固定のSQLとなる。値をビルダー画面から実行/保存画面へ引き継ぐ追加のデータ受け渡し機構は不要（生成されたSQL文字列自体が唯一の連携データ、BR-QUERYBUILDER-08と整合）。

ユーザが後から値を可変にしたい場合は、生成・連携後のSQL編集画面（UNIT-06のFlow A-3・Flow B-2）で手動で`:param`形式に書き換える運用とする（domain-entities.md §5のCondition.valueの位置づけを維持）。

## 2. 比較値の安全なSQL埋め込み方式（Q2=A、SECURITY-05）

比較値は、JSqlParserのExpression構築API（`net.sf.jsqlparser.expression`パッケージの`StringValue`/`LongValue`/`DoubleValue`/`DateValue`等）を用いて、列のデータ型分類（`ColumnDataTypeCategory`、§4）に応じた型安全なリテラルオブジェクトとして構築する:

- `NUMERIC` → `LongValue`または`DoubleValue`（整数/小数を値の形式から判定）
- `DATETIME` → `DateValue`/`TimestampValue`（JSqlParserが自動的にクォート・リテラル接頭辞を付与）
- `STRING` → `StringValue`（コンストラクタが内部でシングルクォートのエスケープを行う）
- `BOOLEAN` → JSqlParserの真偽値リテラル相当（方言により`TRUE`/`FALSE`リテラルまたは`1`/`0`を使い分けない。標準SQLの`TRUE`/`FALSE`リテラルで統一し、対象4方言（PostgreSQL/MySQL/MariaDB/H2）いずれも解釈可能なことを確認済み）

これらをASTの`BinaryExpression`（比較演算子）の右辺として組み込み、文字列連結による手動エスケープを一切行わない。IS NULL/IS NOT NULL等の値不要の演算子は、対応するJSqlParser式クラス（`IsNullExpression`）を使用する。

## 3. SQL生成・リバースエンジニアリングの実装基盤（Q3=A）

- **生成（`generateSql`）**: JSqlParserのオブジェクトモデル（`PlainSelect`/`Table`/`Join`/`SelectItem`/`GroupByElement`/`OrderByElement`等）を`QueryBuilderState`から組み立て、`.toString()`で文字列化する
- **解析（`parseToBuilderState`）**: UNIT-05/06で確立したパターン（`CCJSqlParserUtil.parseStatements(sql)`で単一`Select`文であることを確認）を踏襲し、得られた`PlainSelect`のASTを走査してタブUIで表現可能な構造かを判定しつつ`QueryBuilderState`へ変換する
- 生成・解析の両方を同じJSqlParserのオブジェクトモデルで扱うことで、ラウンドトリップPBT（business-logic-model.md §8）における構文的な非対称性のリスクを低減する

## 4. ColumnDataTypeCategoryのマッピング元（Q4=A）

UNIT-05の`ColumnDataTypeMapper`と同じ設計思想（UNIT-03の`SchemaColumn.normalizedType`からのマッピング）を、`cherry.mastermeister.querybuilder`パッケージ内に独自クラス（例: `QueryBuilderColumnTypeMapper`）として再実装する。UNIT-05の`masterdata`パッケージへの依存は行わない（unit-of-work.mdの前提ユニット定義どおり）。

| NormalizedType（UNIT-03） | ColumnDataTypeCategory（本ユニット） |
|---|---|
| NUMBER | NUMERIC |
| DATE_TIME | DATETIME |
| BOOLEAN | BOOLEAN |
| STRING, BINARY, OTHER | STRING（UNIT-05と同じフォールバック方針） |

## 5. リクエストサイズ上限（Q5=A、SECURITY-05）

`QueryBuilderState`の各リストに、Bean Validationの`@Size`で以下の上限を設ける:

| リスト | 上限件数 |
|---|---|
| `selectItems` | 50 |
| `joins` | 20 |
| `whereConditions` | 50 |
| `groupByColumns` | 50 |
| `havingConditions` | 50 |
| `orderByItems` | 50 |

上限超過時は400エラー（Bean Validationの標準的なバリデーションエラー応答、UNIT-02〜06で確立済みの方式）とする。

## 6. アクセス可能テーブル/カラム一覧取得のキャッシュ戦略（Q6=A）

`EffectivePermissionResolver`への新規メソッド追加・新規キャッシュ層の追加は行わない。テーブル/カラム一覧取得処理が、対象スキーマ内の全テーブル・全カラムに対して既存の`resolvePrimary`をループ呼び出しする（UNIT-05の`MasterDataService.listAccessibleTables`と同じ実装パターン）。UNIT-04のCaffeineキャッシュ（テーブル/カラム単位、maximumSize=10,000, expireAfterWrite=30分）がヒットする前提のため、追加のキャッシュ層は不要と判断する。

## 7. 新規依存関係

なし。JSqlParserはUNIT-05で追加済みの依存関係を再利用する（今回、初めて構文解析だけでなくAST構築による文字列生成にも使用するが、追加のライブラリ依存は発生しない）。

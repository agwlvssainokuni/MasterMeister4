# UNIT-05 マスタメンテナンス - Data Access Layer Summary

Code Generation計画Step 1〜4の実施結果。

## 作成したコンポーネント

| コンポーネント | パッケージ | 責務 |
|---|---|---|
| `ColumnDataTypeMapper` | `cherry.mastermeister.masterdata` | UNIT-03の`SchemaColumn.normalizedType`から`ColumnDataTypeCategory`（NUMERIC/DATETIME/STRING/BOOLEAN）への変換 |
| `RawQueryConditionValidator` | `cherry.mastermeister.masterdata` | SQL手入力のWHERE/ORDER BY句をJSqlParserで構文検証し、パラメータ化されたSQL断片へ変換する（BR-MASTER-04） |
| `RecordQueryService` | `cherry.mastermeister.masterdata` | `NamedParameterJdbcTemplate`による動的SELECT/COUNT文の組み立て・実行（ページング・構造化フィルタ・SQL手入力併用） |
| `RecordBatchService` | `cherry.mastermeister.masterdata` | 一括反映（作成・更新・削除混在）のオールオアナッシング制御 |

新規の値オブジェクト（`cherry.mastermeister.masterdata.model`）: `ColumnDataTypeCategory`, `FilterOperator`, `OperationType`, `AccessibleConnection`, `AccessibleTable`, `RecordColumn`, `RecordFilterCondition`, `RecordPage`, `BatchOperationItem`, `BatchOperationResult`, `BatchOperationItemResult`。

新規例外（`cherry.mastermeister.common.exception`）: `InvalidQueryConditionException`（VALIDATION_ERROR系、400）, `BatchSizeExceededException`（400）。いずれも`ApiException`のサブクラスであり、`GlobalExceptionHandler`の既存の汎用`@ExceptionHandler(ApiException.class)`がそのまま処理するため、個別ハンドラの追加は不要だった（logical-components.md/nfr-design-patterns.mdの想定からの簡略化）。

## 実装時の設計訂正（Part 1計画からの変更点）

### 1. ColumnDataTypeMapperの入力源
当初計画（logical-components.md §1、NFR Design Q4=A）では、UNIT-03の`SchemaColumn`が保持するJDBC型情報（`java.sql.Types`相当）から`ColumnDataTypeCategory`を導出する想定だった。しかし実装着手時に、`SchemaColumn.normalizedType`（`NormalizedType`: STRING/NUMBER/DATE_TIME/BOOLEAN/BINARY/OTHER）が既にJDBC型情報を正規化した分類として保持済みであることが判明したため、生のJDBC型情報を再解析するのではなく、この既存の正規化結果を単純にマッピングする方式とした。UNIT-03のエンティティ自体への変更はなく（モジュール境界は維持）、観測可能な`ColumnDataTypeCategory`の値自体にも影響はない。マッピングは `NUMBER→NUMERIC`, `DATE_TIME→DATETIME`, `BOOLEAN→BOOLEAN`, `STRING/BINARY/OTHER→STRING`（BR-MASTER-05に存在しない型は最も制約の緩いSTRING扱いにフォールバック）。

### 2. 識別子クオートの新規追加（`RdbmsDialectStrategy.quoteIdentifier()`）
動的に組み立てるSQL（スキーマ名・テーブル名・カラム名）の識別子エスケープについて、既存の4方言実装に`quoteIdentifier(String)`のデフォルトメソック（ANSI標準のダブルクオート）を追加し、MySQL/MariaDBの2方言はバッククオートへオーバーライドした。NFR Design時点では明示的に検討されていなかった実装上の必要事項（MySQL/MariaDBは既定でバッククオートを用いるため、ダブルクオートのままでは方言によって動作しない）。

### 3. RawQueryConditionValidatorの実装方式
nfr-design-patterns.md §3.1では「ExpressionVisitorで許可構文要素のみか検証する」としていたが、JSqlParserの`ExpressionVisitor<T>`インタフェースは数十個のvisitメソッドを持つため、Java 25のパターンマッチングswitch文による再帰的な検証・再構築（許可要素のみをcase節で列挙し、それ以外はdefault節で一律拒否するフェイルクローズなアローリスト方式）で代替した。設計意図（許可要素のみのアローリスト、既定拒否）は同一であり、観測可能な検証結果に差異はない。

### 4. コメント記号・複数ステートメント区切りの明示的拒否
JSqlParserによる構文解析・再構築のアプローチでは、コメント記号（`--`、`/* */`）はパース時に自動的に除去され、再構築後のSQLには含まれ得ないため、設計上は安全性が担保されている。しかしBR-MASTER-04が明示的に「検出した場合は入力全体を拒否する」ことを求めているため、パース前に生の入力文字列へ`--`/`/*`/`*/`/`;`の含有チェックを追加し、要件どおり明示的に拒否するようにした。

### 5. MASTER_DATA_BULK_ACCESSED / MASTER_DATA_BATCH_APPLIEDの記録タイミング
`RecordQueryService`/`RecordBatchService`は純粋なデータアクセス層とし、監査イベントの発行判断（閾値比較、成功時のみ発行等）はStep 5で作成する`MasterDataService`（ビジネスロジック層）に集約する。

## テスト結果

- `RawQueryConditionValidatorTest`: 11件成功（許可構文の受理、サブクエリ・関数呼び出し・コメント記号・複数ステートメント・構文エラーの拒否、ORDER BYの列+ASC/DESC限定、負数リテラル）
- `RecordQueryServiceTest`: 8件成功（H2インメモリ実テーブル。全件取得・構造化フィルタ・LIKEエスケープ・ページング・構造化フィルタとSQL手入力WHERE句のAND結合・非表示カラムの除外・未知カラム参照時のエラー・SQL手入力ORDER BYの優先）
- `RecordBatchServiceTest`: 7件成功（H2インメモリ実テーブル。全件成功時のコミット、制約違反時の全件ロールバック実証、作成/更新/削除それぞれの権限拒否、主キー欠落、対象行不存在）
- `RdbmsDialectStrategyTest`: `quoteIdentifier()`のテストケースを追加（4方言）

`./gradlew :backend:compileJava :backend:compileTestJava` および対象テストクラスの実行はいずれも成功。

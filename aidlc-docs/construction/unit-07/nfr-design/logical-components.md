# UNIT-07 クエリビルダー - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.querybuilder`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。

---

## 1. クエリビルダードメイン（`cherry.mastermeister.querybuilder`）

### QueryBuilderController（Q5=A）
単一のControllerに3エンドポイントをまとめる。
- `GET /api/query-builder/{connectionId}/tables?schemaName=...` — アクセス可能テーブル/カラム一覧（`QueryBuilderAccessResolver`に委譲）
- `POST /api/query-builder/{connectionId}/generate` — SQL生成（`QueryBuilderService.generateSql`）
- `POST /api/query-builder/{connectionId}/parse` — リバースエンジニアリング（`QueryBuilderService.parseToBuilderState`）

接続一覧・スキーマ一覧はUNIT-06の既存エンドポイント（`GET /api/queries/connections`・`GET /api/queries/{connectionId}/schemas`）を再利用するため、`QueryBuilderController`には含めない（frontend-components.mdのとおり）。既存の`SecurityConfig`の`.requestMatchers("/api/**").authenticated()`ルールでカバーされるため、新規のSecurityFilterChainルール追加は不要。

### QueryBuilderService（COMP-16、Q4=A）
SQL生成・リバースエンジニアリングの2責務に専念する。
- `generateSql(QueryBuilderState state): String`
  - GROUP BY整合性チェック（nfr-design-patterns.md §1.1）を行い、違反時は`QueryBuilderInvalidGroupByException`を送出
  - JSqlParserの`PlainSelect`/`Table`/`Join`/`SelectItem`/`GroupByElement`/`OrderByElement`オブジェクトを`QueryBuilderState`から構築し、`.toString()`で文字列化する
  - WHERE/HAVING条件の比較値は、`buildLiteralExpression(ColumnDataTypeCategory, String)`（nfr-design-patterns.md §2.1）で型安全なリテラルオブジェクトに変換し、`BinaryExpression`の右辺として組み込む。変換不能な入力は`QueryBuilderInvalidLiteralException`を送出
- `parseToBuilderState(Long connectionId, String schemaName, String sql): QueryBuilderState`
  - `CCJSqlParserUtil.parseStatements(sql)`で単一`Select`文であることを確認（UNIT-05/06のパターン踏襲）
  - ASTを走査し、タブUIで表現可能な構造かを判定する。非対応の構文要素・条件構造・JOIN種別・集計関数を検出した場合は`QueryBuilderUnsupportedSqlException`を送出（nfr-design-patterns.md §1.2）
  - 参照するテーブル・カラムが`QueryBuilderAccessResolver`経由で確認できない（存在しない、またはREAD未満）場合は`QueryBuilderReferenceNotAccessibleException`を送出（同§1.2）
  - 上記いずれにも該当しない場合のみ、対応する`QueryBuilderState`を構築して返す

### QueryBuilderAccessResolver（Q4=A、新設・分離）
アクセス可能テーブル/カラム一覧取得ロジックを専任で担当する。`QueryBuilderService`からは利用されるが、直接Controllerからも呼び出される（一覧取得APIのため）。
- `listAccessibleTables(Long userId, Long connectionId, String schemaName): List<AccessibleBuilderTable>`
  - 事前に`schemaName`がUNIT-06の`QueryExecutionService.listAccessibleSchemas(userId, connectionId)`の結果に含まれることを確認し、含まれない場合はUNIT-06の既存例外`QuerySchemaNotAccessibleException`を送出（nfr-design-patterns.md §1.3）
  - `SchemaIntrospectionService.getSchema(connectionId)`で対象スキーマの`SchemaTable`一覧を取得
  - 各テーブルについて、UNIT-05の`MasterDataService.isTableVisible()`と同じOR条件のロジック（テーブル単位または列単位いずれかの実効主権限が非NONE）で候補判定（BR-QUERYBUILDER-01、business-logic-model.md §1）
  - 候補に残ったテーブルについて、各カラムの実効主権限がREAD以上のもののみを`AccessibleBuilderColumn`として含める
  - `isColumnAccessible(Long userId, Long connectionId, String schemaName, String tableName, String columnName): boolean`のような補助メソッドを設ける。メソッド名が示すとおり、構造メタデータ上の存在確認とREAD以上の実効権限確認の両方を1メソッドで担う（`existsTableColumn`のような存在確認のみを示唆する命名は、権限チェックの実施有無について実装者に誤解を与えるため避ける）。`QueryBuilderService.parseToBuilderState`が参照テーブル/カラムごとにこのメソッドを呼び出し、`false`が1件でもあれば`QueryBuilderReferenceNotAccessibleException`を送出する

### QueryBuilderColumnTypeMapper（Q4関連、tech-stack-decisions.md §4）
- UNIT-05の`ColumnDataTypeMapper`と同じ設計思想（UNIT-03の`SchemaColumn.normalizedType`からのマッピング）を独自実装する
- `toCategory(NormalizedType): ColumnDataTypeCategory`の1メソッドのみを提供する軽量コンポーネント

### 例外クラス（新規、いずれも`ApiException`のサブクラスとして`GlobalExceptionHandler`の汎用ハンドラで自動処理される）
- `QueryBuilderInvalidGroupByException`（400 BAD_REQUEST、BR-QUERYBUILDER-11）
- `QueryBuilderUnsupportedSqlException`（422 UNPROCESSABLE_ENTITY、BR-QUERYBUILDER-07の構文非対応系）
- `QueryBuilderReferenceNotAccessibleException`（403 FORBIDDEN、BR-QUERYBUILDER-07のアクセス権限不足系）
- `QueryBuilderInvalidLiteralException`（400 BAD_REQUEST、列のデータ型分類と入力値の不整合）

既存例外（新規追加なし、そのまま再利用）:
- UNIT-06の`QuerySchemaNotAccessibleException`（403 FORBIDDEN）

### DTO設計
- `AccessibleBuilderTableResponse`（`tableName`, `tableType`, `columns: List<AccessibleBuilderColumnResponse>`）
- `AccessibleBuilderColumnResponse`（`columnName`, `dataTypeCategory`）
- `QueryBuilderStateRequest`（`from`, `joins`, `selectItems`, `whereConditions`, `groupByColumns`, `havingConditions`, `orderByItems`, `limit`, `offset` — domain-entities.mdの各モデルに対応するリクエストDTO一式。各リストに`@Size`で件数上限を設定、tech-stack-decisions.md §5参照）
- `GenerateSqlResponse`（`sql: String`）
- `ParseSqlRequest`（`schemaName`, `sql`）
- `QueryBuilderStateResponse`（`QueryBuilderStateRequest`と対称の構造。リバースエンジニアリング結果として返却）

---

## 2. 依存関係の追加

なし。JSqlParserはUNIT-05で追加済みの依存関係を再利用する（今回、構文解析に加えAST構築による文字列生成にも使用）。

---

## 3. 設定（`AppProperties`拡張）

新規設定項目なし。リクエストサイズ上限（tech-stack-decisions.md §5）はBean Validationの`@Size`アノテーションでDTOに直接埋め込むため、`application.yml`外部化は不要と判断する（他ユニットの`bulk-access-threshold`等のような運用時調整の必要性が薄い固定的な安全上限のため）。

---

## 4. Spring Security設定の変更

なし。既存の`.requestMatchers("/api/**").authenticated()`ルールが`/api/query-builder/**`をカバーする（nfr-design-patterns.md §4、UNIT-05/06のCode Generation時の前例と同様の確認結果）。

---

## 5. 監査ログ連携

なし。本ユニットはDB更新を伴わない読み取り専用の変換処理のみであり、`AuditEventPublisher`への新規イベント発行は行わない。

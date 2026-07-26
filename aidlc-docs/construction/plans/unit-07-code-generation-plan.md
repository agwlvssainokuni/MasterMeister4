# UNIT-07 クエリビルダー - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-5.1（タブUIによるSQL組み立て）, STORY-5.2（SQL生成と実行・保存への連携、PBT対象）, STORY-5.3（既存SQLからのリバースエンジニアリング）
- **対応要件**: FR-5.1〜FR-5.7
- **対応コンポーネント**: COMP-16（`QueryBuilderService`）＋新設の`QueryBuilderAccessResolver`・`QueryBuilderColumnTypeMapper`。nfr-design/logical-components.md §1参照
- **前提ユニット**: UNIT-01（design-systemの`Tabs`コンポーネント、`queryBuilder`ナビ項目の仮予約）, UNIT-02（JWT認証、`GlobalExceptionHandler`）, UNIT-03（`SchemaIntrospectionService.getSchema`）, UNIT-04（`EffectivePermissionResolver.resolvePrimary`）, UNIT-05（JSqlParser依存関係、`ApiException`パターン、`ColumnDataTypeMapper`と同じ設計思想）, UNIT-06（`QueryExecutionService.listAccessibleConnections`/`listAccessibleSchemas`、`QuerySchemaNotAccessibleException`の再利用、`QueryExecutionPage`/`SavedQueryEditorPage`への逆遷移導線追加）
- **依存関係**: UNIT-05の`MasterDataService`には依存しない（unit-of-work.mdの前提ユニット定義どおり）
- **本ユニットが所有するデータ**: なし（BR-QUERYBUILDER-08、DB永続化エンティティを新規に持たない）
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時の実装判断

- パッケージ構成は`cherry.mastermeister.querybuilder`単一。エンティティ相当のモデルクラスはDTOと統合する（下記参照）、サービスクラスはパッケージ直下、DTOは`{package}.dto`、コントローラは`{package}.api`に配置する
- **DTO設計の簡略化（重要な実装判断）**: domain-entities.mdは`QueryBuilderState`等を「業務ロジックモデル」として定義しているが、本ユニットはDB永続化を持たず（BR-QUERYBUILDER-08）、`QueryBuilderState`はAPI（`generate`/`parse`）のリクエスト/レスポンス以外の用途で内部的に使われることがない。そのため、UNIT-05/06のような「model（内部表現）とdto（API表現）を分離する」設計は行わず、`cherry.mastermeister.querybuilder.dto`パッケージ内の1セットのクラス（`FromClauseDto`, `JoinClauseDto`, `JoinConditionDto`, `ColumnRefDto`, `SelectItemDto`, `AggregateExpressionDto`, `ConditionDto`, `OrderByItemDto`等）を、リクエスト・レスポンス両方の入れ子要素として共用する。トップレベルのみ`QueryBuilderStateRequest`（`@Size`等のBean Validation注釈付き）と`QueryBuilderStateResponse`（同一構造、注釈なし）の2クラスに分ける（nfr-design/logical-components.mdで決定済みの「対称の構造」を維持しつつ、入れ子要素の重複定義を避ける）
- SQL生成・リバースエンジニアリングは`QueryBuilderService`（`generateSql`/`parseToBuilderState`の2メソッド）、アクセス可能テーブル/カラム一覧取得は`QueryBuilderAccessResolver`（`listAccessibleTables`/`isColumnAccessible`の2メソッド）に分離する（nfr-design-patterns.md §4、Q4=A）
- 型分類マッピングは`QueryBuilderColumnTypeMapper`（UNIT-05の`ColumnDataTypeMapper`と同じ設計思想、独自実装）
- WHERE/HAVING比較値は、列のデータ型分類に応じてJSqlParserの`LongValue`/`DoubleValue`/`StringValue`/`DateValue`/`TimestampValue`/`BooleanValue`に変換し、AST上の`BinaryExpression`に組み込む（tech-stack-decisions.md §2〜3、nfr-design-patterns.md §2.1）
- 新規例外4種（`QueryBuilderInvalidGroupByException`400, `QueryBuilderUnsupportedSqlException`422, `QueryBuilderReferenceNotAccessibleException`403, `QueryBuilderInvalidLiteralException`400）は`cherry.mastermeister.common.exception`に配置する（既存パッケージ規約に合わせる）。UNIT-06の`QuerySchemaNotAccessibleException`（403）はそのままimportして再利用する
- 本ユニットもPBT対象プロパティ（business-logic-model.md §8: SQL生成/解析のラウンドトリップ、GROUP BY整合性の不変条件、アクセス可能テーブル/カラム一覧のREAD以上不変条件）を持つため、jqwikによるプロパティベーステストを実装する。ラウンドトリップPBTは、`QueryBuilderAccessResolver`をMockitoでモック化し（`isColumnAccessible`が常に`true`を返す）、純粋にSQL生成→解析の構文的往復性のみを検証する（実際のスキーマ・権限判定への依存を切り離すため）
- フロントエンドは接続選択画面→クエリビルダー画面（画面内スキーマ選択＋タブUI）の2画面構成。UNIT-01で仮予約済みの`queryBuilder`ナビ項目をそのまま使用する（navigation.tsへの変更は不要）
- UNIT-06の`QueryExecutionPage.tsx`・`SavedQueryEditorPage.tsx`に「クエリビルダーで編集」ボタンを追加する（既存ファイルの修正、frontend-components.md「逆遷移・相互遷移の実装方針」参照）
- 新規外部ライブラリ依存・Build Configuration変更なし（JSqlParserを再利用、新規`AppProperties`項目なし）。Database Migration Scriptsセクションも該当なし

## 計画チェックリスト

### 1. Business Logic Generation

- [x] Step 1.1: DTOクラス群を作成する（`cherry.mastermeister.querybuilder.dto`）: `ColumnRefDto`, `FromClauseDto`, `JoinConditionDto`, `JoinClauseDto`, `AggregateExpressionDto`, `SelectItemDto`, `ConditionDto`, `OrderByItemDto`, `QueryBuilderStateRequest`（各リストに`@Size`: selectItems=200, joins=20, whereConditions=50, groupByColumns=20, havingConditions=20, orderByItems=20）, `QueryBuilderStateResponse`, `GenerateSqlResponse`, `ParseSqlRequest`, `AccessibleBuilderTableResponse`, `AccessibleBuilderColumnResponse`（domain-entities.md §1〜7、tech-stack-decisions.md §5）— `SelectItemDto`/`ConditionDto`/`OrderByItemDto`に`@AssertTrue`でcolumn/aggregateの排他性検証を追加（実装時の判断）。**実装時の発見**: `generateSql`はDBアクセスを伴わない純粋な変換だが、比較値の型安全なリテラル変換（tech-stack-decisions.md §2）には列のデータ型分類が必要。`ConditionDto`に`dataTypeCategory`フィールドを追加（フロントエンドがテーブル/カラム一覧取得APIで既に取得済みの値をそのまま送信する）
- [x] Step 1.2: enumを作成する（`cherry.mastermeister.querybuilder.dto`）: `JoinType`（INNER/LEFT/RIGHT）, `AggregateFunction`（COUNT/SUM/AVG/MIN/MAX）, `ConditionOperator`（UNIT-05の`FilterOperator`と同じ設計思想の独自定義）, `ColumnDataTypeCategory`（NUMERIC/DATETIME/STRING/BOOLEAN）, `SortDirection`（ASC/DESC）
- [x] Step 1.3: `QueryBuilderColumnTypeMapper`（`cherry.mastermeister.querybuilder`）を作成する（`NormalizedType`から`ColumnDataTypeCategory`への1メソッドマッピング、tech-stack-decisions.md §4）
- [x] Step 1.4: 新規例外を作成する: `QueryBuilderInvalidGroupByException`（400）, `QueryBuilderUnsupportedSqlException`（422）, `QueryBuilderReferenceNotAccessibleException`（403）, `QueryBuilderInvalidLiteralException`（400）（`cherry.mastermeister.common.exception`）— messages_ja/en.propertiesにもエラーメッセージを追加
- [x] Step 1.5: `QueryBuilderAccessResolver`（`cherry.mastermeister.querybuilder`）を作成する（`listAccessibleTables(userId, connectionId, schemaName)`: UNIT-06の`listAccessibleSchemas`でスキーマアクセス可否を確認→`SchemaIntrospectionService.getSchema`でテーブル一覧取得→UNIT-05の`isTableVisible`と同じOR条件で候補判定→列単位フィルタリング。`isColumnAccessible(userId, connectionId, schemaName, tableName, columnName)`: 構造メタデータ上の存在確認＋実効権限READ以上確認の両方を1メソッドで担う。business-logic-model.md §1、nfr-design-patterns.md §1.3・3.1）
- [x] Step 1.6: `QueryBuilderService`（`cherry.mastermeister.querybuilder`、COMP-16）を作成する
  - `generateSql(QueryBuilderStateRequest state): String` — GROUP BY整合性チェック（違反時`QueryBuilderInvalidGroupByException`）→JSqlParserの`PlainSelect`/`Table`/`Join`/`SelectItem`/`GroupByElement`/`OrderByElement`オブジェクト構築→WHERE/HAVING比較値を型安全なリテラルへ変換（変換不能時`QueryBuilderInvalidLiteralException`）→`.toString()`で文字列化（business-logic-model.md §2〜6、nfr-design-patterns.md §1.1・§2.1）。**実装時の発見**: JSqlParserの`Join`は複数の`onExpression`を単純追加すると`ON cond1 ON cond2`という不正なSQLになるため、`AndExpression`で単一の式にまとめてから追加するよう実装（ソースコードで`Join.toString()`を確認して発見）
  - `parseToBuilderState(Long userId, Long connectionId, String schemaName, String sql): QueryBuilderStateResponse` — `CCJSqlParserUtil.parseStatements`で単一`PlainSelect`文確認（`SetOperationList`＝UNION等は除外）→タブUIで表現可能な構造かをASTベースで判定（非対応時`QueryBuilderUnsupportedSqlException`）→`QueryBuilderAccessResolver.isColumnAccessible`/`listAccessibleTables`で参照テーブル/カラムの確認（アクセス不可時`QueryBuilderReferenceNotAccessibleException`）→`QueryBuilderStateResponse`構築（business-logic-model.md §7、nfr-design-patterns.md §1.2）。**実装時の発見**: `generateSql`は当初案どおりDBアクセス不要の純粋関数だが、`parseToBuilderState`はFROM/JOINのテーブル自体の存在確認が抜けていた実装ミスを自己レビューで発見し修正

### 2. Business Logic Unit Testing

- [x] Step 2.1: `QueryBuilderColumnTypeMapperTest`を作成する（NormalizedTypeの全パターン→ColumnDataTypeCategoryのマッピング確認）— 5件
- [x] Step 2.2: `QueryBuilderAccessResolverTest`を作成する（Mockito。テーブル単位・列単位いずれかが非NONEの場合の候補判定、列単位フィルタリング、スキーマアクセス不可時の`QuerySchemaNotAccessibleException`）— 7件
- [x] Step 2.3: `QueryBuilderServiceTest`を作成する（Mockito。`generateSql`: SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSETの各組み合わせでのSQL生成、GROUP BY整合性違反時の例外、型別リテラル変換（NUMERIC/DATETIME/STRING/BOOLEAN）、IS NULL等値不要演算子。`parseToBuilderState`: 対応可能なSQLの解析成功、非対応構文（サブクエリ・UNION・FULL JOIN等）・アクセス不可カラム参照時の各例外）— 26件。**実機テストで発見・修正した実装バグ3件**:
  (1) WHERE/HAVING条件の比較値をSQLへ埋め込む際、列参照の「エイリアス」（例:`t1`）をそのまま実テーブル名として`EffectivePermissionResolver`の権限チェックに渡してしまっていた。FROM/JOIN句からエイリアス→実テーブル名のマッピングを構築し解決するよう修正
  (2) JSqlParserの`LongValue(String)`・`BooleanValue(String)`は実際には値を検証しない（前者は`getValue()`遅延パース時のみ例外、後者は`Boolean.parseBoolean`が不正な値も無条件で`false`にする）ため、コンストラクタ呼び出し前に明示的な数値・真偽値検証を追加。`DateValue`は`toString()`がJDBC escape構文`{d '...'}`になり4方言への直接実行時の移植性に懸念があったため不採用とし、日時は単純な文字列リテラルとして埋め込む方式に変更（対象4方言はいずれも文字列からの暗黙変換を受け付けるため問題ない）
  (3) HAVING句で比較の左辺が集計関数（例: `COUNT(t1.id) > 5`）の場合に対応できていなかった（単純な列参照のみを想定していた）。左辺が列参照/集計関数のいずれでも解析できるよう`parseCondition`をリファクタリング
- [x] Step 2.4: `QueryBuilderServicePropertyTest`をjqwikで作成する（business-logic-model.md §8: `QueryBuilderState`→`generateSql`→`parseToBuilderState`のラウンドトリップが元の状態と構造的に等価である性質。`QueryBuilderAccessResolver`をMockでスタブ化し常にアクセス可能として扱う）— 1プロパティ、1000回試行全件成功

### 3. Business Logic Summary

- [x] Step 3.1: `aidlc-docs/construction/unit-07/code/business-logic-summary.md`を作成する（作成したサービス・DTO一覧、PBTプロパティ実装内容、テスト結果）

### 4. API Layer Generation

- [x] Step 4.1: `QueryBuilderController`（`cherry.mastermeister.querybuilder.api`）を作成する（3エンドポイント: `GET /api/query-builder/{connectionId}/tables`, `POST /api/query-builder/{connectionId}/generate`, `POST /api/query-builder/{connectionId}/parse`）
- [x] Step 4.2: `GlobalExceptionHandler`への追加要否を確認する — 確認の結果、Step 1.4の4例外は`ApiException`のサブクラスであり既存の汎用`@ExceptionHandler(ApiException.class)`で処理されるため追加不要と判明（UNIT-05/06と同じ結論）
- [x] Step 4.3: SecurityFilterChain設定への`/api/query-builder/**`ルール追加要否を確認する — `SecurityConfig`を確認した結果、既存の`/api/admin/**`（ADMIN限定）の次の`/api/**`→`authenticated()`という汎用ルールがそのまま適用されるため、新規ルール追加は不要と判明（UNIT-05/06と同じ結論）
- [x] Step 4.4: OpenAPI/Swagger UIへの反映を確認する（既存の自動生成のみ、追加実装不要。Step 12の起動確認で最終確認）

### 5. API Layer Unit Testing

- [x] Step 5.1: `@WebMvcTest`で`QueryBuilderControllerTest`を作成する（テーブル/カラム一覧取得、SQL生成・リバースエンジニアリングの正常系・異常系（各例外のHTTPステータス確認）、Bean Validation違反時の400応答）— 8件全件成功

### 6. API Layer Summary

- [x] Step 6.1: `aidlc-docs/construction/unit-07/code/api-layer-summary.md`を作成する（エンドポイント一覧、テスト結果）

### 7. Frontend Components Generation

- [ ] Step 7.1: APIクライアント`frontend/src/api/queryBuilder.ts`を作成する（テーブル/カラム一覧取得・SQL生成・リバースエンジニアリングの各関数、型定義）
- [ ] Step 7.2: `QueryBuilderConnectionListPage`（`frontend/src/pages/`）を作成する（frontend-components.md 画面1）
- [ ] Step 7.3: タブUIの共有サブコンポーネント群（`frontend/src/pages/`または専用ディレクトリ）を作成する: `SelectItemBuilder`, `FromTableBuilder`, `JoinBuilder`, `ConditionListBuilder`（WHERE/HAVING共通）, `ColumnListBuilder`（GROUP BY用）, `OrderByListBuilder`, `LimitOffsetInput`（frontend-components.md 画面2のタブ内訳）
- [ ] Step 7.4: `QueryBuilderPage`（`frontend/src/pages/`）を作成する（frontend-components.md 画面2。design-system既存の`Tabs`コンポーネントで8タブを構成、スキーマセレクタ、SQLプレビュー（デバウンス付き`generate`呼び出し）、「保存へ」「実行へ」ボタン、router state経由の初期SQLリバースエンジニアリング対応）
- [ ] Step 7.5: `App.tsx`のルーティングに`/query-builder`, `/query-builder/:connectionId`を追加する（`ProtectedRoute`配下）
- [ ] Step 7.6: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'queryBuilder'`を追加する
- [ ] Step 7.7: i18nリソース（`common.json`の`ja`/`en`）に`queryBuilder.*`関連キー（タブ名・ボタンラベル・エラーメッセージ等）を追加する（`nav.queryBuilder`はUNIT-01で追加済みのためdesign-system.jsonへの追加は不要）
- [ ] Step 7.8: UNIT-06の`QueryExecutionPage.tsx`に「クエリビルダーで編集」ボタンを追加する（既存ファイルの修正、frontend-components.md「逆遷移・相互遷移の実装方針」1）
- [ ] Step 7.9: UNIT-06の`SavedQueryEditorPage.tsx`に「クエリビルダーで編集」ボタンを追加する（既存ファイルの修正、new/editモード双方。同2〜3）

### 8. Frontend Components Unit Testing

- [ ] Step 8.1: `queryBuilder.test.ts`（APIクライアント）を作成する
- [ ] Step 8.2: `QueryBuilderConnectionListPage.test.tsx`を作成する
- [ ] Step 8.3: 各タブサブコンポーネントのテストを作成する（`SelectItemBuilder`, `FromTableBuilder`, `JoinBuilder`, `ConditionListBuilder`, `ColumnListBuilder`, `OrderByListBuilder`）
- [ ] Step 8.4: `QueryBuilderPage.test.tsx`を作成する（タブ切替、SQLプレビュー更新、保存/実行への遷移、逆遷移時のプレフィル）
- [ ] Step 8.5: `QueryExecutionPage.test.tsx`・`SavedQueryEditorPage.test.tsx`に、追加した「クエリビルダーで編集」ボタンのテストケースを追加する（既存ファイルの修正）
- [ ] Step 8.6: `HomePage.test.tsx`の実装済みバッジ数の変化を反映する

### 9. Frontend Components Summary

- [ ] Step 9.1: `aidlc-docs/construction/unit-07/code/frontend-summary.md`を作成する（作成した画面・コンポーネント一覧、テスト結果）

### 10. Documentation Generation

- [ ] Step 10.1: `backend/README.md`を更新する（UNIT-07概要: クエリビルダー、`/api/query-builder/*`エンドポイント）
- [ ] Step 10.2: `frontend/README.md`を更新する（UNIT-07の新規画面をpages概要に追記）

### 11. Deployment Artifacts

- [ ] Step 11.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に追加のインフラが不要であることを確認する

### 12. 最終ビルド検証

- [ ] Step 12.1: **検証チェックポイント**: `./gradlew :backend:build`（jqwikプロパティテスト含む）、`npm test`（frontend）、`npm run build`（frontend）が全件成功することを確認する
- [ ] Step 12.2: devenv（PostgreSQL・MySQL）に対し実アプリで、一般ユーザとして接続選択→スキーマ選択→タブUIでのクエリ組み立て→SQL生成→（UNIT-06連携）保存・実行、および逆方向（クエリ実行画面・保存クエリ編集画面からのクエリビルダーへの遷移とリバースエンジニアリング反映）を実機E2E検証する。BOOLEAN型リテラルの4方言での動作（tech-stack-decisions.md §2で未検証と明記した事項）もここで確認する
- [ ] Step 12.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）はUNIT-02〜06と同じくNVD APIキー未設定のため実施見送り（既知の制約として記録）

## Story Traceability

- STORY-5.1（タブUIによるSQL組み立て） — Step 1.1〜1.2, 7.3〜7.4
- STORY-5.2（SQL生成と実行・保存への連携、PBT対象） — Step 1.6, 2.4, 7.4, 7.8〜7.9
- STORY-5.3（既存SQLからのリバースエンジニアリング） — Step 1.6, 2.3〜2.4, 7.4, 7.8〜7.9

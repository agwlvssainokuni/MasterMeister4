# UNIT-07 クエリビルダー - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（比較値のリテラル埋め込み＋JSqlParser Expression APIによる型安全な埋め込み、SQL生成/解析のJSqlParser ASTベース実装、独自の`ColumnDataTypeCategory`マッピング、リクエスト件数上限、既存Caffeineキャッシュの再利用）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## Scalability Patterns（前提確認）
requirements.mdの前提（同時利用者約10名規模）により、新規のスケーリング機構は不要（N/A、UNIT-01〜06と同様）。

## Security Patterns（既に決定済みの事項の確認）
`/api/query-builder/**`は、既存の`SecurityConfig`の`.requestMatchers("/api/**").authenticated()`ルールでカバーされる（UNIT-05/06と同様、一般ユーザ向け機能でロール不問）。`/api/admin/**`のような専用ルール追加は不要と判断する（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `nfr-design-patterns.md`（エラー表現・例外設計、SQL生成/解析の詳細パターン）を作成する
- [ ] Step D: `logical-components.md`（新設する論理コンポーネント、Controller構成）を作成する
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Security Patterns、BR-QUERYBUILDER-11）
GROUP BY整合性制約（集計関数を含むSELECT句で、GROUP BYに含まれない非集計列が存在してはならない）に違反する`QueryBuilderState`が渡された場合のエラー表現は？

A) 新規`ApiException`サブクラス`QueryBuilderInvalidGroupByException`（400 BAD_REQUEST）を新設する（UNIT-05/06の1業務ルール1例外クラスの既存方針を踏襲）

B) 専用例外は設けず、Bean Validationの`@AssertTrue`等でリクエストDTOレベルの検証に統合する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 2（Security Patterns、BR-QUERYBUILDER-07、重要）
リバースエンジニアリング失敗時のエラー表現は？BR-QUERYBUILDER-07は「タブUIで表現できない構文要素（サブクエリ・UNION・FULL JOIN等）」と「参照テーブル/カラムへのアクセス権限不足」という、性質の異なる2種類の失敗要因を列挙している。

A) 失敗要因を2種に区別し、それぞれ専用の`ApiException`サブクラスを新設する: `QueryBuilderUnsupportedSqlException`（422 UNPROCESSABLE_ENTITY、構文的に非対応）と`QueryBuilderReferenceNotAccessibleException`（403 FORBIDDEN、アクセス権限不足）。フロントエンドはエラー種別に応じてメッセージを出し分けられる（例:「この構文はビルダーで編集できません」/「アクセス権限のないテーブル・カラムを参照しています」）

B) 単一の`QueryBuilderReverseEngineeringFailedException`（例: 422で統一）にまとめ、詳細はメッセージ文字列のみで区別する。実装がシンプルになるが、フロントエンドでの出し分けはメッセージ文字列のパースが必要になる

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 3（Security Patterns、business-logic-model.md §1・BR-QUERYBUILDER-01）
テーブル/カラム一覧取得（`GET /api/query-builder/{connectionId}/tables`）で、指定された`schemaName`がアクセス可能なスキーマ一覧（UNIT-06の`listAccessibleSchemas`基準）に含まれない場合のエラー表現は？

A) UNIT-06の既存`QuerySchemaNotAccessibleException`（403 FORBIDDEN）をそのままimportして再利用する。本ユニットは元々UNIT-06のスキーマ許可リスト判定をそのまま利用する方針（business-logic-model.md §1）のため、同一の失敗モードに対して新規の例外クラスを重複定義しない

B) 本ユニット独自の例外クラスを新設する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 4（Logical Components、重要）
アクセス可能テーブル/カラム一覧取得ロジック（`SchemaIntrospectionService`＋`EffectivePermissionResolver`＋独自の型分類マッパーの組み合わせ）の配置は？

A) `QueryBuilderService`（SQL生成・リバースエンジニアリングの2責務）とは別に、専用クラス（例: `QueryBuilderAccessResolver`）を新設し、テーブル/カラム一覧取得ロジックのみを担当させる。UNIT-06がQuerySqlAnalyzer（構文解析専任）とQueryExecutionService/SavedQueryService（業務サービス）を分離した前例を踏襲する

B) `QueryBuilderService`に3つ目のメソッドとして統合する（Application Design時点のCOMP-16定義は`generateSql`/`parseToBuilderState`の2メソッドのみだったが、実装時に1クラスへ集約する）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 5（Logical Components）
Controller構成は？

A) 単一の`QueryBuilderController`に3エンドポイント（テーブル/カラム一覧取得・SQL生成・リバースエンジニアリング）をまとめる（UNIT-05のMasterDataController、UNIT-06のQueryControllerと同程度の規模）

B) 複数のControllerに分割する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

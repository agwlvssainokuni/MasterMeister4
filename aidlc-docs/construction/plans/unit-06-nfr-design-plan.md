# UNIT-06 クエリ保存・実行 - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（`SingleConnectionDataSource`による接続管理、サブクエリラップ方式のページング、JDBC標準`setQueryTimeout`、結果件数上限10,000件、`EffectivePermissionResolver.resolvePrimary`ループ呼び出し）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## Scalability Patterns（前提確認）
requirements.mdの前提（同時利用者約10名規模）により、新規のスケーリング機構は不要（N/A）。クエリ実行のたびに`DataSource.getConnection()`で既存のHikariCPプール（UNIT-03既存）から接続を取得し、処理完了後に返却する方式（NFR Requirements Q1=A）のため、追加のプーリング・キャッシュ機構は導入しない。

## Resilience Patterns（前提確認）
クエリタイムアウト（NFR Requirements Q3=A）・結果件数上限（Q4=A）は既に決定済み。本ステージでは、これらの制約に抵触した場合の具体的なエラー表現・例外変換方式をQ4で確定する。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全7問、AI推奨どおりQ1〜6=A、Q7=Bで確定 2026-07-25T00:40:00Z）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 曖昧な回答なし
- [x] Step C: `nfr-design-patterns.md`（レジリエンス・パフォーマンス・セキュリティの設計パターン）を作成する（2026-07-25T00:45:00Z）
- [x] Step D: `logical-components.md`（新設する論理コンポーネント、データ設計上の注意点等）を作成する（2026-07-25T00:45:00Z）
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Security Patterns、BR-QUERY-02、重要）
実行対象スキーマが許可リストに含まれない場合のエラー表現は？

A) 新規`ApiException`サブクラス`QuerySchemaNotAccessibleException`（403 FORBIDDEN）を新設する。UNIT-05の`MasterDataTableNotAccessibleException`（404、テーブルの存在自体を隠すフェイルクローズ方針）とは異なり、スキーマ一覧は`GET /api/queries/{connectionId}/schemas`で既にアクセス可能な範囲のみを返却済みのため、スキーマ自体の存在を隠す必要性は薄く、403で明確にアクセス拒否を示す

B) UNIT-05と同様404 NOT_FOUNDとし、スキーマの存在自体を隠すフェイルクローズ方針を踏襲する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 2（Security Patterns、BR-QUERY-01）
SQLが単一のSELECT文でない（構文エラー・複数ステートメント・非SELECT文）場合のエラー表現は？

A) 新規`ApiException`サブクラス`NonReadOnlyQueryException`（400 BAD_REQUEST）を新設する（UNIT-05の`BatchSizeExceededException`/`InvalidQueryConditionException`と同様、1業務ルール1例外クラスの既存方針を踏襲）

B) UNIT-05の`InvalidQueryConditionException`（BR-MASTER-04用）を流用する（対象の業務ルールは異なるが、400+入力検証エラーという性質は共通のため）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Security Patterns、BR-QUERY-09）
保存クエリへのアクセス不可（非表示化済み・Privateで作成者以外等）の場合のエラー表現は？

A) 新規`ApiException`サブクラス`SavedQueryNotAccessibleException`（404 NOT_FOUND）を新設する。UNIT-05の`MasterDataTableNotAccessibleException`と同じフェイルクローズ方針（存在有無とアクセス権限有無を区別しない）を踏襲する

B) 403 FORBIDDENとし、保存クエリの存在自体は示しつつアクセス権のみ拒否する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4（Resilience Patterns・Security Requirements、NFR Requirements Q3〜Q4）
クエリ実行タイムアウト（`SQLTimeoutException`）・結果件数上限超過の例外処理は？

A) それぞれ専用の`ApiException`サブクラス（`QueryExecutionTimeoutException`＝408 REQUEST_TIMEOUT、`QueryResultSizeExceededException`＝400 BAD_REQUEST、UNIT-05の`BatchSizeExceededException`と同様の構成）を新設し、`SQLTimeoutException`を明示的にcatchして変換する。既存の汎用`Exception.class`ハンドラ（500 INTERNAL_SERVER_ERROR、ログ出力のみ）に落ちないようにし、クライアントに意味のあるエラーを返す

B) 明示的な変換は行わず、`SQLTimeoutException`は既存の汎用`Exception.class`ハンドラ（500）に委ねる。結果件数上限超過も同様に、実装上例外的な500として扱う

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Performance Patterns、tech-stack-decisions.md §2〜4）
ページング有効時、総件数（COUNT）取得と結果取得（LIMIT/OFFSET）を、どのように実行しますか？

A) NFR RequirementsのQ1で決定した同一の物理接続（`SingleConnectionDataSource`でラップ済み）上で、同一の`NamedParameterJdbcTemplate`インスタンスを使い、COUNTクエリ→結果取得クエリの順に2回実行する。スキーマ切替（`applySchemaSwitch`）は接続確立時に1回のみ実施すればよい。読み取り専用のため、2クエリ間で同一トランザクションスナップショットである必要はないと判断する（UNIT-05の`RecordQueryService`も同一`jdbcTemplate`でCOUNT・結果取得の両方を実行する方式を踏襲）

B) COUNT取得と結果取得を、それぞれ独立した物理接続（`DataSource.getConnection()`を2回呼び出す）で行う。スキーマ切替も2回実施することになるが、処理としての独立性が高い

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Logical Components、STORY-7.1、BR-QUERY-01〜§2）
SQL読み取り専用検証（BR-QUERY-01）とパラメータ検出（§2、`JdbcNamedParameter`収集）を担うクラス構成は？

A) 新規クラス`QuerySqlAnalyzer`を`query`パッケージに新設し、1回のJSqlParser構文解析結果（`Select`文のAST）を保持したうえで、読み取り専用検証とパラメータ検出の両方のメソッドを提供する（1回の解析結果を再利用でき、二重解析を避けられる）

B) 検証用（`ReadOnlyQueryValidator`）とパラメータ検出用（`QueryParameterDetector`）を別クラスに分割する（UNIT-05の`RawQueryConditionValidator`/`ColumnDataTypeMapper`のような役割分担に近いが、本ユニットは同一のASTを両方の用途で使うため、分割すると呼び出し側で2回構文解析することになりやすい）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7（Logical Components）
`query`パッケージのController構成は？

A) 単一の`QueryController`に、接続一覧・スキーマ一覧・ad-hoc実行・保存クエリCRUD・保存クエリ実行・非表示化の全9エンドポイントをまとめる（UNIT-05のMasterDataController、4エンドポイントの前例を踏襲するが、本ユニットはやや規模が大きい）

B) `QueryController`（接続一覧・スキーマ一覧・ad-hoc実行の3エンドポイント、COMP-14 QueryExecutionServiceに対応）と`SavedQueryController`（保存クエリの一覧・取得・作成・更新・実行・非表示化の6エンドポイント、COMP-15 SavedQueryServiceに対応）の2つに分割する。Application Designで定義済みのサービス境界（COMP-14/15）とController構成を一致させる

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

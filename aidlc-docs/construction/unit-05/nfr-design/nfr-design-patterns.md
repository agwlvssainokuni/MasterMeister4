# UNIT-05 マスタメンテナンス - NFR Design Patterns

`unit-05-nfr-design-plan.md`の回答（全問A）に基づく実装パターンを記載する。

---

## 1. Resilience

### 1.1 一括反映のオールオアナッシング検証手順（Q1=A、Q2=A、BR-MASTER-07）
- 権限チェック（`canCreate`/`canDelete`/実効主権限、UNIT-04の判定式）は、DB反映前にバッチ全件について事前検証する。1件でも権限不足があれば、DBへは一切アクセスせず、その時点で拒否する
- 権限チェックを通過した場合、対象接続の`DataSourceTransactionManager`が管理する単一トランザクション内で、バッチ内の各操作を**個別のSQL文として順次実行**する（複数行をまとめた1つのSQL文にはしない）
- いずれかの操作でDB例外（NOT NULL制約・一意制約・外部キー制約違反等）が発生した場合、その操作のバッチ内インデックスを記録した上で、トランザクション全体をロールバックする（UNIT-04のYAML importで実際に発生したHibernateフラッシュ順序起因の制約違反バグの教訓を踏まえ、DB制約チェックは事前シミュレーションに頼らず、実際の実行結果に委ねる）
- 権限チェック段階の失敗、DB例外段階の失敗のいずれも、レスポンスには失敗した操作のインデックスと失敗理由（`PERMISSION_DENIED`/`CONSTRAINT_VIOLATION`/`INVALID_VALUE`）を含める（BR-MASTER-07）

### 1.2 SQL構文検証拒否時のエラーハンドリング（Q3=A、BR-MASTER-04）
- JSqlParserによる構文解析・検証（`RawQueryConditionValidator`等、§2 logical-components.md参照）が、許可されない構文要素を検出した場合、新規例外`InvalidQueryConditionException`を送出する
- `GlobalExceptionHandler`（UNIT-02）に`@ExceptionHandler(InvalidQueryConditionException.class)`を追加し、既存の`VALIDATION_ERROR`（400）レスポンスにマッピングする（UNIT-04で追加した`MissingServletRequestParameterException`等のハンドラと同じパターン）

### 1.3 実効権限判定の安全側デフォルト（既存決定の継続適用、SECURITY-15）
- 本ユニットは`EffectivePermissionResolver`（UNIT-04）をそのまま利用する。判定対象の設定が一切存在しない場合はフェイルクローズ（`NONE`/`false`）となる既存の挙動をそのまま引き継ぐ
- レコード一覧・一括反映のいずれも、実効権限判定の結果を都度参照するのみで、本ユニット独自のフォールバック処理は追加しない

---

## 2. Performance

### 2.1 動的レコードアクセスとトランザクション制御（Q7=A、NFR-05-06/09）
- レコード一覧取得（読み取りのみ）は、`RdbmsConnectionService.getDataSource(connectionId)`から得た`DataSource`を使い`NamedParameterJdbcTemplate`で都度クエリを実行する。トランザクション制御は行わない（単一SELECT文のため不要）
- 一括反映（書き込み）は、リクエストごとに`new DataSourceTransactionManager(dataSource)`を生成し、`TransactionTemplate`でトランザクション境界を制御する（インスタンスのキャッシュは行わない。`HikariDataSource`自体は引き続き`RdbmsConnectionService`側でキャッシュされているため、`DataSourceTransactionManager`の生成コスト自体は軽微）
- `NamedParameterJdbcTemplate`は、`TransactionTemplate`のコールバック内で実行することで、Springのトランザクション同期（`DataSourceUtils`）を通じて同一コネクション・同一トランザクションに参加する

### 2.2 バッチ上限・ページング（NFR-05-04/05）
- 一括反映バッチの合計操作件数が`AppProperties`の設定値（デフォルト1,000件）を超える場合、DBアクセス前にリクエストレベルで拒否する（400エラー）
- レコード一覧のページングはオフセットベース。総件数取得のCOUNTクエリは、絞込条件（構造化フィルタ＋SQL手入力のWHERE句、BR-MASTER-15）を適用した上で毎回実行する

---

## 3. Security

### 3.1 SQL手入力の構文検証（Q3=A、BR-MASTER-04、SECURITY-05）
- WHERE句・ORDER BY句の手入力は、`SELECT * FROM (対象テーブル) WHERE (入力WHERE句) ORDER BY (入力ORDER BY句)`という形の**ダミーSELECT文に埋め込んでJSqlParserでパース**し、`PlainSelect`から`Where`式・`OrderByElement`のみを取り出して検証する（JSqlParserは断片ではなく完全な文の構文解析を前提とするAPI形状のため）
- 取り出した式をVisitorパターン（JSqlParserの`ExpressionVisitor`）で走査し、許可された構文要素（比較演算子・`AND`/`OR`・カラム参照・リテラル値。ORDER BYは`Column`＋`ASC`/`DESC`）以外（サブクエリ、関数呼び出し、複数ステートメント等）を検出した場合は`InvalidQueryConditionException`を送出する
- 検証を通過した構文木は、そのままSQL文字列化して`NamedParameterJdbcTemplate`に渡すのではなく、リテラル値をバインドパラメータに置き換えて再構築する（構文木を走査しながら、リテラル値を`:whereParam0`等の名前付きパラメータに差し替え、対応する値をパラメータマップに追加する）

### 3.2 `/api/master-data/**`のアクセス制御（Q5=A、SECURITY-08）
- UNIT-02のSecurityFilterChain設定に、`/api/master-data/**`を対象とする新規ルールを追加する: 認証済み（JWT検証を通過し、`APPROVED`状態のユーザ）であればロールを問わず許可する（`ADMIN`/一般ユーザいずれも許可）
- `/api/admin/**`（管理者ロール必須）とは別の認可ルールとして、既存のSecurityFilterChain設定に追記する形で実装する

### 3.3 監査ログとの連携（既存決定の継続適用、SECURITY-13）
- レコード一覧取得（閾値超過時）・一括反映成功時は、domain-entities.md §9で定義した2種のイベント種別（`MASTER_DATA_BULK_ACCESSED`/`MASTER_DATA_BATCH_APPLIED`）でAuditEventPublisher（UNIT-02）経由の記録を行う

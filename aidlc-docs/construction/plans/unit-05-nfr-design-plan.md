# UNIT-05 マスタメンテナンス - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（JSqlParserによるSQL手入力検証、`RdbmsConnectionService.getDataSource()`＋`NamedParameterJdbcTemplate`、一括反映は接続ごとの`DataSourceTransactionManager`＋`TransactionTemplate`で明示的トランザクション制御、一括反映バッチ上限1,000件、監査ログ閾値の`application.yml`設定化）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全8問、AI推奨どおり全問Aで確定 2026-07-24T10:30:00Z）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 曖昧な回答なし
- [x] Step C: `nfr-design-patterns.md`（レジリエンス・パフォーマンス・セキュリティの設計パターン）を作成する
- [x] Step D: `logical-components.md`（新設する論理コンポーネント、データ設計上の注意点等）を作成する
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Resilience Patterns・重要な設計上の注意点、BR-MASTER-07）
一括反映のオールオアナッシング（BR-MASTER-07）を実現する具体的な検証手順は？

**背景**: UNIT-04のYAML importでは「検証を全件通過してからDB反映」という2段階方式を採ったが、そのCode Generation時に、Hibernateのフラッシュ順序に起因する実際の制約違反がDB反映段階で初めて表面化するというバグが見つかった（事前検証だけでは検出しきれないケースがあった）。本ユニットの一括反映も、NOT NULL・一意制約等のDB制約違反は実際にSQLを実行してみないと確実には分からない。

A) 権限チェック（`canCreate`/`canDelete`/実効主権限）はDB反映前に全件事前検証する。その上で、`DataSourceTransactionManager`が管理する単一トランザクション内で全操作のSQLを実行し、いずれかでDB例外（制約違反等）が発生した場合はトランザクション全体をロールバックし、失敗した操作を含めて全操作が「未反映」として扱われる（権限チェックとDB制約チェックを分離し、後者は実際の実行結果に委ねる）

B) 権限チェック・制約チェックの両方を、実際にDBへ反映する前にアプリケーション側で完全にシミュレーションして検証する（DB例外の発生を待たない）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 2（Resilience Patterns、BR-MASTER-07）
Question 1でA案の場合、バッチ内の一部操作でDB例外（制約違反）が発生した場合、どの操作が失敗したかをどう特定しますか？

A) 各操作を個別のSQL文として順次実行し、例外発生時点でその操作のインデックスを記録した上でトランザクション全体をロールバックする（失敗理由の特定と、オールオアナッシングの両方を満たす）

B) バッチ全体を1つのSQL（複数行INSERT等）にまとめて実行し、失敗時はバッチ全体の失敗としてのみ扱う（どの行が原因かは特定しない）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Logical Components、BR-MASTER-04）
JSqlParserによるSQL構文検証が入力を拒否した場合のエラー表現は？

A) 新規例外（例: `InvalidQueryConditionException`）を定義し、UNIT-02の`GlobalExceptionHandler`に`@ExceptionHandler`を追加してVALIDATION_ERROR（400）にマッピングする（既存の例外処理パターンを踏襲）

B) 既存の`MethodArgumentNotValidException`（Bean Validation）に無理に統合する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4（Logical Components）
`RecordColumn.dataTypeCategory`（`NUMERIC`/`DATETIME`/`STRING`/`BOOLEAN`、BR-MASTER-05のフィルタ演算子決定に使用）は、どこでJDBC型情報から導出しますか？

A) `masterdata`パッケージ内に新規のマッパー（例: `ColumnDataTypeMapper`）を用意し、UNIT-03の`SchemaColumn`が保持するJDBC型情報（`java.sql.Types`相当）から`ColumnDataTypeCategory`への変換を行う

B) UNIT-03の`SchemaColumn`エンティティ自体に`dataTypeCategory`フィールドを追加する（UNIT-03への変更が必要）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Security Patterns、SECURITY-08）
新設する一般ユーザ向けAPI（`/api/master-data/**`）のアクセス制御実装は？

**背景**: 既存の`/api/admin/**`はADMINロール専用のSecurityFilterChainルールが適用されているが、`/api/master-data/**`は初の非管理者向けトップレベル名前空間であり、既存ルールではカバーされない。

A) UNIT-02のSecurityFilterChain設定に、`/api/master-data/**`は認証済み（`APPROVED`状態）であればロールを問わず許可する新規ルールを追加する

B) `/api/admin/**`と同様、ADMINロール専用とする（一般ユーザがアクセスできなくなるため本ユニットの要件と矛盾するが選択肢として提示）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Performance Patterns）
一括反映バッチの件数上限（1,000件、NFR-05-05）・監査ログ閾値（100件、NFR-05-10）の設定値は、どこにどう定義しますか？

A) `AppProperties`に新規ネストプロパティ（例: `mm.app.masterdata.batch-max-size`（デフォルト1000）, `mm.app.audit.bulk-access-threshold`（デフォルト100））を追加する（UNIT-02/03と同じ設定値管理方式）

B) `masterdata`パッケージ内に本ユニット専用の設定クラスを別途新設する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7（Logical Components・Resilience Patterns）
`DataSourceTransactionManager`は、一括反映のリクエストごとに毎回新規生成しますか、それとも`RdbmsConnectionService`の`HikariDataSource`キャッシュと同様に接続ID単位でキャッシュしますか？

A) リクエストごとに`new DataSourceTransactionManager(dataSource)`を生成する（`DataSourceTransactionManager`は軽量なラッパーでありキャッシュの必要性が薄い。`HikariDataSource`自体は`RdbmsConnectionService.getDataSource()`で引き続きキャッシュされる）

B) `HikariDataSource`と同様、接続ID単位で`DataSourceTransactionManager`インスタンスをキャッシュする

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 8（Logical Components）
`masterdata`パッケージのController構成は？

A) 単一の`MasterDataController`に、アクセス可能接続一覧・テーブル/ビュー一覧・レコード一覧・一括反映の全エンドポイントをまとめる（本ユニットのAPIサーフェスは比較的小さいため）

B) リソース単位で複数のControllerに分割する（例: `MasterDataConnectionController`, `MasterDataTableController`, `MasterDataRecordController`）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

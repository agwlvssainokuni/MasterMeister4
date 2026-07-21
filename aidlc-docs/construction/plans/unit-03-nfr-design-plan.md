# UNIT-03 RDBMSセットアップ - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（AES-256-GCM+鍵ローテーション、TLSデフォルト無効、Bean Validation、HikariCP動的DataSourceキャッシュ、JDBCドライバ4種、60秒タイムアウト等）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 曖昧な回答なし、追加質問不要
- [x] Step C: `nfr-design-patterns.md`（レジリエンス・パフォーマンス・セキュリティの設計パターン）を作成する
- [x] Step D: `logical-components.md`（新設する論理コンポーネント、AppProperties拡張等）を作成する
- [x] Step E: 完了メッセージを提示し、承認を得る — 承認済み（2026-07-21T02:00:00Z。2回のレビュー対応: タイムアウト時のConnection強制中断・同時更新リスク許容・鍵重複検証／JDBC URL構築の方言別責務化、を経て承認）

## 質問

### Question 1（Security Patterns）
接続パスワードの暗号化・復号ロジックは、どこに実装しますか？

A) `rdbmsconnection`パッケージ内に閉じて実装する（本ユニット専用のロジックであり、他に可逆暗号化を必要とするユニットは現時点でないため。ユーザ登録・認証（UNIT-02）はパスワードを一方向ハッシュ化するのみで、可逆暗号化とは要件が異なる）

B) 新規`common.crypto`パッケージに切り出し、横断的なユーティリティとして実装する（将来的な再利用を見込む）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 2（Resilience Patterns）
スキーマ取込の全体タイムアウト（60秒、NFR-03-03）は、どう実装しますか？

A) `CompletableFuture.supplyAsync(...).orTimeout(60, TimeUnit.SECONDS)`によるタイムアウト制御

B) 専用の`ExecutorService`を用意し、`Future.get(60, TimeUnit.SECONDS)`で制御する

C) JDBCの`Statement.setQueryTimeout()`等、JDBCドライバ側のタイムアウト機構のみに頼り、アプリケーション側での追加のタイムアウト制御は行わない

D) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Resilience Patterns）
接続パスワード暗号鍵（`MM_APP_RDBMS_ENCRYPTION_KEYS`）が未設定・不正フォーマットの場合の起動時挙動は？

A) アプリケーション起動時の検証（`AppProperties`のコンストラクタ検証）で失敗させる（`MM_APP_JWT_SECRET`と同様のfail-fastパターン）

B) 起動は許可し、暗号化が必要な操作（接続登録・更新等）の実行時にエラーとする

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4（Logical Components）
HikariCPの`DataSource`キャッシュ（Q5=A、NFR Requirements）の実装配置は？

A) `RdbmsConnectionService`内部に保持する（Application Design時点でCOMP-07の`getDataSource(connectionId): DataSource`メソッドとして既に定義済みのため、独立コンポーネント化はせずこのメソッドの内部実装として持つ）

B) 独立した新規コンポーネント（例: `DataSourceRegistry`）に分離する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Security Patterns）
管理者専用エンドポイント（`/api/admin/rdbms-connections/**`）のアクセス制御実装は？

A) UNIT-02で確立済みのSecurityFilterChain設定（`/api/admin/**`パスパターンでのロールチェック）にそのまま含まれるため、追加設定は不要

B) 本ユニット専用の追加設定を行う

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Security Patterns）
パスワード非公開（BR-RDBMS-12）の実装パターンは？

A) レスポンス用DTO（例: `RdbmsConnectionResponse`）にパスワードフィールド自体を含めない設計とする

B) エンティティに`@JsonIgnore`等を付与し、シリアライズ時の混入を防ぐ

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7（Performance Patterns）
HikariCPのプール詳細設定（アイドル接続数等）は？

A) `maximumPoolSize=5`, `minimumIdle=0`（アイドル時は接続を保持しない。接続テスト・スキーマ取込は低頻度操作のため、常時接続を維持する必要はない）

B) `minimumIdle`も一定数維持し、常時接続を保持する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 8（Resilience Patterns・Scalability Patterns）
DataSourceキャッシュのエビクション方針（未使用接続のクリーンアップ）は？

A) 明示的なエビクションは行わない。接続情報の更新・削除時にのみキャッシュを破棄する（NFR-03-01の前提（接続数十数件程度）であれば、全接続分のDataSourceをメモリ上に保持し続けても問題ない規模）

B) 一定時間未使用の接続はキャッシュから自動的に破棄する（LRU等）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

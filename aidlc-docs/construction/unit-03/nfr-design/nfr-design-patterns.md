# UNIT-03 RDBMSセットアップ - NFR Design Patterns

`unit-03-nfr-design-plan.md`の回答（全問A）に基づく実装パターンを記載する。

---

## 1. Resilience

### 1.1 スキーマ取込の全体タイムアウト（Q2=A、NFR-03-03）
- `CompletableFuture.supplyAsync(...).orTimeout(60, TimeUnit.SECONDS)`でスキーマ取込処理全体（JDBC接続確立〜メタデータ読取〜結果組み立てまで）を包む
- タイムアウト発生時は`TimeoutException`を捕捉し、BR-RDBMS-07のオールオアナッシング方針どおり取込処理全体を失敗として扱う（既存のスキーマスナップショットは変更しない）
- JDBC接続自体のタイムアウト（5秒、NFR-03-02）は、HikariCPの`connectionTimeout`設定で別途担保する（全体タイムアウトとは独立した、より短い個別の防御層）
- **タイムアウト時の裏スレッド中断（レビュー指摘の反映）**: `CompletableFuture.orTimeout()`は呼び出し元に失敗を返すのみで、`supplyAsync`側で実行中のJDBC呼び出し自体は中断されない。JDBCのブロッキングI/Oはスレッド割り込みに素直に応じないため、放置するとバックグラウンドスレッドが動き続け、HikariCPのプール枠（`maximumPoolSize=5`）を専有し続けるおそれがある。そのため、タイムアウト検知時（`orTimeout`が例外を返した時点）に、実行中の`Connection`を明示的に`close()`して強制的に中断させる処理を追加する（多くのJDBCドライバでは、別スレッドからの`Connection.close()`呼び出しによりブロック中の呼び出しが例外を投げて復帰する）

### 1.2 暗号鍵未設定・不正フォーマット時のfail-fast（Q3=A）
- `AppProperties`のコンストラクタ検証で、`MM_APP_RDBMS_ENCRYPTION_KEYS`が空・パース不能な場合はアプリケーション起動を失敗させる（`MM_APP_JWT_SECRET`の既存パターンに準拠）
- 検証項目: 最低1世代の鍵が存在すること、各鍵がBase64として妥当な形式でありAES-256（32バイト）の鍵長であること、**`keyId`が重複していないこと（レビュー指摘の反映。重複は設定ミスの兆候であり、fail-fastの対象に含める）**

### 1.3 対象RDBMS接続失敗時のフェイルセーフ（既存方針の継続適用）
- 接続テスト・スキーマ取込のいずれも、失敗時は例外を捕捉しBR-RDBMS-04のエラー分類に変換する（グローバル例外ハンドラへ伝播させず、サービス層で意図的に捕捉・変換する）
- DataSourceキャッシュ（§2.1）の生成自体が失敗した場合も同様に捕捉し、接続テスト・スキーマ取込のいずれの呼び出し元にも同じエラー分類で返す

### 1.4 接続情報更新中の同時実行に関する競合（レビュー指摘の反映）
接続情報の更新・削除時にDataSourceキャッシュを`close()`＋除去する一方、同時に別リクエストが同じキャッシュ済みDataSourceでスキーマ取込・接続テストを実行中の場合、当該操作がエラーとなる可能性がある。管理者1名〜数名規模の運用（NFR-03-01前提）では実害が小さいと判断し、明示的な排他制御（ロック等）は導入しない。過剰な同期制御は本プロジェクトの規模に見合わないリスク許容判断とする。

---

## 2. Performance

### 2.1 HikariCPプール設定（Q7=A）
- 接続ごとに生成する`HikariDataSource`は`maximumPoolSize=5`, `minimumIdle=0`とする
- `minimumIdle=0`により、アイドル時（接続テスト・スキーマ取込を実行していない間）は物理的なDB接続を保持しない。対象RDBMSが多数登録された場合でも、常時接続を保持することによるリソース消費（対象RDBMS側のコネクション上限逼迫等）を避ける

### 2.2 DataSourceキャッシュのエビクション方針（Q8=A）
- 明示的な時間ベース・LRUベースのエビクションは行わない
- 接続情報の更新・削除操作でのみキャッシュを破棄する（該当`HikariDataSource`の`close()`を呼び出した上でキャッシュから除去）
- NFR-03-01の前提規模（接続数十数件程度）であれば、全接続分の`HikariDataSource`インスタンス（いずれも`minimumIdle=0`のため未使用時は実接続を持たない）をメモリ上に保持し続けても問題ない

---

## 3. Security

### 3.1 接続パスワードの暗号化・復号（Q1=A、SECURITY-01, SECURITY-12）
- 暗号化・復号ロジックは`cherry.mastermeister.rdbmsconnection`パッケージ内に閉じて実装する（本ユニット専用。他ユニットへの再利用は現時点で想定しない）
- AES-256-GCMによる暗号化。鍵は世代（`keyId`）管理し、現在鍵（最大の`keyId`）で新規暗号化、全世代の鍵で復号を試行可能とする（tech-stack-decisions.md §1参照）
- 暗号化結果は「使用した`keyId`」と「IV（初期化ベクトル）＋暗号文＋認証タグ」を組にして保存する（永続化形式の詳細はlogical-components.mdで確定）

### 3.2 管理者専用エンドポイントのアクセス制御（Q5=A、SECURITY-08）
- `/api/admin/rdbms-connections/**`は、UNIT-02で確立済みのSecurityFilterChain設定（`/api/admin/**`パスパターンに対するロールベース認可）にそのまま合致するため、追加のセキュリティ設定は不要
- 未認証・非管理者ロールでのアクセスは既存の仕組みにより401/403として扱われる

### 3.3 パスワードの非公開（Q6=A、BR-RDBMS-12）
- 一覧・詳細取得用のレスポンスDTO（例: `RdbmsConnectionResponse`, `RdbmsConnectionSummaryResponse`）にパスワードフィールドを含めない設計とする（エンティティを直接シリアライズせず、DTOへの変換時点で除外する）
- リクエストDTO（登録・更新用）にはパスワードフィールドを持たせるが、更新時は空欄（null/空文字）の場合に既存の暗号化パスワードを保持する処理をサービス層で行う

### 3.4 対象RDBMSとのTLS方針（tech-stack-decisions.md §2の継続適用）
- アプリケーション側でTLSを強制する実装は行わない。`additionalParams`（BR-RDBMS-10）で管理者が指定した値をそのままJDBC URLへ付加する
- `backend/README.md`に、本番運用では対象RDBMSの実際のTLS構成に応じて`additionalParams`（例: `useSSL=true`, `sslmode=require`）を設定することを推奨する旨を記載する（SECURITY-09）

### 3.6 JDBC URL構築の方言別責務化（レビュー指摘の反映）
- JDBC URLのスキーム・パラメータ区切り文字（MySQL/MariaDB/PostgreSQLは`?`＋`&`、H2は`;`）は方言ごとに異なるため、URL構築自体を`RdbmsDialectStrategy.buildJdbcUrl(...)`に集約する（logical-components.md §1参照、component-methods.mdへの訂正を反映）
- フロントエンドの「追加パラメータ」入力欄のヘルプテキスト・プレースホルダーは、選択中の`dbType`に応じた記法例を表示する（Functional Design frontend-components.mdへの訂正を反映）

### 3.5 入力バリデーション（Q4=A、tech-stack-decisions.md §4の継続適用、SECURITY-05）
- 接続登録・更新・接続テスト（未保存値）のリクエストDTOにBean Validationアノテーション（`@NotBlank`, `@Min`/`@Max`（port）等）を付与し、コントローラで`@Valid`を用いる
- バリデーション違反時はUNIT-02で確立済みのBR-API-01形式のエラーレスポンスに変換する（既存のグローバル例外ハンドラをそのまま利用、追加実装不要）

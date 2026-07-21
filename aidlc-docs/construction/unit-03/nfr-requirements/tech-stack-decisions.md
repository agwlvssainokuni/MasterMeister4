# UNIT-03 RDBMSセットアップ - Tech Stack Decisions

`unit-03-nfr-requirements-plan.md`の回答（Q1=B, Q2=A, Q3=B, Q4=A, Q5=A, Q6=A, Q7=B, Q8=A, Q9=A）に基づく、バックエンドの技術選定一覧。

---

## 1. 接続パスワードの暗号化アルゴリズム・鍵ローテーション（Q1=B）

**決定**: AES-256-GCMによる可逆暗号化。鍵ローテーション（複数世代の鍵を許容し、古い鍵で暗号化されたデータも復号できる仕組み）を実装する。

**理由**: requirements.md §4の既存決定（可逆暗号化）を満たしつつ、GCMモードにより暗号化と改ざん検知（認証タグ）を同時に行える。ユーザ判断により、鍵の定期的な更新（漏洩時の被害限定、運用上のセキュリティ衛生）を可能にする鍵ローテーションまで対応する。

**設計方針**:
- 鍵は世代（`keyId`、整数の連番）で管理する。環境変数で複数世代の鍵を保持する（例: `MM_APP_RDBMS_ENCRYPTION_KEYS=1:base64key1,2:base64key2`のような`keyId:key`のカンマ区切りリスト形式。具体的なフォーマットはCode Generation段階で確定）
- 起動時、環境変数から鍵一覧を読み込み、最大の`keyId`を「現在鍵」（新規暗号化・更新時に使用）として扱う。すべての世代の鍵は復号に使用可能な状態でメモリ上に保持する
- 接続情報（`RdbmsConnection`）に、どの世代の鍵で暗号化したかを示す`encryptionKeyId`属性を追加する（domain-entities.mdへの反映はNFR Design段階で行う。UNIT-02のRegistrationRateState追加と同様の手続き）
- 復号時は、対象レコードの`encryptionKeyId`に対応する鍵を選択して復号する。該当する鍵が環境変数に存在しない場合（誤って古い鍵を削除した等）はエラーとする
- **鍵ローテーションの運用フロー**: 管理者が新しい鍵を環境変数に追加（世代を1つ増やす）して再起動すると、以降の新規登録・パスワード変更は新しい鍵で暗号化される。既存レコードは古い鍵のままでも復号可能なため、即座の一括再暗号化は不要。全レコードを新しい鍵に揃えたい場合は、各接続を個別に更新（パスワード再入力）することで段階的に移行する（自動的な一括再暗号化バッチは本ユニットのスコープ外。将来必要になった時点で別途検討する）
- 古い鍵を環境変数から削除すると、その世代で暗号化されたレコードは復号不能になる（運用上の注意点としてREADMEに明記する）

**依存関係**: 追加ライブラリ不要（`javax.crypto`標準API）

**設定項目**: `MM_APP_RDBMS_ENCRYPTION_KEYS`（必須、`keyId:base64key`のカンマ区切りリスト。詳細フォーマットはCode Generation段階で確定）

---

## 2. 対象RDBMSとのTLS利用方針（Q2=A）

**決定**: アプリケーション側でTLSを強制しない。デフォルトは平文接続とし、TLS有効化は管理者が`additionalParams`（BR-RDBMS-10）でDB固有のパラメータ（例: MySQL/MariaDBの`useSSL=true`、PostgreSQLの`sslmode=require`）を明示的に指定する運用とする。

**理由**: devenvのローカルDB（MySQL/MariaDB/PostgreSQL）はいずれもTLS未設定であり、アプリ側で強制すると開発時の動作確認自体ができなくなる。本番運用でTLSを必須にしたい場合は、管理者が対象RDBMSの実際の構成に応じて`additionalParams`を設定する（SECURITY-09、`backend/README.md`に運用上の注記を追加する）。

---

## 3. スキーマ取込のタイムアウト設定（Q3=B）

**決定**: JDBC接続タイムアウト5秒、スキーマ取込処理全体のタイムアウト60秒。全体タイムアウト超過時は失敗として扱う（BR-RDBMS-07のオールオアナッシングに従う）。

**理由**: 接続タイムアウトだけでは、接続確立後にメタデータ読取が想定外に長引くケース（ネットワーク不調、対象RDBMS側の高負荷等）を防げない。具体的な実装方式（`CompletableFuture` + `orTimeout()`、または`ExecutorService`によるタイムアウト付き実行等）はNFR Design段階で確定する。

---

## 4. 接続情報バリデーション実装方式（Q4=A）

**決定**: Bean Validation（`jakarta.validation`、`@NotBlank`/`@Min`/`@Max`等）をリクエストDTOに付与する。

**理由**: UNIT-02の登録・認証系エンドポイントと同じ方式に揃えることで、実装・レビューの一貫性を保つ。

**依存関係**: 追加不要（`spring-boot-starter-validation`はUNIT-01/02で導入済み想定。未導入の場合はCode Generation段階で追加）

---

## 5. 動的DataSource・コネクションプール管理方式（Q5=A）

**決定**: 接続ごとにHikariCPの`DataSource`インスタンスを生成し、アプリケーション内でキャッシュ（`ConcurrentHashMap<ConnectionId, HikariDataSource>`等）して再利用する。接続情報の更新・削除時は該当キャッシュを破棄（`HikariDataSource.close()`を呼び出した上で）し、次回アクセス時に再生成する。プールサイズは小さめ（`maximumPoolSize=5`程度）に設定する。

**理由**: COMP-07の`getDataSource(connectionId): DataSource`というメソッド設計は再利用前提の構造になっている。本ユニットでの利用頻度（接続テスト、スキーマ取込）は低頻度だが、UNIT-06（クエリ保存・実行）で同じ仕組みを高頻度に再利用することを見据え、プーリングの基盤を先に確立しておく。プールサイズ・負荷対応の本格的な調整はUNIT-06で見直す。

**依存関係**: 追加不要（HikariCPはSpring Boot標準のDataSource実装として同梱済み）

---

## 6. 対象RDBMS接続数・スキーマ規模の前提（Q6=A）

**決定**: 接続数は数件〜十数件程度、1接続あたり数十〜百数十テーブル程度を設計上の前提規模とする。

**理由**: requirements.mdの前提（社内10名規模の利用）と整合する典型的な規模。この前提を超える大規模ケース（数千テーブル等）への最適化は本ユニットのスコープ外とする。

---

## 7. 対象RDBMS接続用DBユーザの最小権限ガイダンス（Q7=B）

**決定**: `backend/README.md`に、対象RDBMS接続に使用するDBユーザは最小権限（本アプリの用途に必要な範囲のみ）で作成することを推奨する旨のドキュメントを追加する。アプリケーション側での権限チェック・強制は行わない。

**理由**: SECURITY-06への最低限の配慮。DBユーザの権限設定自体はRDBMS側の管理operationであり、本アプリの制御範囲外である。

---

## 8. JDBCドライバの依存関係追加（Q8=A）

**決定**: `backend`のGradle依存関係として以下の4種のJDBCドライバを追加する。

| dbType | Gradle依存関係（グループ:アーティファクト。バージョンはCode Generation段階で最新安定版を確認） |
|---|---|
| MYSQL | `com.mysql:mysql-connector-j` |
| MARIADB | `org.mariadb.jdbc:mariadb-java-client` |
| POSTGRESQL | `org.postgresql:postgresql` |
| H2 | `com.h2database:h2`（内部DB用に既存導入済みの依存関係を、対象RDBMS接続でも共用する） |

**理由**: UNIT-01で導入済みのOWASP Dependency-Checkプラグインは`backend`モジュールの全依存関係をスキャン対象とするため、追加設定なしで新規ドライバもスキャン対象に含まれる（SECURITY-10）。

---

## 9. 対象RDBMS接続失敗時のアラート機構（Q9=A）

**決定**: 専用のアラート機構（通知・監視）は設けない。UI上のエラー表示とアプリケーションログ（失敗時のみ）で完結させる。

**理由**: 対象RDBMS接続テスト・スキーマ取込は管理者による手動操作であり、外部攻撃者による自動化された試行対象ではない。UNIT-02のNFR-4.5（ログイン失敗多発時のアラート）とは性質が異なると判断する。

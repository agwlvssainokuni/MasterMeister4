# UNIT-03 RDBMSセットアップ - NFR Requirements 計画

## Scalability / Availability
requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、本ユニットもN/A（UNIT-01/UNIT-02と同様の判断）。対象RDBMS接続数・スキーマ規模についてのみ、後述Q6で確認する。

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | **該当** | 接続パスワードの保存時暗号化（requirements.md §4で可逆暗号化と決定済み。アルゴリズム・鍵管理を本ステージで確定）。対象RDBMSとの通信時暗号化（TLS）はQ2で確認 |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | リバースプロキシ等の中間機器は本ユニットのスコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（business-logic-model.md）で対応。追加検討事項なし |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み・再決定不要） | UNIT-02 NFR Designで全体設定済み（`SecurityConfig`）。本ユニット固有の追加なし |
| SECURITY-05（全APIパラメータの入力検証） | 該当 | 接続情報登録・更新のバリデーション実装方式をQ4で確認 |
| SECURITY-06（最小権限アクセスポリシー） | 該当（運用ガイダンスとして） | 対象RDBMS接続に使用するDBユーザ自体の権限は本アプリの制御外だが、管理者向けの推奨事項として文書化するかをQ7で確認 |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベルの構成であり、Infrastructure Design自体がSKIP判定（devenv整備済みのため） |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み・再確認） | UNIT-02のJWT認証・ロールチェックを流用し、本ユニットの全エンドポイントを管理者専用とする（frontend-components.md §4で記載済み）。追加検討事項なし |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | 該当 | 対象RDBMSへのTLSデフォルト方針（Q2）と関連 |
| SECURITY-10（ソフトウェアサプライチェーン） | 該当 | 新規追加するJDBCドライバ（MySQL/MariaDB/PostgreSQL/H2）がOWASP Dependency-Checkの対象に含まれることを確認（Q8） |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | フェイルクローズ・管理者専用境界の明示（business-logic-model.md）。追加検討事項なし |
| SECURITY-12（認証・認証情報管理） | 該当 | 接続パスワードの暗号化アルゴリズム・鍵管理方式をQ1で確定 |
| SECURITY-13（データ整合性検証） | N/A | 本ユニットに署名検証等のデータ整合性要件はない |
| SECURITY-14（アラート・監視） | N/A（判断） | 対象RDBMS接続テスト・スキーマ取込は管理者による手動操作であり、外部からの自動化された攻撃対象ではないため、専用のアラート機構は不要と判断（Q9で確認） |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。エラー分類（BR-RDBMS-04）は業務要件としてFunctional Designで確定済み。追加検討事項なし |

## Property-Based Testing 拡張
Functional Design（business-logic-model.md §7）でPBT対象プロパティなしと判断済み。本ステージでの追加検討は不要（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 曖昧な回答なし、追加質問不要
- [x] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する
- [x] Step D: `tech-stack-decisions.md`（暗号化方式、JDBCドライバ、動的DataSource/コネクションプール方針等）を作成する
- [x] Step E: 完了メッセージを提示し、承認を得る — 承認済み（2026-07-21T01:25:00Z）

## 質問

### Question 1（Security Requirements、SECURITY-12）
接続パスワードの可逆暗号化について、アルゴリズム・鍵管理方式は？

A) AES-256-GCM。暗号鍵は環境変数（例: `MM_APP_RDBMS_ENCRYPTION_KEY`）経由で1本のみ設定する（鍵ローテーションは対象外。UNIT-02のJWT署名鍵と同様の運用方針に揃える）

B) Aと同様だが、鍵ローテーション（複数世代の鍵を許容し、古い鍵で暗号化されたデータも復号できる仕組み）まで実装する

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 2（Security Requirements、SECURITY-01, SECURITY-09）
対象RDBMSとの通信（JDBC接続）でのTLS利用について、アプリケーション側でどこまで関与しますか？

A) デフォルトはTLS無効（平文接続）とし、TLS有効化は管理者が`additionalParams`（BR-RDBMS-10）でDB固有のパラメータ（`useSSL=true`等）を明示的に指定する運用とする。アプリケーション側で強制はしない（devenvのローカルDBはTLS未設定のため、強制するとローカル動作確認ができなくなる）

B) TLSを既定で有効化し、無効化にはあえて`additionalParams`での明示的なオプトアウトを必要とする

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Performance Requirements）
スキーマ取込（JDBC `DatabaseMetaData`による読取）の性能に関する前提は？

A) 想定規模は小〜中規模（数十〜百数十テーブル程度）とし、明示的なタイムアウト設定（例: 接続タイムアウト5秒、取込全体のタイムアウト設定はなし）のみ行う。パフォーマンスチューニング（並列読取等）は対象外

B) 取込全体にも明示的なタイムアウト（例: 60秒）を設ける。超過時は失敗として扱う（BR-RDBMS-07のオールオアナッシングに従う）

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 4（Security Requirements、SECURITY-05）
接続情報のバリデーション実装方式は？

A) Bean Validation（Jakarta Validation、`@NotBlank`/`@Min`/`@Max`等のアノテーション）をリクエストDTOに付与する（UNIT-02の登録・認証系エンドポイントと同じ方式に揃える）

B) サービス層での手続き的なバリデーション（if文による individual チェック）とする

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Tech Stack Selection）
対象RDBMSへの動的な接続（`RdbmsConnectionService.getDataSource(connectionId): DataSource`）は、どのように管理しますか？

A) 接続ごとにHikariCPの`DataSource`インスタンスを生成し、アプリケーション内でキャッシュ（Map等）して再利用する。接続情報の更新・削除時は該当キャッシュを破棄し、次回アクセス時に再生成する。プールサイズは小さめ（例: maximumPoolSize=5）に設定し、本格的な負荷対応はUNIT-06（クエリ実行）で見直す

B) プーリングは行わず、操作の都度JDBCの生の`Connection`を生成・破棄する（スキーマ取込・接続テストは低頻度操作のため、プールの複雑さを避ける）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Scalability、前提確認）
対象RDBMS接続数・1接続あたりのテーブル数について、設計上の前提規模は？

A) 接続数は数件〜十数件程度、1接続あたり数十〜百数十テーブル程度（社内ツールとしての典型的な利用規模）

B) より大規模（接続数百件、1接続あたり数千テーブル）を想定する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7（Security Requirements、SECURITY-06）
対象RDBMS接続に使用するDBユーザの権限について、アプリケーション側で何らかのガイダンス・チェックを行いますか？

A) 行わない。接続に使用するDBユーザの権限設定は運用者の責任範囲とし、アプリケーションは指定された認証情報でそのまま接続を試みるのみとする（README等でのドキュメント上の推奨事項の記載は任意）

B) README等に「最小権限のDBユーザを使うことを推奨する」旨のドキュメントを追加する

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 8（Tech Stack Selection、SECURITY-10）
新規追加するJDBCドライバ（MySQL/MariaDB/PostgreSQL/H2）の依存関係追加について

A) `backend`のGradle依存関係として4種のドライバを追加する。UNIT-01で導入済みのOWASP Dependency-Checkプラグインの既存スキャン対象に自動的に含まれる（追加設定不要）

B) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 9（Reliability・Availability、SECURITY-14）
対象RDBMS接続の疎通失敗・スキーマ取込失敗について、専用のアラート機構（通知・監視）を設けますか？

A) 設けない。管理者による手動操作の結果はUI上のエラー表示とアプリケーションログ（失敗時）で完結させる。UNIT-02で導入したアラート機構（NFR-4.5、ログイン失敗多発時のアラート）とは性質が異なる（外部攻撃者による自動化された試行ではなく、管理者自身の操作ミスが主因のため）

B) スキーマ取込失敗が一定回数連続した場合にアラートを出す

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

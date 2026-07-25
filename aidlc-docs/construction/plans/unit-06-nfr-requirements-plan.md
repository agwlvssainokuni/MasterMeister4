# UNIT-06 クエリ保存・実行 - NFR Requirements 計画

## Scalability / Availability
requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜UNIT-05と同様の判断）。ただし本ユニットは任意のSELECT文を対象RDBMSに対して直接実行するため、結果セットの規模・タイムアウト・ページング設計についてはQ2〜Q4で個別に確認する。

## 既存基盤の確認（Functional Designからの追加調査）
NFR Requirements着手にあたり、関連する既存実装を確認した:
- `RdbmsDialectStrategy.applySchemaSwitch(Connection, String)`は、UNIT-04時点で「本メソッドはUNIT-06（クエリ実行時の対象スキーマ指定、FR-7.5）専用とする」と明記され、既に用意済み（未使用）。新規のダイアログ方言メソッド追加は不要
- UNIT-05の`RecordQueryService`は`NamedParameterJdbcTemplate(dataSource)`を都度生成し、`LIMIT :pageSize OFFSET :offset`をSQL末尾に直接付与する方式（常時ページング前提）。本ユニットはページング有無をユーザが選択でき（FR-7.6）、かつ対象がテーブル直接参照ではなく任意SELECT文であるため、同じ方式をそのまま適用できるかはQ2で確認する
- UNIT-05の`MasterDataService.listAccessibleConnections/listAccessibleTables`は、`EffectivePermissionResolver.resolvePrimary`（UNIT-04のCaffeineキャッシュ対象）をループ呼び出しする方式で、スキーマ/テーブル単位の新規集約キャッシュは追加していない。本ユニットのスキーマ許可リスト判定（BR-QUERY-02）も同様の方式が使えるかをQ6で確認する

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 内部DBに新規永続化する`SavedQuery`/`QueryExecutionRecord`は機微情報（パスワード等）を含まない。対象RDBMSへの接続情報自体はUNIT-03で暗号化済み |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（BR-QUERY-10、`QUERY_EXECUTED`等）で対応済み |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | ユーザ入力SQL全体の読み取り専用検証（BR-QUERY-01、Functional Designで確定済み）に加え、実行時のスキーマ・接続・パラメータ値の検証方式をQ1・Q6で確認 |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | BR-QUERY-02〜04（スキーマ単位のアクセス制御）で対応済み |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | BR-QUERY-05〜09（公開範囲・作成者限定操作の判定）で対応済み |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし（JSqlParserはUNIT-05で追加済みを再利用、`NamedParameterJdbcTemplate`はSpring既存機能） |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | スキーマ許可リストのフェイルクローズ（BR-QUERY-02〜03）、読み取り専用検証（BR-QUERY-01）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 本ユニットは読み取り専用のみ（更新系操作なし） |
| SECURITY-14（アラート・監視） | 該当 | 任意SQL実行という性質上、長時間実行クエリ・大量結果取得に対する制御方針をQ3・Q4・Q5で確認 |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-QUERY-01の検証拒否時エラー応答はフェイルセーフの実践例 |

## Property-Based Testing 拡張
business-logic-model.md §9でテスト可能プロパティを識別済み（SQL読み取り専用検証、パラメータ検出、スキーマ許可リスト判定）。フレームワークはUNIT-02でjqwikに確定済みのため、本ステージでの追加決定は不要（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全6問、AI推奨どおり全問Aで確定 2026-07-25T00:20:00Z）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 回答に曖昧性なし
- [x] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する（2026-07-25T00:25:00Z）
- [x] Step D: `tech-stack-decisions.md`（スキーマ切替＋クエリ実行の接続管理方式、ページング方式、タイムアウト制御、監査ログ閾値の扱い等）を作成する（2026-07-25T00:25:00Z）
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Tech Stack Selection・Security Requirements、BR-QUERY §3.3、最重要）
`RdbmsDialectStrategy.applySchemaSwitch(Connection, schema)`によるスキーマ切替と、その後のクエリ実行（`NamedParameterJdbcTemplate`）を、同一の物理JDBC接続上で確実に実行するための接続管理方式は？

A) `DataSource.getConnection()`で物理接続を取得し`applySchemaSwitch`を適用したうえで、Springの`SingleConnectionDataSource`（`suppressClose=true`）でラップして`NamedParameterJdbcTemplate`に渡して実行する。処理完了後は`finally`句で物理接続を明示的にクローズする。読み取り専用の単発クエリであり、ロールバック等のトランザクション意味論を必要としないため、`DataSourceTransactionManager`は導入しない

B) UNIT-05の`RecordBatchService`と同様に`DataSourceTransactionManager`＋`TransactionTemplate`（読み取り専用トランザクション）でコネクションをバインドし、そのトランザクション内で`applySchemaSwitch`と`NamedParameterJdbcTemplate`実行を行う。既存の一括反映と同じインフラを再利用できるが、読み取り専用処理にトランザクション機構を持ち込むことになる

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 2（Tech Stack Selection・Performance、FR-7.6）
ページング有効時、ユーザが入力した任意のSELECT文にLIMIT/OFFSETを適用する方式は？

A) `SELECT * FROM (<ユーザSQL>) AS mm_page LIMIT :pageSize OFFSET :offset`のようにサブクエリでラップする。対象4方言（PostgreSQL/MySQL/MariaDB/H2）はいずれもLIMIT/OFFSET構文をサポートし、UNIT-05の`RecordQueryService`と同じLIMIT/OFFSET直書き方式を踏襲できる。ただし、内側SQLにORDER BYが含まれていても外側に別途ORDER BYがない場合の行順序保持はSQL標準では厳密には保証されない（実務上は主要RDBMS実装で保持されるが100%の保証ではない旨をbusiness-rules.mdに注記する）

B) アプリケーション層でのメモリ内ページング（DB側は常に全件取得し、Java側でリストをスライスする）。行順序はDBが返した結果そのままのため確実に保持されるが、大量データ時にメモリ使用量・パフォーマンスのリスクがある

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Reliability・Security Requirements、SECURITY-14）
ユーザが入力した任意SQLの実行に対するタイムアウト制御は？

A) JDBC標準の`Statement.setQueryTimeout(秒数)`を利用する（`JdbcTemplate`の`queryTimeout`プロパティとして設定可能）。デフォルト値は`application.yml`で設定可能とする（UNIT-02/03/05で確立した設定値管理方式と一貫する）

B) UNIT-03の`SchemaIntrospectionService`と同様、`CompletableFuture.orTimeout`による非同期タイムアウト制御を導入する（より強制力が高いが実装が複雑）

C) タイムアウト制御を設けない（社内ツール規模のため許容範囲と判断する）

D) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4（Scalability・Reliability、FR-7.6）
ページングを無効にした場合の結果件数に、安全のための上限を設けますか？

A) 上限を設ける（例: 10,000件を超える結果は400エラーで拒否し、ページングの有効化を促すメッセージを返す）。任意規模のマスタデータ（数万〜数十万件、UNIT-05前提を踏襲）を直接対象とするため、上限なしのメモリ展開はリスクがあると判断する

B) 上限を設けない（ユーザの任意SQL・任意規模であり、結果として生じる表示崩れやパフォーマンス劣化はユーザの責任範囲とする）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Security Requirements・監査ログ、requirements.md §6.1「大量データ取得」との関係）
requirements.md §6.1は「大量データ取得（閾値は設定可能、デフォルト100件以上）」と「クエリ実行（結果件数・実行時間を含む）」を別個のデータアクセスイベントとして列挙している。BR-QUERY-10により`QUERY_EXECUTED`は実行のたびに結果件数（rowCount）を含めて必ず記録されるが、これとは別に、UNIT-05で導入した閾値判定（`mm.app.audit.bulk-access-threshold`）に基づく専用の大量データ取得イベントを追加しますか？

A) 追加しない。`QUERY_EXECUTED`が実行のたびにrowCountを含めて必ず記録される（UNIT-05の閾値判定より広いカバレッジ）ため、管理者は`QUERY_EXECUTED`をrowCountで絞り込めば大量データ取得を把握でき、重複記録は不要と判断する

B) 追加する。新規イベント種別`QUERY_BULK_ACCESSED`を新設し、UNIT-05と同じ閾値判定ロジック（既存の`mm.app.audit.bulk-access-threshold`設定を再利用）を適用して、閾値超過時のみ別イベントとしても記録する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Tech Stack Selection・Performance、BR-QUERY-02〜03）
実行対象スキーマの許可リスト判定（「その接続について、実行者がいずれかのテーブル/カラムに実効主権限READ以上を持つスキーマ」の集合を求める処理）は、既存の`EffectivePermissionResolver`をどう利用しますか？

A) `EffectivePermissionResolver`に新規メソッドを追加せず、`QueryExecutionService`側で対象接続配下の全テーブル/カラムに対し既存の`resolvePrimary`をループ呼び出しして許可スキーマ集合を都度算出する。UNIT-04のCaffeineキャッシュ（テーブル/カラム単位）がヒットする前提のため許容できるコストと判断する。UNIT-05の`listAccessibleConnections`/`listAccessibleTables`と同じ方式

B) `EffectivePermissionResolver`に新規メソッド（例: `listAccessibleSchemas(userId, connectionId): Set<String>`）を追加し、スキーマ単位の判定結果自体を新たにキャッシュする。アクセス頻度が高い場合の追加最適化になるが、UNIT-04の`@CacheEvict`無効化ロジックの対象拡大が必要になる

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

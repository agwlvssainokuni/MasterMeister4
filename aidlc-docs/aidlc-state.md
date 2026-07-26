# AI-DLC State Tracking

## Project Information
- **Project Type**: Greenfield
- **Start Date**: 2026-07-20T09:54:00Z
- **Current Stage**: CONSTRUCTION - UNIT-08 Infrastructure Design（詳細は`## Current Status`参照）

## Workspace State
- **Existing Code**: No
- **Reverse Engineering Needed**: No
- **Workspace Root**: /Users/agawa/Documents/project/git/MasterMeister4

## Code Location Rules
- **Application Code**: Workspace root (NEVER in aidlc-docs/)
- **Documentation**: aidlc-docs/ only
- **Structure patterns**: See code-generation.md Critical Rules

## Extension Configuration
| Extension | Enabled | Decided At |
|---|---|---|
| Security Baseline | Yes | Requirements Analysis |
| Resiliency Baseline | No | Requirements Analysis |
| Property-Based Testing | Yes (Full enforcement) | Requirements Analysis |

## Execution Plan Summary
- **Total Stages**: 8（Application Design, Units Generation, Functional Design, NFR Requirements, NFR Design, Infrastructure Design, Code Generation, Build and Test）
- **Stages to Execute**: Application Design, Units Generation, Functional Design（ユニットごと）, NFR Requirements（ユニットごと）, NFR Design（ユニットごと）, Infrastructure Design（ユニットごと）, Code Generation, Build and Test
- **Stages to Skip**: なし（Operationsはプレースホルダーのため対象外）

## Stage Progress
### 🔵 INCEPTION PHASE
- [x] Workspace Detection — COMPLETED (2026-07-20T09:54:00Z)
- [x] Requirements Analysis — COMPLETED (approved 2026-07-20T10:41:00Z)
- [x] User Stories — COMPLETED (approved 2026-07-20T10:57:00Z)
- [x] Workflow Planning — COMPLETED (approved 2026-07-20T11:10:00Z)
- [x] Application Design — COMPLETED (approved 2026-07-20T11:45:00Z)
- [x] Units Generation — COMPLETED (approved 2026-07-20T12:02:00Z, 10 units, base package cherry.mastermeister)

### 🟢 CONSTRUCTION PHASE
**注記**: 以下4ステージの「EXECUTE（ユニットごと）」はプロジェクト全体の見通しであり確約ではない。各ユニット着手時にそのユニット単独でEXECUTE/SKIPを判定し、他ユニットの判定は引き継がない（詳細は execution-plan.md 参照）。
- [ ] Functional Design — EXECUTE（ユニットごと、判定は都度独立）
- [ ] NFR Requirements — EXECUTE（ユニットごと、判定は都度独立）
- [ ] NFR Design — EXECUTE（ユニットごと、判定は都度独立）
- [ ] Infrastructure Design — EXECUTE（ユニットごと、判定は都度独立）
- [ ] Code Generation — EXECUTE
- [ ] Build and Test — EXECUTE

### 🟡 OPERATIONS PHASE
- [ ] Operations — PLACEHOLDER

## Current Status
- **Lifecycle Phase**: CONSTRUCTION
- **Current Stage**: UNIT-08 クエリ履歴 - Infrastructure Design SKIP
- **Next Stage**: UNIT-08 クエリ履歴 - Code Generation
- **Status**: 実施中

## Backlog（今後の検討課題）
- **E2Eテストフレームワーク（Playwright等）の導入**: 現状、各ユニットのCode Generation最終ステップでコマンドラインによる実機E2E検証（curl等）を実施しているが、UI表示に関する不具合（例: UNIT-05のダークモード文字色バグ）はこの方式では検出できない。Playwright等のフレームワークは既存の実インフラE2E検証（DB方言差異等のバックエンド/インフラ層の不具合検出に有効）を置き換えるものではなく補完するものと位置づけ、プロジェクト全体のテスト戦略として別途検討する（2026-07-24、UNIT-05承認時にユーザ提起）。

## Current Unit - Stage Progress (UNIT-01)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-20T14:20:00Z。グランドデザイン・代表画面モックのコンポーネント構造設計のため）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-20T14:40:00Z）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-20T14:55:00Z）
- [x] Infrastructure Design — SKIP（devenvはローカル開発環境設定であり、本番デプロイのインフラ設計には該当しない）
- [x] Code Generation — EXECUTE、COMPLETED（承認 2026-07-20T19:26:00Z。Part 1計画承認 → Part 2実装、全12セクション完了）

## Current Unit - Stage Progress (UNIT-02)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-20T21:10:00Z。business-logic-model.md, business-rules.md, domain-entities.md, frontend-components.mdを作成。レビューで複数回の修正を反映: DISABLED運用フロー、email一意制約、REJECTED再登録方針、管理者ダッシュボード/ユーザ管理画面の統合とトップ画面新設、無効化時のトークン失効、AuditLogEntry記録内容の一元化、メール件名管理方式）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-20T21:46:00Z。Spring Security OAuth2 Resource Server、HS256、BCrypt、登録エンドポイントのレート制限、jqwik（NFR-5.2最終確定）、SLF4J+Logback、内部DBアクセス方式（Spring Data JPA、Flyway、H2ファイルベース永続化）を決定）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-20T22:11:00Z。認証トークンはsessionStorage保管に確定、HTTPヘッダ・入力バリデーション・グローバル例外ハンドラ・SecurityFilterChain構成、内部DB暗号化の文書化された例外（NFR-4.8）、レジリエンス方針を決定。RegistrationRateStateエンティティとレート制限値(BR-REG-07)を追加）
- [x] Infrastructure Design — SKIP（メール送信・JWT鍵管理は設定レベルで対応可能）
- [x] Code Generation — COMPLETED（承認 2026-07-21T00:15:00Z。全18セクション完了。承認前レビュー対応: CORS設定削除、EmailNotificationServiceの責務分離とMailDeliveryService新設・Fromアドレス欠落修正、devenv整備、frontend.base-urlデフォルト修正）

## Current Unit - Stage Progress (UNIT-03)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-21T01:05:00Z。business-logic-model.md, business-rules.md（BR-RDBMS-01〜12）, domain-entities.md, frontend-components.mdを作成。レビューで反映: JDBC URL追加パラメータ(additionalParams)、DBMS選択時のデフォルトポート自動入力、未保存値に対する接続テスト、パスワード非公開方針、表示名重複許可、H2のschemaName欄表示）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-21T01:25:00Z。AES-256-GCM+鍵ローテーション、TLSデフォルト無効、Bean Validation、HikariCP動的DataSourceキャッシュ、JDBCドライバ4種、DBユーザ最小権限のREADME注記を決定）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-21T02:00:00Z。CompletableFuture.orTimeoutによるタイムアウト制御・タイムアウト時のConnection強制中断、ConnectionCredentialCipher新設、RdbmsConnectionService内部DataSourceキャッシュ、AppProperties.Rdbms（鍵世代管理・keyId重複検証）、RdbmsDialectStrategyへのbuildJdbcUrl追加（方言別URL構築の一元化）を決定）
- [x] Infrastructure Design — SKIP（承認 2026-07-21T00:25:00Z。devenvのDBコンテナは整備済みのため）
- [x] Code Generation — COMPLETED（承認 2026-07-22T06:10:00Z。全16セクション完了。承認前レビュー対応: スキーマ詳細画面のテーブル選択不可を修正（DataTableにonRowClick追加）、テーブル切替時の制約バッジ残留を修正（React key衝突、DataTableへのkey付与＋バッジkeyの一意化）、付随して主キー自動生成インデックスのUNIQUE制約重複登録を修正、テーブル一覧・カラム一覧へのキャプション追加）

## Current Unit - Stage Progress (UNIT-04)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-23T07:40:00Z。business-logic-model.md, domain-entities.md, business-rules.md, frontend-components.mdを作成。複数回のレビュー指摘を反映: UNIT-03の1接続=1スキーマ前提の遡及修正（複数スキーマ対応）、ナビパス/access-control→/groups、権限設定APIをトップレベル独立リソース/permissions/{connectionId}へ変更、パッケージ構成をgroup/permissionの2分割（unit-of-work.mdへの遡及修正含む）、DELETE APIの対象特定不備修正）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-23T08:00:00Z。Caffeineキャッシュ（maximumSize=10,000, expireAfterWrite=30分）、Spring Cache抽象化、無効化は@CacheEvict(allEntries=true)によるキャッシュ全体クリア、EffectivePermissionResolverはUNIT-04時点でAPI非公開、jackson-dataformat-yaml流用、楽観的ロックなし、小〜中規模想定、Bean Validation、YAML importサイズ上限を決定）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-23T08:30:00Z。nfr-design-patterns.md（YAML import検証/DB反映分離、グループ削除カスケード、フェイルクローズ、@CacheEvict配置一覧、AccessPermissionインデックス設計、NULL値センチネル対応、YAML受け渡し方式、管理者エンドポイントアクセス制御）、logical-components.md（group/permissionパッケージ設計、Caffeine設定）を作成。承認前レビュー対応: AccessControlService→PermissionServiceに改称（GroupServiceとの命名一貫性）、EffectivePermissionResolver.invalidateCache()の設計矛盾を@CacheEvict宣言的方式に整合）
- [x] Infrastructure Design — SKIP（承認 2026-07-22T06:15:00Z。新規インフラ不要）
- [x] Code Generation — COMPLETED（承認 2026-07-24T09:00:00Z。全16セクション完了。group/permissionパッケージのエンティティ・サービス・API・フロントエンド一式を実装。実機E2E検証でYAML再インポート時の複合UNIQUE制約違反（Hibernateフラッシュ順序起因）を発見・修正、再発防止テスト追加。バックエンド213件・フロントエンド149件全件成功。承認前レビューでAccessPermissionTreePageのツリー表示レイアウトを調整（CSS Grid固定幅化、hover色修正））

## Current Unit - Stage Progress (UNIT-05)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-24T09:50:00Z。business-logic-model.md, business-rules.md（BR-MASTER-01〜15）, domain-entities.md, frontend-components.mdを作成。全17問（Q1〜17）に回答、レビューでdeletableフィールド欠落（AccessibleTable/RecordPage/ER図）とフィルタ併用可否未定義を修正。一般ユーザ向け新規API名前空間`/api/master-data/*`、接続選択→テーブル/ビュー一覧→レコード一覧の3画面構成、内部DB新規永続化なしを決定）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-24T10:20:00Z。JSqlParserによるSQL手入力の構文検証、既存`RdbmsConnectionService.getDataSource()`＋`NamedParameterJdbcTemplate`による動的レコードアクセス、一括反映バッチ上限1,000件、監査ログ閾値のapplication.yml設定化を決定。レビューで一括反映のトランザクション制御方式の欠陥（@Transactionalが対象RDBMS用DataSourceを制御できない）を発見し、接続ごとの`DataSourceTransactionManager`＋`TransactionTemplate`による明示的制御に修正）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-24T10:40:00Z。全8問に回答。オールオアナッシングの2段階検証手順、JSqlParserのダミーSELECT文embedding構文検証、DataSourceTransactionManagerのリクエストごと生成、/api/master-data/**の新規SecurityFilterChainルール、MasterDataController等の論理コンポーネントを確定）
- [x] Infrastructure Design — SKIP（承認 2026-07-24T10:45:00Z。新規インフラ不要、JSqlParserはライブラリ依存のみ）
- [x] Code Generation — COMPLETED（承認 2026-07-24T13:36:00Z。全16セクション完了。実機E2E検証（PostgreSQL/MySQL）で2件の重大バグを発見・修正: ObjectMapper DI注入によるアプリ起動失敗、RecordBatchService.executeDeleteの主キー型バインド不具合。承認前レビュー対応2件: APIパス構造の簡略化（/api/master-data/connections/{id}/...→/api/master-data/{id}/...）、ダークモードでの編集可能セル文字色固定の修正）

## Current Unit - Stage Progress (UNIT-06)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-25T00:10:00Z。business-logic-model.md, business-rules.md（BR-QUERY-01〜11）, domain-entities.md, frontend-components.mdを作成。全10問（Q1〜10、Q9=B以外は推奨A）＋追加質問Q11に回答。レビュー指摘を反映: 保存クエリは`connectionId`を保持し接続に紐付ける（スキーマは非依存のまま）方式に訂正、画面フローをFlow A（保存クエリ管理: 接続選択→一覧→新規/既存、ナビ項目`savedQueries`）とFlow B（ad-hocクエリ実行: 接続選択→実行、新規ナビ項目`queryExecution`）に分離、APIパス命名をUNIT-05確立済み規約（`connections`セグメント重複除去、`{connectionId}`配下へのネスト統一）に整理）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-25T00:30:00Z。全6問AI推奨（全問A）で確定。nfr-requirements.md（Security Baseline全15ルール評価、Scalability/Performance/Availability/監査ログ/Reliability方針）、tech-stack-decisions.md（SingleConnectionDataSourceによるスキーマ切替＋クエリ実行の接続管理、サブクエリラップ方式のLIMIT/OFFSETページング、JDBC標準setQueryTimeoutによるタイムアウト制御、結果件数上限10,000件、EffectivePermissionResolver.resolvePrimaryループ呼び出しによるスキーマ許可リスト判定）を作成。新規外部ライブラリ依存なし）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-25T00:50:00Z。全7問AI推奨（Q1〜6=A、Q7=B）で確定。nfr-design-patterns.md（スキーマ非許可403/SQL非読み取り専用400/保存クエリアクセス不可404/タイムアウト408/結果件数上限超過400の各専用例外、同一物理接続でのスキーマ切替＋COUNT・結果取得の2回実行、QuerySqlAnalyzerによる1回解析の再利用）、logical-components.md（QueryController/SavedQueryControllerの2分割、QueryExecutionService/SavedQueryService/QuerySqlAnalyzerの新設、5種の新規例外クラス、AppProperties.Query新設）を作成）
- [x] Infrastructure Design — SKIP（承認 2026-07-24T13:45:00Z。新規インフラ不要）
- [x] Code Generation — COMPLETED（承認 2026-07-26T05:21:00Z。Part 1計画承認 2026-07-25T01:10:00Z。マイグレーション（V15 saved_query, V16 query_execution_record）、QuerySqlAnalyzer/QueryExecutionService/SavedQueryService、5種新規例外、QueryController/SavedQueryController、フロントエンドFlow A/B全5画面＋共有QueryEditorPanel、新規ナビ項目queryExecution追加。バックエンド334件・フロントエンド203件全件成功。実機E2E検証（PostgreSQL/MySQL）でSavedQueryService.updateQuery/retireQueryの永続化バグを発見・修正）

## Current Unit - Stage Progress (UNIT-07)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-26T05:55:00Z。unit-07-functional-design-plan.mdの全10問に回答: Q1-8=A（列単位実効権限フィルタリング、INNER/LEFT/RIGHTのみ、構造化等価結合、フラットAND、UNIT-05踏襲の演算子体系独自定義、常にエイリアス修飾、リバースエンジニアリング失敗時は専用例外拒否、QueryBuilderState永続化なし）、Q9=B（標準5種+DISTINCT修飾）、Q10=A+逆遷移/相互遷移追加。business-logic-model.md, business-rules.md（BR-QUERYBUILDER-01〜12）, domain-entities.md, frontend-components.mdを作成。承認前レビューでテーブル可視判定ロジックの矛盾（UNIT-05 isTableVisible()との不整合）を発見・修正）
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-26T06:18:00Z。unit-07-nfr-requirements-plan.mdの全6問に推奨どおり全問Aで回答: 比較値はリテラル埋め込み、JSqlParser Expression APIによる型安全な埋め込み、SQL生成/解析はJSqlParserのASTオブジェクトモデルで統一、ColumnDataTypeCategoryはUNIT-05踏襲の独自再実装、リクエスト件数上限あり、既存Caffeineキャッシュに一任。nfr-requirements.md, tech-stack-decisions.mdを作成。承認前レビューでBOOLEAN型リテラルの「確認済み」という未検証の言い切りを是正）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-26T06:38:00Z。unit-07-nfr-design-plan.mdの全5問に推奨どおり全問Aで回答: GROUP BY整合性違反は専用例外、リバースエンジニアリング失敗は構文非対応(422)とアクセス権限不足(403)を分離、スキーマアクセス不可はUNIT-06の既存例外を再利用、テーブル/カラム一覧取得ロジックはQueryBuilderAccessResolverとして分離、Controllerは単一構成。nfr-design-patterns.md, logical-components.mdを作成。承認前レビューで補助メソッド名existsTableColumn（存在確認のみを示唆）をisColumnAccessible（存在+権限確認の両方を明示）に是正）
- [x] Infrastructure Design — SKIP（判定 2026-07-26T06:38:00Z。新規DB永続化なし、新規外部サービス依存なし、既存インフラ（UNIT-03/04/06）の再利用のみのため新規インフラ設計不要）
- [x] Code Generation — COMPLETED（承認 2026-07-26T19:56:00Z）。Part 1（計画）承認済み（2026-07-26T06:50:00Z）、Part 2全12セクション完了（unit-07-code-generation-plan.md。Business Logic層（DTO/enum群、QueryBuilderColumnTypeMapper、新規例外4種、QueryBuilderAccessResolver、QueryBuilderService）、API層（QueryBuilderController）、Frontend（接続選択画面、タブUIサブコンポーネント、QueryBuilderPage、UNIT-06既存ページへの逆遷移ボタン追加）を作成。実装・実機検証の過程で発見・修正した不具合4件: (1)WHERE/HAVING列参照でSQLエイリアスを実テーブル名として権限チェックに渡していた誤り、(2)JSqlParserのLongValue/BooleanValueが値検証しないこと・DateValueがJDBC escape構文になる問題、(3)HAVING句の集計関数オペランド未対応、(4)Bean Validation用@AssertTrueメソッドがJacksonのgetterとしてレスポンスJSONへ漏れていた問題。実機E2E検証（PostgreSQL/MySQL）でSQL生成・実行・リバースエンジニアリング・異常系を確認、BOOLEAN型リテラルの4方言動作を確認。**完了報告後、承認前レビューで8件の指摘に対応**: (1)実行可能アーティファクトはWAR（運用ルール確定、Gradle設定変更なし、feedback_deployment_artifact.mdに記録）、(2)FROM/JOINタブをFROMタブ1つに統合しタブ順序をFROM/SELECT/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSETに変更、(3)FROM/JOINタブのレイアウトを一行化、(4)SELECT/WHERE/HAVING/ORDER BYタブのレイアウトを一行化、(5)FROMタブの駆動表とJOINの縦間隔追加、(6)JOIN条件右辺の候補列を左辺と同一化（FROM+全JOIN済み列から選択可能に）、(7)FROM/JOINタブの実装をQueryBuilderFromTab 1ファイル・1コンポーネントに統合、(8)クエリビルダーからの逆遷移でSQLが引き継がれないバグ修正（QueryExecutionPage.tsxのrouter state受信ロジック欠落、SavedQueryEditorPage.tsxのgetSavedQuery非同期競合状態）。最終状態: バックエンド全384件・フロントエンド全222件成功

## Current Unit - Stage Progress (UNIT-08)
- [x] Functional Design — EXECUTE、COMPLETED（承認 2026-07-26T20:26:00Z。unit-08-functional-design-plan.mdの全8問に推奨どおり全問Aで回答: 失敗実行は記録対象外（既存QueryExecutionRecord踏襲）、接続選択→履歴一覧の2画面構成、実行者スコープはロールに応じフェイルクローズ、履歴閲覧はアクセス権を再判定しない（記録の不変性）、SQLテキスト検索は部分一致、保存クエリ名表示、router state経由の画面遷移、Spring Data JPA標準Pageable採用。business-logic-model.md, business-rules.md（BR-QUERYHISTORY-01〜10）, domain-entities.md, frontend-components.mdを作成。承認前レビューで2件の矛盾を発見・修正: (1)Pagination（1-indexed）とPageable（0-indexed）の基準不一致の明記、(2)スキーマ絞込セレクタが「現在アクセス可能なスキーマ」ではBR-04（アクセス権不問の閲覧）と矛盾するため、履歴実績ベースのDISTINCT取得に是正（BR-QUERYHISTORY-10新設、新規API追加）。**NFR Design中の指摘により追加遡及修正（2026-07-26T21:02:00Z）**: 接続選択画面もUNIT-06既存の「現在アクセス可能な接続一覧」ではなくBR-04と矛盾するため、履歴実績ベースの新規API（`GET /api/query-history/connections`）に是正（BR-QUERYHISTORY-11新設、QueryHistoryConnectionView DTO追加、削除済み接続のプレースホルダー表示を追加））
- [x] NFR Requirements — EXECUTE、COMPLETED（承認 2026-07-26T20:53:00Z。unit-08-nfr-requirements-plan.mdの全5問に推奨どおり全問Aで回答: JpaSpecificationExecutorによる動的絞込クエリ（プロジェクト内初導入）、(connection_id, executed_at)複合インデックス新設（既存query_execution_recordテーブルにconnection_idのインデックスがなかったため）、絞込パラメータの入力検証、Controller層でのロール判定によるフェイルクローズ、findAllByIdInによる名前解決（キャッシュなし）。nfr-requirements.md（Security Baseline全15ルール評価）、tech-stack-decisions.mdを作成。承認前レビューで事実誤認を発見・修正: 「UNIT-05/06で確立したロール判定パターンを踏襲」という記述が誤りで、実際には業務ロジック内でのロール分岐に前例はなく本ユニットが初導入と訂正。JWTのroleクレーム参照方法（principal.getClaimAsString("role")）を具体化）
- [x] NFR Design — EXECUTE、COMPLETED（承認 2026-07-26T21:33:00Z。unit-08-nfr-design-plan.mdの全4問に推奨どおり全問Aで回答: Bean Validationでの絞込パラメータ検証、QueryHistoryServiceへの3責務集約、単一QueryHistoryController（3エンドポイント）、Controller層のみでの実行者スコープ判定。nfr-design-patterns.md, logical-components.mdを作成。承認前レビューで2件発見・修正: (1)ServiceシグネチャがisAdmin（ロール由来の値）をそのまま受け取っており「ロール判定ロジックをServiceに持ち込まない」という方針と矛盾していたため、executedByFilter（絞込済みの実行者ID）を渡す形に是正、(2)listSchemasが実行者スコープの絞込を受け取っておらず一般ユーザが他ユーザのスキーマ名を知りうる情報漏洩リスクを発見・修正（listConnectionsと同じexecutedByFilter方式に統一）。さらに、この情報漏洩パターンが他ユニット(UNIT-01〜07)にも存在しないか横断点検（Exploreエージェント）を実施し、問題なしと確認）
- [x] Infrastructure Design — SKIP（判定 2026-07-26T21:34:00Z。新規DB永続化なし（既存query_execution_recordテーブルの閲覧のみ）、新規外部サービス依存なし、既存インフラ（UNIT-03/04/06）の再利用のみのため新規インフラ設計不要）
- [ ] Code Generation — EXECUTE

## Current Unit Progress
- [x] UNIT-01 デザインシステム基盤 — COMPLETED（承認 2026-07-20T19:26:00Z）
- [x] UNIT-02 ユーザ登録・認証 — COMPLETED（承認 2026-07-21T00:15:00Z）
- [x] UNIT-03 RDBMSセットアップ — COMPLETED（承認 2026-07-22T06:10:00Z）
- [x] UNIT-04 アクセス制御 — COMPLETED（承認 2026-07-24T09:00:00Z）
- [x] UNIT-05 マスタメンテナンス — COMPLETED（承認 2026-07-24T13:36:00Z）
- [x] UNIT-06 クエリ保存・実行 — COMPLETED（承認 2026-07-26T05:21:00Z）
- [x] UNIT-07 クエリビルダー — COMPLETED（承認 2026-07-26T19:56:00Z）
- [ ] UNIT-08 クエリ履歴 — IN PROGRESS（Functional Design承認済み、NFR Requirementsへ）
- [ ] UNIT-09 監査ログ閲覧
- [ ] UNIT-10 CI/CD
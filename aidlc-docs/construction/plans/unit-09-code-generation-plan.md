# UNIT-09 監査ログ閲覧 - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-9.1（監査ログの閲覧・絞り込み）
- **対応要件**: requirements.md §6.1〜6.3
- **対応コンポーネント**: COMP-18（閲覧機能）
- **前提ユニット**: UNIT-02（`AuditLogEntry`エンティティ・テーブル、`AuditEventType`/`ResultStatus`、`AuditLogEntryRepository`、`GlobalExceptionHandler`、`AdminUserController`の`GET /api/admin/users`）, UNIT-03（`RdbmsConnectionController`の`GET /api/admin/rdbms-connections`）
- **依存関係**: UNIT-04の`EffectivePermissionResolver`には依存しない（本ユニットは管理者専用でありデータ絞込を行わないため）
- **本ユニットが所有するデータ**: なし（`AuditLogEntry`はUNIT-02所有、本ユニットは参照専用。新規追加は`(connection_id, occurred_at)`複合インデックスのみ）
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時の実装判断

- パッケージ構成は既存の`cherry.mastermeister.audit`（UNIT-02実装済み）に追加する。閲覧用Service・Specificationクラスはパッケージ直下、DTOは`{package}.dto`、コントローラは`{package}.api`に配置する
- アクセス制御は既存の`SecurityConfig`の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールでカバーされるため、`AuditLogController`には独自のロール判定ロジックを実装しない（UNIT-08のようなController層でのフェイルクローズ変換は不要）
- `AuditLogQueryService`が絞込・ページング・名前解決の3責務を担う。動的クエリ構築は`AuditLogSpecifications`（静的ファクトリメソッド集）に分離する。記録専用の既存`AuditLogService`は変更しない
- `AuditLogEntryRepository`（UNIT-02既存、現状は空の`JpaRepository`）に`JpaSpecificationExecutor<AuditLogEntry>`を追加実装する（既存インターフェースを直接修正、新規ファイルは作らない）
- 新規マイグレーション`V18__add_index_audit_log_entry_connection_occurred_at.sql`を追加する（現時点の最終マイグレーションはV17のため次番号はV18、既存マイグレーションは変更しない）
- 絞込パラメータの検証はUNIT-08と同じ判断基準（既存GETエンドポイントとの一貫性）を適用する。Controller側で個々の`@RequestParam`を受け取り検証する方式とし、Bean Validation付きの`@ModelAttribute`DTOバインドは採用しない（UNIT-08のCode Generation時の実装判断を踏襲）。新規例外`AuditLogInvalidParameterException`（400）をページサイズ上限超過・日時範囲相関違反時に送出する
- フロントエンドは単一の`AuditLogPage`（`/audit-log`）構成。UNIT-01で仮予約済みの`auditLog`ナビ項目をそのまま使用する（navigation.tsへの変更は不要）
- 対象ユーザ・対象接続セレクタの選択肢は、既存のAPIクライアント関数`listUsers()`（`frontend/src/api/adminUsers.ts`）・`listConnections()`（`frontend/src/api/rdbmsConnections.ts`）をそのまま再利用する（新規APIクライアント関数の追加は不要、確認済み）
- 監査ログ一覧画面はdesign-system既存の`DataTable`・`Pagination`・`Select`・`TextInput`を組み合わせる。絞込条件はUNIT-08の承認前レビュー対応と同じ縦並びレイアウト（`FormField`・`FilterBar`は使用しない、独自のCSS Moduleで`label`＋入力欄を横並びにした行を縦に積む）を最初から採用する
- フロントエンドのページ番号は`Pagination`（1-indexed）↔`Pageable`（0-indexed）の変換を`AuditLogPage`内の1箇所に限定する
- 画面遷移導線は設けない（BR-AUDITVIEW-11）。監査ログ閲覧自体の監査記録も行わない（NFR Requirements Q2=A）
- 新規外部ライブラリ依存・Build Configuration変更なし。PBT対象外（stories.mdに明記済み）

## 計画チェックリスト

### 1. Business Logic Generation

- [x] Step 1.1: DTOクラス群を作成する（`cherry.mastermeister.audit.dto`）: `AuditLogEntryResponse`（`id`, `occurredAt`, `userId`, `userDisplayName`, `connectionId`, `connectionDisplayName`, `eventType`, `targetResource`, `resultStatus`, `detail`）, `AuditLogSearchCriteria`（`occurredAtFrom`, `occurredAtTo`, `eventType`, `userId`, `connectionId`, `resultStatus`。Service層内部用）, `AuditLogPageResponse`（`Page<AuditLogEntryResponse>`をUNIT-08の`QueryHistoryPageResponse`と同様の独自の軽量ラッパーへ変換）
- [x] Step 1.2: `AuditLogSpecifications`（`cherry.mastermeister.audit`）を作成する（`Specification<AuditLogEntry>`の静的ファクトリメソッド: `occurredAtFrom`, `occurredAtTo`, `eventTypeEquals`, `userIdEquals`, `connectionIdEquals`, `resultStatusEquals`。nfr-design-patterns.md §2.1）
- [x] Step 1.3: `AuditLogEntryRepository`（UNIT-02既存、`cherry.mastermeister.audit.repository`）を修正する: `JpaSpecificationExecutor<AuditLogEntry>`を追加実装（logical-components.md §2）
- [x] Step 1.4: `AuditLogQueryService`（`cherry.mastermeister.audit`）を作成する
  - `listAuditLog(AuditLogSearchCriteria criteria, Pageable pageable): Page<AuditLogEntryResponse>` — `AuditLogSpecifications`で動的組立→`findAll(spec, pageable)`→`userId`/`connectionId`の一括解決（`UserRepository.findAllById`, `RdbmsConnectionRepository.findAllById`）→`AuditLogEntryResponse`へ変換（business-logic-model.md §2・§6）

### 2. Business Logic Unit Testing

- [x] Step 2.1: `AuditLogQueryServiceTest`を作成する（Mockito。各絞込条件の組み合わせ、対象ユーザ名解決（正常・不明ユーザ）、対象接続名解決（正常・削除済み接続））— 4件
- [x] Step 2.2: `AuditLogSpecificationsTest`を作成する（`@DataJpaTest`。各ファクトリメソッド単体、および複数条件の組み合わせでの絞込結果確認）— 6件

### 3. Business Logic Summary

- [x] Step 3.1: `aidlc-docs/construction/unit-09/code/business-logic-summary.md`を作成する

### 4. API Layer Generation

- [x] Step 4.1: `AuditLogController`（`cherry.mastermeister.audit.api`）を作成する（単一エンドポイント: `GET /api/admin/audit-log`。既存の`RdbmsConnectionController`と同様、クラスJavadocに「既存のSecurityFilterChain設定により管理者ロール必須」である旨のコメントを付与、logical-components.md §1）
- [x] Step 4.2: 新規例外`AuditLogInvalidParameterException`（400）を`cherry.mastermeister.common.exception`に追加し、ページサイズ上限超過・日時範囲の相関チェック違反時に送出する。`messages_ja.properties`/`messages_en.properties`に`error.AUDIT_LOG_INVALID_PARAMETER`を追加
- [x] Step 4.3: `GlobalExceptionHandler`への追加要否を確認する — 新規例外は`ApiException`のサブクラスであり既存の汎用ハンドラで処理されるため追加不要と確認（UNIT-05〜08と同じ結論）
- [x] Step 4.4: SecurityFilterChain設定への`/api/admin/audit-log/**`ルール追加要否を確認する — 既存の`/api/admin/**`→`hasRole("ADMIN")`ルールでカバーされるため追加不要と確認
- [x] Step 4.5: OpenAPI/Swagger UIへの反映を確認する（既存の自動生成のみ、追加実装不要）

### 5. API Layer Unit Testing

- [x] Step 5.1: `@WebMvcTest`で`AuditLogControllerTest`を作成する（正常系、絞込パラメータ違反時400応答、一般ユーザ（`ADMIN`ロールなし）のリクエストが403で拒否されること、未認証リクエストが401で拒否されることの確認）— 5件全件成功

### 6. API Layer Summary

- [x] Step 6.1: `aidlc-docs/construction/unit-09/code/api-layer-summary.md`を作成する

### 7. Repository Layer Generation

（Step 1.3で完了済み。UNIT-02既存Repositoryの拡張のため独立ステップは設けない）

### 8. Frontend Components Generation

- [x] Step 8.1: APIクライアント`frontend/src/api/auditLog.ts`を作成する（監査ログ一覧取得の関数、型定義。対象ユーザ・対象接続の一覧取得は既存の`listUsers()`・`listConnections()`を再利用するため新規関数は追加しない）
- [x] Step 8.2: `AuditLogPage`（`frontend/src/pages/`）を作成する（frontend-components.md。単一画面、`DataTable`＋`Pagination`＋絞込条件（縦並びレイアウト、UNIT-08の承認前レビュー対応後の構成を最初から採用）、`Pagination`↔`Pageable`のページ番号変換）。画面遷移導線・詳細モーダルは設けない
- [x] Step 8.3: `App.tsx`のルーティングに`/audit-log`を追加する（`ProtectedRoute`配下）
- [x] Step 8.4: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'auditLog'`を追加する
- [x] Step 8.5: i18nリソース（`common.json`の`ja`/`en`）に`auditLog.*`関連キーを追加する（`nav.auditLog`はUNIT-01で追加済み。イベント種別28値の表示名`auditLog.eventType.*`を含む）

### 9. Frontend Components Unit Testing

- [x] Step 9.1: `auditLog.test.ts`（APIクライアント）を作成する — 2件
- [x] Step 9.2: `AuditLogPage.test.tsx`を作成する（一覧表示、絞込条件変更時の再取得、対象ユーザ・対象接続セレクタの選択肢取得元確認）— 3件。`HomePage.test.tsx`の「準備中」バッジ数（1→0、全カード実装済みに）を反映

### 10. Frontend Components Summary

- [x] Step 10.1: `aidlc-docs/construction/unit-09/code/frontend-summary.md`を作成する

### 11. Database Migration Scripts

- [x] Step 11.1: `V18__add_index_audit_log_entry_connection_occurred_at.sql`を作成する（`(connection_id, occurred_at)`複合インデックス、logical-components.md §7）。`AuditLogSpecificationsTest`（`@DataJpaTest`）経由でマイグレーション適用を確認済み

### 12. Documentation Generation

- [x] Step 12.1: `backend/README.md`を更新する（UNIT-09概要: 監査ログ閲覧、`/api/admin/audit-log`エンドポイント）
- [x] Step 12.2: `frontend/README.md`を更新する（UNIT-09の新規画面をpages概要に追記）

### 13. Deployment Artifacts

- [x] Step 13.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に追加のインフラが不要であることを確認した（既存構成のまま変更なし）

### 14. 最終ビルド検証

- [x] Step 14.1: **検証チェックポイント**: `./gradlew :backend:build`（全427件成功）、`npm test`（frontend、全60ファイル246件成功）、`npm run build`（frontend、成功）を確認した
- [x] Step 14.2: devenv（H2内部DB＋PostgreSQL接続、`bootWar`で明示的にビルド、`feedback_deployment_artifact.md`の運用ルールに従う）で、管理者ユーザ（新規ブートストラップ）・一般ユーザ（新規登録・承認）それぞれのJWTを用いてAPI経由（curl）で以下を確認した:
  - 監査ログ一覧の絞込・ページングの正常動作（イベント種別・対象ユーザ・対象接続の各絞込）
  - 一般ユーザのJWTでのアクセスが403で拒否されること（BR-AUDITVIEW-03）を確認
  - 日時範囲の相関検証（開始>終了で400 `AUDIT_LOG_INVALID_PARAMETER`）、ページサイズ上限超過（500件指定）時の400応答を確認
  - 接続削除後、「(削除済み接続)」のプレースホルダーが表示されること（BR-AUDITVIEW-07）を確認
  - **実機検証時の手順ミス（実装バグではない）**: (1) 内部DB（メタデータDB）はH2であり、devenvのPostgreSQL等は対象RDBMS接続専用という区別を誤り、当初PostgreSQLへの接続切替を試みた、(2) `${MM_APP_XXX:default}`プレースホルダの上書きにはコマンドライン引数`--MM_APP_XXX=value`（環境変数名そのまま）が必要で、`--mm.app.xxx=value`形式では反映されず意図せず既存DBファイルを再利用してしまった、(3) ユーザ登録APIのパス・リクエスト構造を誤り、(4) パスワード漏洩チェックで単純なパスワードが拒否された。いずれも検証手順上の見落としでUNIT-09実装自体には問題なし。`feedback_deployment_artifact.md`に教訓を追記
- [x] Step 14.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）はUNIT-02〜08と同じくNVD APIキー未設定のため実施見送り

## Story Traceability

- STORY-9.1（監査ログの閲覧・絞り込み） — Step 1.1〜1.4, 8.1〜8.2

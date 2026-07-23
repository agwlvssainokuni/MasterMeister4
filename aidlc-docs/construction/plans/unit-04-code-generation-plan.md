# UNIT-04 アクセス制御 - Code Generation 計画

## Unit Context

- **対応ストーリー**: STORY-2.3（権限設定）, STORY-2.4（実効権限判定）, STORY-2.5（YAML入出力）, STORY-2.6（グループ管理）
- **対応要件**: FR-2.3〜FR-2.15
- **対応コンポーネント**: COMP-10（`GroupService`/`PermissionService`、`AccessControlService`から改称・分割。unit-04/nfr-design/logical-components.md §1・§2参照）, COMP-11（`EffectivePermissionResolver`）, COMP-12（`PermissionYamlService`）
- **前提ユニット**: UNIT-01（design-systemコンポーネント）, UNIT-02（JWT認証・ロールチェック、`AuditEventPublisher`/`AuditLogService`、`GET /api/admin/users`）, UNIT-03（`RdbmsConnection`, `SchemaIntrospectionService.getSchema()`によるスキーマ・テーブル・カラム・制約情報）
- **依存関係**: 本ユニットは`SecurityFilterChain`（`/api/admin/**`）、`AuditEventPublisher`/`AuditEventType`、UNIT-03の`SchemaIntrospectionService.getSchema(connectionId)`（`canCreate`/`canDelete`判定における主キー列情報の取得、`EffectivePermissionResolver`から利用）をそのまま利用・拡張する。UNIT-03の`SchemaIntrospectionService.refreshSchema()`に対しては`@CacheEvict`アノテーションの追加のみを行う（既存ロジックは変更しない）
- **本ユニットが所有するデータ**: `Group`, `GroupMembership`（`group`パッケージ）、`AccessPermission`（`permission`パッケージ）
- **参照ドキュメント**: functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md、nfr-requirements/{nfr-requirements,tech-stack-decisions}.md、nfr-design/{nfr-design-patterns,logical-components}.md

## Part 1計画作成時の実装判断

- パッケージ構成は`cherry.mastermeister.group`・`cherry.mastermeister.permission`の2つとし、既存ユニット（`rdbmsconnection`, `registration`）と同様に、サービスクラスはパッケージ直下、エンティティは`{package}.entity`、リポジトリは`{package}.repository`、DTOは`{package}.dto`、コントローラは`{package}.api`に配置する
- `AccessPermission`の`tableName`/`columnName`は「該当階層なし」を空文字列（`''`）で永続化する（nfr-design-patterns.md §3.1）。エンティティのgetterで空文字列⇄`null`の変換を吸収し、`domain-entities.md`が想定するnullable項目としての振る舞いをAPI・業務ロジック層に対して維持する
- `EffectivePermissionResolver.canCreate()`/`canDelete()`が必要とする主キー列情報は、`AccessPermission`からUNIT-03のエンティティへ外部キーを張らず、UNIT-03の`SchemaIntrospectionService.getSchema(connectionId)`を呼び出しスキーマ名・テーブル名で対象`SchemaTable`を検索して取得する（`domain-entities.md` §1の「外部キーではなく文字列で独立保持」という設計と整合）
- 本ユニットが、要件定義時点（NFR-5.1）で識別されたPBT対象プロパティ（business-logic-model.md §5）を持つ最初のユニットであるため、jqwik（UNIT-02でtestImplementation追加済み、実使用はこれが初回）による実際のプロパティベーステストをここで実装する

## 計画チェックリスト

### 1. Build Configuration

- [x] Step 1.1: `backend/build.gradle.kts`に依存関係を追加する: `implementation("com.github.ben-manes.caffeine:caffeine")`, `implementation("org.springframework.boot:spring-boot-starter-cache")`（tech-stack-decisions.md §1・§2）— WebSearchでCaffeine最新安定版3.2.4を確認の上、明示バージョン指定で追加
- [x] Step 1.2: `backend/src/main/resources/application.yml`にCaffeineキャッシュ設定を追加する（`spring.cache.type: caffeine`, `spring.cache.cache-names: effectivePermission`, `spring.cache.caffeine.spec: maximumSize=10000,expireAfterWrite=30m`。logical-components.md §5）
- [x] Step 1.3: `MasterMeisterApplication`に`@EnableCaching`を付与する

### 2. Database Migration Scripts

- [x] Step 2.1: `backend/src/main/resources/db/migration/V12__create_group_table.sql`を作成する（domain-entities.md §2、`name`列にUNIQUE制約）— テーブル名は`V1__create_app_user_table.sql`の前例（H2予約語回避）に倣い`app_group`とした
- [x] Step 2.2: `V13__create_group_membership_table.sql`を作成する（domain-entities.md §3、`group_id`外部キー、`(group_id, user_id)`のUNIQUE制約）
- [x] Step 2.3: `V14__create_access_permission_table.sql`を作成する（domain-entities.md §1。`schema_name`/`table_name`/`column_name`はすべて`NOT NULL`とし「該当階層なし」を空文字列で表現（nfr-design-patterns.md §3.1）。`(connection_id, principal_type, principal_id, schema_name, table_name, column_name)`のUNIQUE制約、`(connection_id, principal_type, principal_id)`・`(connection_id, schema_name, table_name, column_name)`の複合INDEXを追加（nfr-design-patterns.md §2.2）。`connection_id`は`rdbms_connection(id)`へのON DELETE CASCADE外部キーとした（接続削除時に権限設定も削除、実装判断）
- [x] Step 2.4: 既存の`AuditEventType`（`cherry.mastermeister.audit.entity`）に`PERMISSION_CHANGED`, `GROUP_CREATED`, `GROUP_RENAMED`, `GROUP_DELETED`, `GROUP_MEMBER_ADDED`, `GROUP_MEMBER_REMOVED`, `PERMISSION_YAML_EXPORTED`, `PERMISSION_YAML_IMPORTED`を追加する（domain-entities.md §4。`audit_log_entry.connection_id`は既存カラムのため追加マイグレーション不要）
- [x] Step 2.5: **検証チェックポイント**: Flywayマイグレーションが後続のRepository層テスト実行時に正常適用されることを確認する（Step 3.5で実施、10件全件成功）

### 3. Repository Layer Generation

- [x] Step 3.1: enumを作成する: `PrincipalType`, `PrimaryPermission`（`cherry.mastermeister.permission.entity`）
- [x] Step 3.2: JPAエンティティ`AccessPermission`（`cherry.mastermeister.permission.entity`）を作成する（domain-entities.md §1の属性。`tableName`/`columnName`のgetter/setterで空文字列⇄`null`変換を行う）
- [x] Step 3.3: JPAエンティティ`Group`・`GroupMembership`（`cherry.mastermeister.group.entity`）を作成する（domain-entities.md §2〜3。`Group`→`GroupMembership`は`@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`。`GroupMembership.userId`は`registration`パッケージへの直接依存を避けIDのみ保持）
- [x] Step 3.4: Spring Data JPAリポジトリを作成する: `AccessPermissionRepository`（`cherry.mastermeister.permission.repository`、`findAllByConnectionIdAndPrincipalTypeAndPrincipalId`等）, `GroupRepository`・`GroupMembershipRepository`（`cherry.mastermeister.group.repository`、`findByName`/`findAllByUserId`等）
- [x] Step 3.5: **検証チェックポイント**: `@DataJpaTest`で基本CRUD・一意制約違反・`Group`削除時の`GroupMembership`カスケード削除を検証するテストを作成する — 実装時に`@EnableCaching`を`MasterMeisterApplication`に直接付与すると`@DataJpaTest`スライスで`CacheManager`不在により起動失敗する問題を発見し、`common.config.CacheConfig`（独立`@Configuration`クラス）へ切り出して解消。10テスト全件成功

### 4. Repository Layer Summary

- [x] Step 4.1: `aidlc-docs/construction/unit-04/code/repository-layer-summary.md`を作成する（エンティティ・リポジトリ・マイグレーション一覧、テスト結果）

### 5. Business Logic Generation

- [x] Step 5.1: `GroupService`（`cherry.mastermeister.group`）を作成する（`createGroup`/`renameGroup`/`deleteGroup`/`addUserToGroup`/`removeUserFromGroup`/`listGroups`/`listMembers`、business-logic-model.md §4、BR-ACCESS-11。`deleteGroup()`は`@Transactional`とし、`AccessPermissionRepository`経由で`principalType=GROUP`かつ`principalId=`当該グループの行を削除してから`Group`本体を削除する。全mutationメソッドに`@CacheEvict`を付与）— `listMembers`はUser詳細解決のため`registration.repository.UserRepository`を直接注入（実装判断、既存UserRegistrationServiceに単一ID検索の公開APIがなかったため）
- [x] Step 5.2: `PermissionService`（`cherry.mastermeister.permission`）を作成する（`setPermission`（upsert）/`unsetPermission`（削除、対象なしは冪等no-op）/`listPermissions`、BR-ACCESS-01。両mutationメソッドに`@CacheEvict`を付与）
- [x] Step 5.3: `EffectivePermissionResolver`（`cherry.mastermeister.permission`）を作成する（`resolvePrimary`/`canCreate`/`canDelete`、BR-ACCESS-04〜08。個別設定優先→グループ合成（より許可的な方）を実装。`canCreate`/`canDelete`はUNIT-03の`SchemaIntrospectionService.getSchema()`から主キー列情報を取得。3メソッドに`@Cacheable`を付与）
- [x] Step 5.4: `PermissionYamlService`（`cherry.mastermeister.permission`）を作成する（`exportToYaml`/`importFromYaml`、BR-ACCESS-09〜10。`jackson-dataformat-yaml`使用。検証フェーズ（プリンシパル解決・重複エントリチェック）とDB反映フェーズを分離、`@CacheEvict`を付与）
- [x] Step 5.5: `GroupService`・`PermissionService`・`PermissionYamlService`から`AuditEventPublisher`経由でイベント発行を組み込み済み（各メソッド実装に含む）
- [x] Step 5.6: UNIT-03の`SchemaIntrospectionService.refreshSchema()`に`@CacheEvict`を追加。UNIT-03の`nfr-design/logical-components.md`・`code/business-logic-summary.md`に追記注記を追加

### 6. Business Logic Unit Testing

- [x] Step 6.1: `GroupServiceTest`を作成する（Mockito。作成・改名・削除時のカスケード順序、重複名エラー、所属追加・削除、所属解除の冪等性）— 11テスト成功
- [x] Step 6.2: `PermissionServiceTest`を作成する（upsert・削除、`columnName`設定時の補助権限強制`false`）— 実装時に`AccessPermission`のコンストラクタでもカラム階層時の補助権限強制falseが未適用というバグを発見・修正（`updatePermission()`のみ適用されていた）。5テスト成功
- [x] Step 6.3: `EffectivePermissionResolverTest`を作成する（BR-ACCESS-04の確認済み具体例5ケースをexample-basedテストで再現、`canCreate`/`canDelete`の境界値）— 10テスト成功
- [x] Step 6.4: `EffectivePermissionResolverPropertyTest`をjqwikで作成する（business-logic-model.md §5.1: 階層優先の不変条件、個別設定優先の不変条件、グループ合成の単調性、作成可否・削除可否判定の同値性）— 本ユニットが実際のjqwikプロパティテスト初適用。5プロパティ全件成功
- [x] Step 6.5: `PermissionYamlServiceTest`を作成する（正常系のエクスポート/インポート、プリンシパル未解決・重複エントリでの全体拒否、部分適用が起きないことの確認）— 6テスト成功
- [x] Step 6.6: `PermissionYamlServicePropertyTest`をjqwikで作成する（business-logic-model.md §5.2: ラウンドトリップ特性、重複拒否の原子性）— Mockitoで簡易インメモリリポジトリを構築し実際のexport→import→export往復を検証。2プロパティ全件成功

### 7. Business Logic Summary

- [x] Step 7.1: `aidlc-docs/construction/unit-04/code/business-logic-summary.md`を作成する（作成したサービス一覧、責務、PBTプロパティ実装内容、テスト結果）

### 8. API Layer Generation

- [x] Step 8.1: DTOを作成する（`cherry.mastermeister.group.dto`: `GroupRequest`, `GroupResponse`, `GroupMemberRequest`, `GroupMemberResponse`／`cherry.mastermeister.permission.dto`: `PermissionEntryRequest`, `PermissionEntryResponse`, `PermissionImportRequest`（`@Size(max=1_048_576)`、tech-stack-decisions.md §9））— `PermissionImportResult`は作成しなかった（実装判断: 成功時は204 No Content、失敗時は`PermissionYamlImportRejectedException`経由のBR-API-01エラーレスポンスで十分表現できるため、専用DTOはYAGNIと判断）
- [x] Step 8.2: `GroupController`（`cherry.mastermeister.group.api`）を作成する（logical-components.md §1のエンドポイント一覧、既存`SecurityFilterChain`の`/api/admin/**`にそのまま合致）
- [x] Step 8.3: `PermissionController`（`cherry.mastermeister.permission.api`）を作成する（logical-components.md §2のエンドポイント一覧。`DELETE`はクエリパラメータで対象キーを指定、frontend-components.md §2の注記のとおり。YAMLエクスポートは`Content-Disposition: attachment`付きでダウンロード応答）
- [x] Step 8.4: OpenAPI/Swagger UIへの反映を確認する（既存の自動生成のみ、追加実装不要）

### 9. API Layer Unit Testing

- [x] Step 9.1: `@WebMvcTest`で`GroupControllerTest`を作成する（管理者ロールチェック、バリデーションエラー、削除エンドポイント動作）— 7テスト成功
- [x] Step 9.2: `@WebMvcTest`で`PermissionControllerTest`を作成する（管理者ロールチェック、`DELETE`のクエリパラメータ必須確認、YAML importのサイズ超過エラー）— 実装時に必須クエリパラメータ欠落（`MissingServletRequestParameterException`）が`GlobalExceptionHandler`で未捕捉のまま500になるバグを発見し、`MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException`をVALIDATION_ERROR(400)として扱うハンドラを追加して解消（UNIT-02のGlobalExceptionHandlerへの機能追加、api-layer-summary.mdに追記注記）。Spring 7で`MockMvcResultMatchers.isUnprocessableEntity()`も非推奨と判明し`isUnprocessableContent()`へ置換。8テスト成功

### 10. API Layer Summary

- [x] Step 10.1: `aidlc-docs/construction/unit-04/code/api-layer-summary.md`を作成する（エンドポイント一覧、テスト結果）

### 11. Frontend Components Generation

- [ ] Step 11.1: APIクライアント`frontend/src/api/groups.ts`を作成する（一覧・作成・改名・削除・所属ユーザ一覧・追加・削除の各関数、`http.ts`共通ラッパー利用）
- [ ] Step 11.2: APIクライアント`frontend/src/api/permissions.ts`を作成する（一覧・設定（upsert）・解除・YAMLエクスポート・インポートの各関数）
- [ ] Step 11.3: `GroupManagementPage`（`frontend/src/pages/`）を作成する（frontend-components.md §1、一覧・作成/改名フォームModal・所属ユーザ管理Modal・削除確認ConfirmDialog）
- [ ] Step 11.4: `AccessPermissionTreePage`（`frontend/src/pages/`）を作成する（frontend-components.md §2、プリンシパル選択部＋スキーマ／テーブル／カラムのツリー展開UI、行単位即時保存、YAMLエクスポート/インポートModal）
- [ ] Step 11.5: `App.tsx`のルーティングに`/groups`（`GroupManagementPage`、`ProtectedRoute`配下）・`/permissions/:connectionId`（`AccessPermissionTreePage`、同）を追加する
- [ ] Step 11.6: `RdbmsConnectionListPage`の行アクションに「権限設定」Linkを追加する（frontend-components.md §3、`/permissions/{connectionId}`へ遷移）
- [ ] Step 11.7: `HomePage.tsx`の`IMPLEMENTED_KEYS`に`'groups'`を追加する（frontend-components.md §4）
- [ ] Step 11.8: i18nリソース（`common.json`/`design-system.json`の`ja`/`en`）に本ユニットの画面文言を追加する

### 12. Frontend Components Unit Testing

- [ ] Step 12.1: `GroupManagementPage.test.tsx`・`AccessPermissionTreePage.test.tsx`を作成する（Vitest + RTL、フォーム操作・ツリー展開・即時保存・YAML入出力のAPI呼び出しモック）
- [ ] Step 12.2: `groups.test.ts`・`permissions.test.ts`（APIクライアント）を作成する
- [ ] Step 12.3: `RdbmsConnectionListPage.test.tsx`・`HomePage.test.tsx`の更新箇所（権限設定リンク追加、実装済みバッジ数の変化）を反映する

### 13. Frontend Components Summary

- [ ] Step 13.1: `aidlc-docs/construction/unit-04/code/frontend-summary.md`を作成する（作成した画面・コンポーネント一覧、テスト結果）

### 14. Documentation Generation

- [ ] Step 14.1: `backend/README.md`を更新する（Caffeineキャッシュ依存関係の追記）
- [ ] Step 14.2: `frontend/README.md`を更新する（新規ページ・ルーティングの追記、必要であれば）

### 15. Deployment Artifacts

- [ ] Step 15.1: `devenv/docker-compose.yml`を確認し、本ユニットの動作確認に追加のインフラ（新規コンテナ等）が不要であることを確認する（キャッシュはアプリ内蔵のCaffeineのため追加コンポーネント不要見込み）

### 16. 最終ビルド検証

- [ ] Step 16.1: **検証チェックポイント**: `./gradlew :backend:build`（全ユニットテスト成功、jqwikプロパティテスト含む）、`./gradlew :backend:test`、`npm test`（frontend）、`npm run build`（frontend）がすべて成功することを確認する
- [ ] Step 16.2: `./gradlew :backend:bootWar`で統合WARを生成し、`java -jar`起動。devenvの対象RDBMS（スキーマ取込済み接続）に対し、グループ作成・ユーザ追加、権限設定（スキーマ/テーブル/カラム各階層）、YAMLエクスポート/インポート、実効権限判定（個別設定優先・グループ合成）の一連の操作をcurlで実行し、想定どおりの実効権限が算出されることを確認する
- [ ] Step 16.3: OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）を実行する（NVD APIキー未設定の場合は既知の制約として記録し実施見送りとする。新規追加のCaffeine依存も対象に含める）

---

## Story Traceability

- STORY-2.3（権限設定）: Step 3, 5.2, 8, 11.4 で実装
- STORY-2.4（実効権限判定）: Step 5.3, 6.3, 6.4 で実装
- STORY-2.5（YAML入出力）: Step 5.4, 6.5, 6.6, 8, 11.4 で実装
- STORY-2.6（グループ管理）: Step 3, 5.1, 8, 11.3 で実装

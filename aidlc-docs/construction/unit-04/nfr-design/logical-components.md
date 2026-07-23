# UNIT-04 アクセス制御 - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.group`・`cherry.mastermeister.permission`の2つ（frontend-components.mdの訂正、unit-of-work.mdへの遡及修正を反映）。

---

## 1. グループドメイン（`cherry.mastermeister.group`）

### GroupService（COMP-10の一部）
- business-logic-model.md §4のグループ管理フローを実装
- `createGroup(name): GroupId` / `renameGroup(groupId, name): void` / `deleteGroup(groupId): void`
- `deleteGroup()`は`@Transactional`とし、`AccessPermissionRepository`（`permission`パッケージ）経由で当該グループを`principalId`とする行を削除してから`Group`本体を削除する（nfr-design-patterns.md §1.2、BR-ACCESS-11）
- `addUserToGroup(groupId, userId): void` / `removeUserFromGroup(groupId, userId): void`
- 上記すべてのmutationメソッドに`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を付与する（nfr-design-patterns.md §2.1）

### Group（エンティティ）
- domain-entities.md §2のとおり: `id`, `name`（一意）, `createdAt`
- `GroupMembership`への`@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`（Group削除時にDBレベルでカスケード削除される）

### GroupMembership（エンティティ）
- domain-entities.md §3のとおり: `id`, `groupId`（外部キー）, `userId`（`User.id`、外部キーだが`registration`パッケージへの直接依存は避けIDのみ保持）
- 一意制約: (`groupId`, `userId`)

### GroupController（`cherry.mastermeister.group`）
- `GET /api/admin/groups`（所属ユーザ数を含む一覧）
- `POST /api/admin/groups`, `PUT /api/admin/groups/{id}`, `DELETE /api/admin/groups/{id}`
- `GET /api/admin/groups/{id}/members`, `POST /api/admin/groups/{id}/members`, `DELETE /api/admin/groups/{id}/members/{userId}`
- 全エンドポイントとも、既存のSecurityFilterChain設定（`/api/admin/**`）により管理者ロール必須（Q5=A、追加設定不要）

---

## 2. 権限ドメイン（`cherry.mastermeister.permission`）

### PermissionService（COMP-10の一部）
- business-logic-model.md §1の権限設定フローを実装
- `setPermission(principal, resource, primary, auxiliary): void`（upsert。既存行があれば更新、なければ新規作成）
- 権限解除（「未設定」に戻す）メソッド: 対象キーに一致する`AccessPermission`行を削除する
- 上記2メソッドに`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を付与する

### EffectivePermissionResolver（COMP-11）
- business-logic-model.md §2の実効権限判定ロジックを実装（BR-ACCESS-04〜08）
- `resolvePrimary(userId, connectionId, resource): PrimaryPermission`、`canCreate(...)`、`canDelete(...)`の3メソッドに`@Cacheable(cacheNames = "effectivePermission")`を付与する
- UNIT-04時点ではREST APIとして公開しない内部Java API（Q4=A、tech-stack-decisions.md §4）。UNIT-05以降のサービスから直接呼び出される想定
- キャッシュ障害時は元のメソッド本体へフォールバックする（Spring Cacheのデフォルト挙動、nfr-design-patterns.md §1.3）

### PermissionYamlService（COMP-12）
- business-logic-model.md §3のYAML入出力フローを実装
- `exportToYaml(connectionId): String`
- `importFromYaml(connectionId, yaml): ImportResult`: 検証フェーズ（プリンシパル解決・重複チェック）とDB反映フェーズを分離する（nfr-design-patterns.md §1.1）。`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を付与する
- YAMLの読み書きは`jackson-dataformat-yaml`（`com.fasterxml.jackson.dataformat.yaml.YAMLFactory` + `ObjectMapper`）を使用する（tech-stack-decisions.md §5、新規依存追加なし）

### AccessPermission（エンティティ）
- domain-entities.md §1のとおり: `id`, `connectionId`, `principalType`, `principalId`, `schemaName`, `tableName`, `columnName`, `primaryPermission`, `createPermission`, `deletePermission`, `updatedAt`, `updatedBy`
- **`tableName`/`columnName`が「該当階層なし」の場合、SQL NULLではなく空文字列（`''`）を格納する**（nfr-design-patterns.md §3.1）。エンティティのgetterは空文字列を透過的に扱い、DTOへの変換時点で`null`相当（JSON上は`null`）に変換する
- 一意制約: (`connectionId`, `principalType`, `principalId`, `schemaName`, `tableName`, `columnName`)（すべて非NULL列のため、複合UNIQUE INDEXが正しく機能する）
- 追加インデックス: (`connectionId`, `principalType`, `principalId`)、(`connectionId`, `schemaName`, `tableName`, `columnName`)（nfr-design-patterns.md §2.2）

### PermissionController（`cherry.mastermeister.permission`）
- `GET /api/admin/permissions/{connectionId}?principalType=&principalId=`
- `PUT /api/admin/permissions/{connectionId}`（リクエストボディに対象キー＋設定値を含む）
- `DELETE /api/admin/permissions/{connectionId}?principalType=&principalId=&schemaName=&tableName=&columnName=`（クエリパラメータで対象キーを指定。`tableName`/`columnName`は任意）
- `GET /api/admin/permissions/{connectionId}/export`
- `POST /api/admin/permissions/{connectionId}/import`（リクエストボディ`{yaml: string}`。`PermissionImportRequest`に`@Size(max=...)`を付与、tech-stack-decisions.md §9）
- 全エンドポイントとも、既存のSecurityFilterChain設定（`/api/admin/**`）により管理者ロール必須（Q5=A、追加設定不要）

### DTO設計
- `GroupRequest`（`name`、Bean Validation）、`GroupResponse`（所属ユーザ数を含む）
- `GroupMemberRequest`（`userId`）、`GroupMemberResponse`（`userId`, `email`, `fullName`）
- `PermissionEntryRequest`（`principalType`, `principalId`, `schemaName`, `tableName`（任意）, `columnName`（任意）, `primaryPermission`, `createPermission`, `deletePermission`）
- `PermissionEntryResponse`（`PermissionEntryRequest`と同じ属性 + `updatedAt`, `updatedBy`）
- `PermissionImportRequest`（`yaml: String`、`@Size(max = ...)`でサイズ上限を実装。tech-stack-decisions.md §9で具体的な上限値を確定）
- `PermissionImportResult`（成功/失敗、拒否理由の概要）

---

## 3. UNIT-03への機能追加（Q8=A）

`cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService.refreshSchema()`に`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を追加する。UNIT-03は完了・承認済みのユニットであるため、この変更はUNIT-04のCode Generation時に実施し、UNIT-03の関連ドキュメント（nfr-design/logical-components.md、code/business-logic-summary.md）に取消線+訂正注記を追加する（既存の遡及修正パターンを踏襲）。

---

## 4. 監査ログ連携

`AuditEventPublisher`（UNIT-02で新設済み、`cherry.mastermeister.audit`）を通じて、domain-entities.md §4で定義した8種のイベント（`PERMISSION_CHANGED`, `GROUP_CREATED`, `GROUP_RENAMED`, `GROUP_DELETED`, `GROUP_MEMBER_ADDED`, `GROUP_MEMBER_REMOVED`, `PERMISSION_YAML_EXPORTED`, `PERMISSION_YAML_IMPORTED`）を発行する。

---

## 5. 設定（Caffeineキャッシュ設定）

`application.yml`に以下を追加する。

```yaml
spring:
  cache:
    type: caffeine
    cache-names: effectivePermission
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=30m
```

- `CaffeineCacheManager`はSpring Bootの自動構成（`spring.cache.type: caffeine`）により登録される。個別の`@Bean`定義は不要（tech-stack-decisions.md §1・§2）
- ~~メインクラスに`@EnableCaching`を付与する~~ 訂正（Code Generation時に発見）: `@EnableCaching`を`MasterMeisterApplication`に直接付与すると、`@DataJpaTest`等のテストスライスが`CacheAutoConfiguration`を除外する一方でアノテーション自体はルート設定クラスとして読み込まれてしまい、`CacheManager`不在で起動に失敗する。`cherry.mastermeister.common.config.CacheConfig`という独立した`@Configuration`クラスに切り出すことで、テストスライスのコンポーネントスキャン除外対象となり解消した

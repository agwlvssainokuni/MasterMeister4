# UNIT-04 アクセス制御 - Repository Layer Summary

`unit-04-code-generation-plan.md` Section 1〜4の実行結果サマリ。

## Build Configuration

- `backend/build.gradle.kts`: `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine:3.2.4`（WebSearchで最新安定版を確認）を追加
- `application.yml`: `spring.cache.type: caffeine`, `spring.cache.cache-names: effectivePermission`, `spring.cache.caffeine.spec: maximumSize=10000,expireAfterWrite=30m`
- `@EnableCaching`は`cherry.mastermeister.common.config.CacheConfig`（独立`@Configuration`クラス）に付与（実装時の判断、詳細は下記トラブルシューティング参照）

## Flywayマイグレーション（`backend/src/main/resources/db/migration/`）

| ファイル | 内容 |
|---|---|
| `V12__create_group_table.sql` | `app_group`テーブル（`name`列にUNIQUE制約）。テーブル名はH2の予約語"GROUP"を避け、V1の`app_user`前例に倣った |
| `V13__create_group_membership_table.sql` | `group_membership`テーブル。`(group_id, user_id)`のUNIQUE制約、両列とも`ON DELETE CASCADE`外部キー |
| `V14__create_access_permission_table.sql` | `access_permission`テーブル。`schema_name`/`table_name`/`column_name`はすべて`NOT NULL`とし「該当階層なし」を空文字列センチネル値で表現（NULL同士は複合UNIQUE制約上「等しくない」とみなされ一意性が機能しなくなるため）。`(connection_id, principal_type, principal_id, schema_name, table_name, column_name)`のUNIQUE INDEX、`(connection_id, principal_type, principal_id)`・`(connection_id, schema_name, table_name, column_name)`の参照系INDEXを追加。`connection_id`は`rdbms_connection(id)`への`ON DELETE CASCADE`外部キー（接続削除時に権限設定も削除、実装判断） |

既存の`audit_log_entry`テーブルはUNIT-02で用意済みのため追加マイグレーション不要。`AuditEventType`（`audit.entity`）に`PERMISSION_CHANGED`, `GROUP_CREATED`, `GROUP_RENAMED`, `GROUP_DELETED`, `GROUP_MEMBER_ADDED`, `GROUP_MEMBER_REMOVED`, `PERMISSION_YAML_EXPORTED`, `PERMISSION_YAML_IMPORTED`を追加した。

## JPAエンティティ

| パッケージ | エンティティ・enum | 内容 |
|---|---|---|
| `cherry.mastermeister.permission.entity` | `AccessPermission` | `tableName`/`columnName`のgetterで空文字列⇄`null`変換を行い、永続化層のセンチネル値をService層以上から隠蔽する。`updatePermission()`でカラム階層（`columnName`設定時）の補助権限を常に`false`へ強制（FR-2.5） |
| 同上 | `PrincipalType`, `PrimaryPermission` | enum。`PrimaryPermission`は`NONE < READ < UPDATE`の順（`ordinal()`比較でBR-ACCESS-05のグループ間合成に利用） |
| `cherry.mastermeister.group.entity` | `Group` | `GroupMembership`への`@OneToMany(cascade=ALL, orphanRemoval=true)`。`addMembership()`/`removeMembership()`で子エンティティとの関連を設定 |
| 同上 | `GroupMembership` | `userId`は`registration`パッケージへの直接依存を避けIDのみ保持（DB外部キー制約は設定） |

## Spring Data JPAリポジトリ

| パッケージ | リポジトリ | カスタムクエリメソッド |
|---|---|---|
| `cherry.mastermeister.permission.repository` | `AccessPermissionRepository` | `findAllByConnectionIdAndPrincipalTypeAndPrincipalId`（権限設定画面の一覧取得）, `findAllByConnectionId`, `deleteAllByPrincipalTypeAndPrincipalId`（BR-ACCESS-11カスケード削除）, `findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw`（upsert対象検索） |
| `cherry.mastermeister.group.repository` | `GroupRepository` | `findByName` |
| 同上 | `GroupMembershipRepository` | `findAllByUserId`（EffectivePermissionResolverのグループ解決用） |

## テスト結果

`@DataJpaTest`による2リポジトリ・10テストケース、すべて成功。`AccessPermission`のセンチネル値変換、複合UNIQUE制約違反の検出、`Group`削除時の`GroupMembership`カスケード削除、`findAllByUserId`による複数グループ所属の解決を確認済み。

## 実装時の判断・トラブルシューティング

- **`@EnableCaching`配置の問題を発見・修正**: 当初計画どおり`MasterMeisterApplication`に直接`@EnableCaching`を付与したところ、`@DataJpaTest`でのテスト実行時に`NoSuchBeanDefinitionException`（`CacheManager`が見つからない）で全件失敗した。`@DataJpaTest`はテストスライスとして`CacheAutoConfiguration`を除外する一方、`@EnableCaching`はルート設定クラス（`MasterMeisterApplication`）上のアノテーションであるため除外対象にならず、`CacheAspectSupport`が`CacheManager`を要求してコンテキスト起動に失敗する。`cherry.mastermeister.common.config.CacheConfig`という独立した`@Configuration`クラスに`@EnableCaching`を切り出すことで、通常の`@Component`としてテストスライスのコンポーネントスキャン除外対象となり解消した（nfr-design/logical-components.md §5に訂正注記を追加済み）

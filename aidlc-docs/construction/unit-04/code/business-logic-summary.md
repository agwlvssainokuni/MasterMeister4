# UNIT-04 アクセス制御 - Business Logic Summary

`unit-04-code-generation-plan.md` Section 5〜7の実行結果サマリ。

## 作成したサービス

| サービス | パッケージ | 責務 |
|---|---|---|
| `GroupService` | `cherry.mastermeister.group` | グループの作成・改名・削除・所属ユーザ管理（BR-ACCESS-11）。`deleteGroup()`は`AccessPermission`（`principalType=GROUP`）を先に削除してから`Group`本体を削除（`GroupMembership`は`@OneToMany`カスケードで削除） |
| `PermissionService` | `cherry.mastermeister.permission` | 権限設定のupsert・冪等な解除（BR-ACCESS-01）。COMP-10の一部（`AccessControlService`から改称・分割） |
| `EffectivePermissionResolver` | `cherry.mastermeister.permission` | 実効権限の判定・合成（BR-ACCESS-04〜08）。個別設定優先→グループ合成（より許可的な方）のロジック |
| `PermissionYamlService` | `cherry.mastermeister.permission` | YAML入出力（BR-ACCESS-09〜10）。検証フェーズとDB反映フェーズを分離 |

全mutationメソッド（`GroupService`の5メソッド、`PermissionService`の2メソッド、`PermissionYamlService.importFromYaml`）と、UNIT-03の`SchemaIntrospectionService.refreshSchema()`に`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を付与。`EffectivePermissionResolver`の3メソッド（`resolvePrimary`/`canCreate`/`canDelete`）に`@Cacheable(cacheNames = "effectivePermission")`を付与。

## 新規例外・エラーメッセージ

`cherry.mastermeister.common.exception`に`GroupNotFoundException`, `GroupNameDuplicateException`, `GroupMembershipDuplicateException`, `UserNotFoundException`, `PermissionYamlImportRejectedException`を追加。`messages_ja.properties`/`messages_en.properties`にエラーメッセージを追加。

## 実装時の判断・トラブルシューティング

1. **`AccessPermission`コンストラクタの補助権限強制漏れ（テスト作成時に発見）**: カラム階層（`columnName`設定時）では補助権限を常に`false`とする規則（FR-2.5）が、`updatePermission()`メソッドにのみ実装され、コンストラクタ（新規作成時）には未適用だった。`PermissionServiceTest`のテストケース作成時に気づき、コンストラクタにも同じ強制ロジックを追加して修正した。
2. **`listMembers`のUser詳細解決**: `GroupService.listMembers()`は所属ユーザのemail/氏名を返す必要があるため、`registration.repository.UserRepository`を直接注入した。既存の`UserRegistrationService`には単一ID検索の公開APIがなかったため、リポジトリを直接参照する実装判断とした（`EffectivePermissionResolver`がUNIT-03の`SchemaIntrospectionService`（サービス層）を参照する形とは異なるが、いずれもドメイン境界を越えた読み取り専用の参照であり許容範囲と判断）。
3. **Spring 7での`HttpStatus.UNPROCESSABLE_ENTITY`非推奨**: `PermissionYamlImportRejectedException`作成時、コンパイル時の非推奨警告で発見。Spring Framework 7.0で`UNPROCESSABLE_CONTENT`（RFC 9110準拠の名称）へ変更されたことが判明し、そちらを使用するよう修正した。
4. **jqwikプロパティテストの初適用**: UNIT-02でtestImplementation依存のみ追加されていたjqwikを、本ユニットで実際に使用した最初のケース。`EffectivePermissionResolverPropertyTest`（5プロパティ: 階層優先の不変条件、個別設定優先の不変条件、グループ合成の単調性、`canCreate`/`canDelete`の formula同値性）と`PermissionYamlServicePropertyTest`（2プロパティ: ラウンドトリップ特性、重複拒否の原子性）を作成。後者はMockitoで簡易的なインメモリリポジトリ（`ArrayList`を裏側に持つ`save`/`deleteAll`/`findAllByConnectionId`のAnswer実装）を構築し、実際の`exportToYaml`→`importFromYaml`→`exportToYaml`の往復フローを検証した。
5. **YAML再インポート時の複合UNIQUE制約違反（Build and Test前の実機検証で発見、重大）**: devenvの実MySQLに対する手動E2E検証（グループ作成・権限設定・YAMLエクスポート/インポート）で、同一キー構成のままYAMLを再インポートすると`ConstraintViolationException`（複合UNIQUE制約違反）で500エラーになる不具合を発見した。原因はHibernateのデフォルトのフラッシュ順序で、同一フラッシュ内では常にDELETEがINSERTより後に実行されるため、`PermissionYamlService.importFromYaml()`内の「既存行を`deleteAll()`→直後に`save()`で同一キーの行を再作成」という処理順序で、削除前の行が物理的にDBへ残ったまま同一キーのINSERTが実行されていた。`deleteAll()`の直後に明示的な`accessPermissionRepository.flush()`を追加し、削除を即時にDBへ反映させることで解消した。Mockitoベースの`PermissionYamlServiceTest`/`PermissionYamlServicePropertyTest`はリポジトリを簡易なインメモリ実装で代替していたためこの実際のHibernateフラッシュ順序の問題を検出できず、実DB（`@DataJpaTest`）を使う`PermissionYamlServiceIntegrationTest`を新規追加して再現・回帰防止を図った（修正前にこのテストが実際に失敗することを確認済み）。

## テスト結果

Mockitoベースのユニットテスト（`GroupServiceTest` 11件、`PermissionServiceTest` 5件、`EffectivePermissionResolverTest` 10件、`PermissionYamlServiceTest` 6件、計32件）とjqwikプロパティテスト7件（`EffectivePermissionResolverPropertyTest` 5件、`PermissionYamlServicePropertyTest` 2件）、すべて成功。バックエンド全体（既存UNIT-01〜03含む）の回帰テストも197件全件成功を確認した。

**追記（Step 16 最終ビルド検証にて）**: 実機E2E検証で発見したYAML再インポート時の複合UNIQUE制約違反を修正し、再現・回帰防止用の`PermissionYamlServiceIntegrationTest`（`@DataJpaTest`、実H2使用）を追加。バックエンド全体で213件全件成功。

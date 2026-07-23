# UNIT-04 アクセス制御 - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（Caffeineキャッシュ、Spring Cache抽象化＋`@CacheEvict(allEntries=true)`、EffectivePermissionResolver非公開、jackson-dataformat-yaml流用、楽観的ロックなし、YAML importサイズ上限等）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 全問A（推奨通り）、曖昧な回答なし
- [x] Step C: `nfr-design-patterns.md`（レジリエンス・パフォーマンス・セキュリティの設計パターン）を作成する
- [x] Step D: `logical-components.md`（新設する論理コンポーネント、データ設計上の注意点等）を作成する
- [x] Step D追記: レビュー指摘「AccessControlServiceよりもPermissionServiceかな」を受け、`permission`パッケージ内のクラス名を`AccessControlService`→`PermissionService`に訂正（`GroupService`/`group`パッケージとの命名一貫性のため）。COMP-10のグループ管理責務は`group`パッケージ分割時点で既に`GroupService`へ移っており、`PermissionService`は権限設定CRUDのみを担う。INCEPTION Application Design（components.md, component-methods.md, services.md, component-dependency.md, application-design.md）、UNIT-04 Functional Design（business-logic-model.md, frontend-components.md）、UNIT-04 NFR Requirements（nfr-requirements.md, tech-stack-decisions.md）にも取消線+訂正注記で反映
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Logical Components・データ設計上の重要な注意点）
`AccessPermission`の一意制約（`connectionId`, `principalType`, `principalId`, `schemaName`, `tableName`, `columnName`）について、`tableName`/`columnName`が「該当階層なし」を表す場合の値の扱いは？

**注意**: 一般的なRDBMSでは、複合UNIQUE INDEXにNULL値を含む列があると、NULL同士は「等しくない」とみなされ一意制約が機能しません（同じ`(connectionId, principalType, principalId, schemaName)`かつ両方`tableName=NULL`の行が複数登録できてしまう）。

A) `tableName`/`columnName`が「該当階層なし」の場合、SQL NULLではなく空文字列（`''`）等のセンチネル値を格納し、複合UNIQUE INDEXが実際に機能するようにする（テーブル階層・カラム階層のいずれも「該当なし」を明示的な値として表現する）

B) SQL NULLのまま保持し、一意性はDBの制約に頼らずアプリケーション層のみで保証する（upsert時に既存行を事前検索してから更新/挿入する）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 2（Logical Components）
グループ削除時、`AccessPermission`（`principalType=GROUP`かつ`principalId=`当該グループ）のカスケード削除はどこに実装しますか？（`principalId`はDB外部キーを持たない多態的な参照のため、DBレベルの`ON DELETE CASCADE`は使えない）

A) `GroupService.deleteGroup()`内で、`AccessPermissionRepository`経由で該当プリンシパルの行を明示的に削除してから`Group`/`GroupMembership`を削除する（単一トランザクション内、`@Transactional`）

B) 別途バッチ処理・非同期処理で後追い削除する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Performance Patterns）
Caffeineキャッシュ（`effectivePermission`、`@CacheEvict(allEntries=true)`）の無効化呼び出し箇所は？

A) 各mutationメソッド（`PermissionService`の権限設定・解除、`GroupService`のグループ作成・改名・削除・所属追加・所属削除、`PermissionYamlService.importFromYaml`、UNIT-03の`SchemaIntrospectionService.refreshSchema`）それぞれに個別に`@CacheEvict`アノテーションを付与する

B) 共通の無効化用コンポーネントを新設し、各所から明示的に呼び出す（呼び出し漏れを防ぎやすいが、間接的になる）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4（Logical Components・Security Patterns）
YAML importのファイル受け渡し方式・サイズ上限の実装箇所は？

A) フロントエンドで`FileReader`によりYAMLファイルをテキストとして読み込み、既存の`apiFetch`によるJSON通信パターン（`{yaml: string}`等）で送信する。サイズ上限はBean Validationの文字列長制約（`@Size(max=...)`）で実装する

B) `multipart/form-data`によるファイルアップロード（Spring MVCの`@RequestParam MultipartFile`）とし、サイズ上限は`spring.servlet.multipart.max-file-size`で設定する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Security Patterns）
新設する管理者専用エンドポイント（`/api/admin/groups/**`, `/api/admin/permissions/**`）のアクセス制御実装は？

A) UNIT-02で確立済みのSecurityFilterChain設定（`/api/admin/**`パスパターンでのロールチェック）にそのまま含まれるため、追加設定は不要

B) 本ユニット専用の追加設定を行う

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Scalability・Performance Patterns）
`AccessPermission`テーブルのインデックス設計は？

A) `(connection_id, principal_type, principal_id)`（プリンシパル別の一覧取得用）と`(connection_id, schema_name, table_name, column_name)`（実効権限判定時のリソース別検索用）の複合インデックスを追加する

B) 一意制約用のインデックスのみとし、追加のインデックスは設けない（想定規模（数百件程度）であれば不要と判断）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7（Resilience Patterns）
YAML import（BR-ACCESS-10、全置換・重複検出時は全体拒否）の実装方式は？

A) 事前に全件を検証（プリンシパル解決・重複エントリチェック）し、すべて成功した場合のみ既存データの削除・新規データの登録に進む（検証とDB反映を明確に分離する）

B) DB反映処理を進めながらエラーを検出した時点でトランザクションをロールバックする（検証とDB反映を分離しない）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 8（Resilience Patterns・FR-2.9）
スキーマ再取込（UNIT-03の`SchemaIntrospectionService.refreshSchema`）時のキャッシュ無効化について、UNIT-03側のコードへの変更が必要になりますが、実施方針は？

A) UNIT-03の`SchemaIntrospectionService.refreshSchema()`に`@CacheEvict(cacheNames="effectivePermission", allEntries=true)`を追加する（UNIT-04のCode Generation時にUNIT-03のクラスへ変更を加える。UNIT-03完了後の機能追加として、既存の取消線+訂正注記パターンに従いドキュメントを更新する）

B) UNIT-03側は変更せず、UNIT-04側で`SchemaIntrospectionService`をラップする新規コンポーネントを用意し、そこで無効化を行う

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

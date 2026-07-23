# UNIT-04 アクセス制御 - NFR Design Patterns

`unit-04-nfr-design-plan.md`の回答（全問A）に基づく実装パターンを記載する。

---

## 1. Resilience

### 1.1 YAML importの検証順序（Q7=A、BR-ACCESS-10）
- `PermissionYamlService.importFromYaml()`は、検証フェーズとDB反映フェーズを明確に分離する
- **検証フェーズ**: YAML内の全エントリについて、(a) プリンシパル識別子（email／グループ名）の解決可否、(b) 重複エントリの有無（同一プリンシパル×スキーマ×テーブル×カラムの主権限重複、同一プリンシパル×スキーマ×テーブル×補助権限種別重複）を、DBへの書き込みを一切行わずにメモリ上で検証する
- 検証フェーズで1件でも問題が見つかった場合、DB反映フェーズに進まず例外を送出する（BR-ACCESS-10の「インポート全体を拒否」を、ロールバックではなく検証フェーズの時点で実現する）
- **DB反映フェーズ**: 検証をすべて通過した場合のみ、`@Transactional`のスコープ内で対象接続の既存`AccessPermission`を全削除し、YAMLの内容から再構築する

### 1.2 グループ削除時のカスケード削除（Q2=A、BR-ACCESS-11）
- `GroupService.deleteGroup()`は`@Transactional`とし、以下の順で削除する: (1) `AccessPermissionRepository`経由で`principalType=GROUP`かつ`principalId=`当該グループIDの行を削除、(2) `GroupMembership`を削除（`Group`への`@OneToMany(cascade=ALL, orphanRemoval=true)`によりDBレベルで対応可能）、(3) `Group`本体を削除
- `AccessPermission`の`principalId`はDB外部キーを持たない多態的な参照（`principalType`により`User.id`または`Group.id`のいずれかを指す）であるため、DBの`ON DELETE CASCADE`には頼れず、アプリケーション層での明示的な削除が必須である

### 1.3 実効権限判定の安全側デフォルト（既存決定の継続適用、SECURITY-15）
- `EffectivePermissionResolver`は、判定対象の設定が一切存在しない場合・想定外の例外が発生した場合のいずれも、`NONE`（主権限）／`false`（補助権限）を返す（フェイルクローズ、BR-ACCESS-03）
- キャッシュ層（Spring Cache）の障害（Caffeineの内部エラー等）が発生した場合も、キャッシュを経由しない直接計算にフォールバックする実装とする（Spring Cacheのデフォルト挙動: キャッシュ取得に失敗した場合は元のメソッド本体を実行する）

---

## 2. Performance

### 2.1 Caffeineキャッシュの無効化実装（Q3=A、Q8=A、BR-ACCESS-08）
- 以下の各mutationメソッドに`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を個別に付与する（共通コンポーネントは介さない、宣言的アノテーションのみで完結させる）:
  - `PermissionService.setPermission()` / 権限設定の解除メソッド
  - `GroupService`の`createGroup()` / `renameGroup()` / `deleteGroup()` / `addUserToGroup()` / `removeUserFromGroup()`
  - `PermissionYamlService.importFromYaml()`
  - UNIT-03の`SchemaIntrospectionService.refreshSchema()`（Q8=A、UNIT-03完了後の機能追加としてUNIT-03側のクラスに変更を加える。既存の取消線+訂正注記パターンに従い、UNIT-03のnfr-design/code関連ドキュメントに訂正注記を追加する）
- キャッシュ名`effectivePermission`は、`resolvePrimary()` / `canCreate()` / `canDelete()`の3メソッドすべてで共用する（同一キャッシュ名の下、メソッドごとに異なるキー空間を持つ。Spring Cacheのデフォルト挙動により、メソッド名・引数から自動生成されるキーが衝突しないことを確認する）

### 2.2 `AccessPermission`テーブルのインデックス設計（Q6=A）
- 一意制約用インデックス（`connectionId`, `principalType`, `principalId`, `schemaName`, `tableName`, `columnName`）に加え、以下2種の複合インデックスを追加する:
  - `(connection_id, principal_type, principal_id)` — 権限設定画面でのプリンシパル別一覧取得（`GET /api/admin/permissions/{connectionId}?principalType=&principalId=`）用
  - `(connection_id, schema_name, table_name, column_name)` — `EffectivePermissionResolver`によるリソース別検索（キャッシュ未ヒット時のDB問い合わせ）用

---

## 3. Security

### 3.1 `AccessPermission`の一意制約とNULL値の扱い（Q1=A、データ設計上の重要な注意点）
- `tableName`/`columnName`が「該当階層なし」を表す場合、SQL NULLではなく空文字列（`''`）を格納する（`schemaName`は必須のため常に値を持つ）
- これにより、複合UNIQUE INDEX（`connectionId`, `principalType`, `principalId`, `schemaName`, `tableName`, `columnName`）が、NULL同士の非等価性という一般的なRDBMSの挙動に妨げられることなく、実際に機能する
- エンティティのgetter（`getTableName()`等）は、空文字列を`null`に変換して返す（あるいはドメイン上「該当階層なし」を意味する空文字列をそのままDTOに透過させず、API層で`null`相当として扱う）ことで、ビジネスロジック層・フロントエンドからは引き続き「未設定はnull」という一貫したモデルを維持する（永続化層のみがセンチネル値を意識する）

### 3.2 YAML importのファイル受け渡し・サイズ制限（Q4=A、SECURITY-05）
- フロントエンドは`FileReader`でYAMLファイルをテキストとして読み込み、既存の`apiFetch`によるJSON通信パターン（`POST /api/admin/permissions/{connectionId}/import`、ボディ`{yaml: string}`）で送信する
- サイズ上限はリクエストDTO（`PermissionImportRequest`）の`yaml`フィールドにBean Validationの`@Size(max = ...)`（文字数上限。具体的な上限値はtech-stack-decisions.md参照）を付与して実装する
- 上限超過時はBean Validationのエラーとして扱い、UNIT-02で確立済みのBR-API-01形式のエラーレスポンスに変換する（既存のグローバル例外ハンドラをそのまま利用）

### 3.3 管理者専用エンドポイントのアクセス制御（Q5=A、SECURITY-08）
- `/api/admin/groups/**`, `/api/admin/permissions/**`は、UNIT-02で確立済みのSecurityFilterChain設定（`/api/admin/**`パスパターンに対するロールベース認可）にそのまま合致するため、追加のセキュリティ設定は不要
- 未認証・非管理者ロールでのアクセスは既存の仕組みにより401/403として扱われる

### 3.4 監査ログとの連携（既存決定の継続適用、SECURITY-13）
- 権限変更・グループ関連の各操作は、domain-entities.md §4で定義した8種のイベント種別（`PERMISSION_CHANGED`等）でAuditEventPublisher経由の記録を行う。ロジックはCode Generation段階でUNIT-02/03と同じ`AuditEventPublisher`インターフェースを再利用する

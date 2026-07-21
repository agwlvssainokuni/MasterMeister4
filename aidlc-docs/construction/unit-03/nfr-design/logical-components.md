# UNIT-03 RDBMSセットアップ - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.rdbmsconnection`（unit-of-work.mdの既存方針）。

---

## 1. RDBMS接続ドメイン（`cherry.mastermeister.rdbmsconnection`）

### RdbmsConnectionService（COMP-07）
- business-logic-model.md §1・§2・§4の登録・更新・接続テスト・削除フローを実装
- `RdbmsConnectionRepository`（Spring Data JPA）経由で`RdbmsConnection`を永続化
- `getDataSource(connectionId): DataSource`（COMP-07既存メソッド）の内部実装として、`ConcurrentHashMap<ConnectionId, HikariDataSource>`によるキャッシュを保持する（Q4=A）。接続情報の更新・削除時にキャッシュのエントリを`close()`した上で除去する
- 未保存の接続情報に対する接続テスト（BR-RDBMS-11）は、リクエストボディの値から一時的な`HikariConfig`/`HikariDataSource`を構築し、テスト後は破棄する（キャッシュには載せない）

### ConnectionCredentialCipher（新設、`cherry.mastermeister.rdbmsconnection`）
- 接続パスワードのAES-256-GCM暗号化・復号を担当（Q1=A、本ユニット専用）
- `encrypt(plainPassword: String): EncryptedCredential`（`EncryptedCredential`は`keyId`・IV・暗号文・認証タグを保持する内部データ構造）
- `decrypt(encryptedPassword: String, keyId: int): String`
- 起動時、`AppProperties.Rdbms.encryptionKeys`（§3参照）から全世代の鍵をロードし、`Map<Integer, SecretKey>`として保持する。最大の`keyId`を現在鍵として`encrypt()`に使用する

### SchemaIntrospectionService（COMP-08）
- business-logic-model.md §3のスキーマ取込フローを実装
- `refreshSchema(connectionId): SchemaSnapshot`を`CompletableFuture.supplyAsync(...).orTimeout(60, TimeUnit.SECONDS)`でラップする（Q2=A）
- JDBC `DatabaseMetaData`によるテーブル・カラム・制約の読取、`RdbmsDialectStrategy`によるスキーマ切替の適用

### RdbmsDialectStrategy（COMP-09、インターフェース＋実装群）
- `MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgresDialectStrategy`, `H2DialectStrategy`
- `requiresSchemaSwitch()`: PostgreSQL/H2は`true`（schemaName指定時）、MySQL/MariaDBは`false`
- `applySchemaSwitch(connection, schema)`: PostgreSQL/H2は`SET search_path`相当の操作を実行

### RdbmsConnectionController（`cherry.mastermeister.rdbmsconnection`）
- `POST /api/admin/rdbms-connections`, `PUT /api/admin/rdbms-connections/{id}`, `DELETE /api/admin/rdbms-connections/{id}`, `GET /api/admin/rdbms-connections`
- `POST /api/admin/rdbms-connections/{id}/test`（保存済み接続のテスト）
- `POST /api/admin/rdbms-connections/test`（未保存値のテスト、BR-RDBMS-11）
- `POST /api/admin/rdbms-connections/{id}/schema-refresh`
- `GET /api/admin/rdbms-connections/{id}/schema`
- 全エンドポイントとも、既存のSecurityFilterChain設定（`/api/admin/**`）により管理者ロール必須（Q5=A、追加設定不要）

### DTO設計（Q6=A、BR-RDBMS-12）
- `RdbmsConnectionRequest`（登録・更新共通。`password`フィールドを持つ。Bean Validationアノテーション付与、Q4/Q4継続適用）
- `RdbmsConnectionResponse` / `RdbmsConnectionSummaryResponse`（一覧・詳細取得用。パスワードフィールドを一切含まない設計。エンティティを直接シリアライズせず、Service/Controller層でDTOへ変換する際に除外する）
- `ConnectionTestRequest`（未保存値のテスト用。`RdbmsConnectionRequest`とほぼ同じ形だが、保存を伴わないことを示す別クラスとする）
- `ConnectionTestResult`（`success: boolean`, `errorCategory: enum（CONNECTION_UNREACHABLE/AUTH_ERROR/TIMEOUT/OTHER）`, `message: String`。BR-RDBMS-04のエラー分類に対応）

---

## 2. 監査ログ連携

- `AuditEventPublisher`（UNIT-02で新設済み、`cherry.mastermeister.audit`）を通じて、`CONNECTION_REGISTERED`/`CONNECTION_UPDATED`/`CONNECTION_DELETED`/`SCHEMA_IMPORTED`の4イベントを発行する（domain-entities.md §3）
- 接続テスト（保存済み・未保存いずれも）は監査ログ対象外（BR-RDBMS-05）。失敗時のみSLF4Jでアプリケーションログに記録する

---

## 3. 設定（AppProperties拡張）

`cherry.mastermeister.common.config.AppProperties`に、以下のネストしたrecordを追加する。

```java
public record Rdbms(List<EncryptionKey> encryptionKeys) {
    public Rdbms {
        if (encryptionKeys == null || encryptionKeys.isEmpty()) {
            throw new IllegalArgumentException("mm.app.rdbms.encryption-keys must have at least one key");
        }
    }

    public record EncryptionKey(int keyId, String base64Key) {
        public EncryptionKey {
            // keyId > 0、base64KeyがAES-256相当（デコード後32バイト）であることを検証（Q3=A、fail-fast）
        }
    }
}
```

- 環境変数: `MM_APP_RDBMS_ENCRYPTION_KEYS`（形式: `keyId:base64key`のカンマ区切りリスト。Spring Bootの`@ConfigurationProperties`でのリストへのマッピング方式はCode Generation段階で確定する）
- 起動時、`AppProperties`のコンストラクタ検証で鍵が0件または不正フォーマットの場合はアプリケーション起動を失敗させる（Q3=A）

---

## 4. HikariCP設定パラメータ（接続ごとに動的生成）

| パラメータ | 値 |
|---|---|
| `maximumPoolSize` | 5 |
| `minimumIdle` | 0 |
| `connectionTimeout` | 5000（ミリ秒、NFR-03-02の接続タイムアウト5秒に対応） |

上記は接続ごとに`RdbmsConnectionService`が動的に生成する`HikariConfig`に設定する（Spring Bootのグローバル`DataSource`設定（内部H2用）とは独立）。

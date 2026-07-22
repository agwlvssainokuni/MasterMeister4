# UNIT-03 RDBMSセットアップ - Business Logic Summary

`unit-03-code-generation-plan.md` Section 5〜7の実行結果サマリ。

## 作成したコンポーネント（`backend/src/main/java/cherry/mastermeister/rdbmsconnection/`）

| コンポーネント | パッケージ | 対応 |
|---|---|---|
| `ConnectionCredentialCipher` | `rdbmsconnection` | AES-256-GCM暗号化・復号、鍵ローテーション（`AppProperties.Rdbms`、SECURITY-01・12） |
| `RdbmsDialectStrategy`（インターフェース） | `rdbmsconnection.dialect` | `requiresSchemaSwitch()`, `applySchemaSwitch()`, `buildJdbcUrl()` |
| `MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgresDialectStrategy`, `H2DialectStrategy` | `rdbmsconnection.dialect` | COMP-09。方言ごとのJDBC URL構築（`&`区切り or H2のみ`;`区切り）、スキーマ切替 |
| `RdbmsDialectStrategyResolver` | `rdbmsconnection.dialect` | component-methods.mdの`resolveDialect()`ファクトリメソッドを、Spring DI経由で実装 |
| `RdbmsConnectionService`（COMP-07） | `rdbmsconnection` | business-logic-model.md §1・§2・§4。登録・更新・削除・接続テスト（保存済み/未保存）・`getDataSource()`（HikariCP DataSourceキャッシュ） |
| `SchemaIntrospectionService`（COMP-08） | `rdbmsconnection` | business-logic-model.md §3。JDBC `DatabaseMetaData`によるスキーマ取込、タイムアウト制御、全置換 |
| `ConnectionErrorCategory`, `ConnectionTestOutcome` | `rdbmsconnection` | BR-RDBMS-04のエラー分類結果 |

## APIエラー例外（`common.exception`、BR-API-01）

`RdbmsConnectionNotFoundException`（404）、`SchemaImportFailedException`（502）、`SchemaNotImportedException`（404）。`messages_ja/en.properties`に対応するメッセージキーを追加した。

## 実装時に発見・修正した設計ギャップ

1. **HikariCPのプール即時疎通確認**: `HikariDataSource`はデフォルトでプール生成時に接続を試行し、失敗すると`PoolInitializationException`を送出する。対象RDBMSが一時的に利用不可でも接続情報自体は登録・キャッシュできるべきという方針（実際の疎通確認はBR-RDBMS-04の接続テストか、実利用時に委ねる）に合わせ、`HikariConfig.setInitializationFailTimeout(-1)`を設定してプール生成時の即時疎通確認を無効化した
2. **スキーマ未指定時のシステムスキーマ混入**: `DatabaseMetaData.getTables()`にschemaPattern=nullを渡すと、H2/PostgreSQLでは`INFORMATION_SCHEMA`等のシステムスキーマのテーブルまで取得対象に含まれてしまうことが判明（実装・テスト時に発見）。`schemaName`が未指定の場合、方言ごとのデフォルトスキーマ（H2=`PUBLIC`、PostgreSQL=`public`）に解決するよう修正した
3. **JDBC接続失敗のエラー分類（BR-RDBMS-04）**: SQLState（`08`系=接続不可、`28`系=認証エラー）とメッセージのキーワードマッチを組み合わせたベストエフォート実装とした（JDBCドライバ間でSQLStateの粒度が完全には統一されていないため）
4. **主キー自動生成インデックスのUNIQUE制約重複登録（Code Generation Complete提示後のレビューで発見）**: MySQL/MariaDBは主キー制約名が常に固定文字列`"PRIMARY"`になる。`DatabaseMetaData.getIndexInfo()`は主キー列に対して自動生成されたインデックスも返すため、`readPrimaryKey()`のPRIMARY_KEY制約と`readIndexes()`のUNIQUE制約とで同一列が二重に登録されていた（実データそのものは誤りではないが冗長）。この重複が、フロントエンドの制約名ベースのReact keyと組み合わさることで、テーブル切替時に別テーブルの同名列へ古い制約バッジが残留する表示不具合（後述）の一因にもなっていたため、`readPrimaryKey()`の戻り値を主キー列集合に変更し、`readIndexes()`側でインデックスの列集合が主キー列集合と完全一致する場合は登録をスキップするよう修正した
5. **1接続内の複数スキーマ対応（UNIT-04 Functional Designにて訂正）**: `RdbmsConnection.schemaName`（単一固定値）は、1接続内に複数スキーマが存在しうるという元々の要件（`initial-request.md` §5.7、FR-7.5「対象接続内でユーザがアクセス権限を持つスキーマの一覧」）に対して狭すぎるスコープだったと判明。`schemaName`フィールドを廃止し、`SchemaTable`に`schemaName`属性を追加。`SchemaIntrospectionService`はPostgreSQL/H2について`DatabaseMetaData.getSchemas()`でシステムスキーマを除く全スキーマを自動検出し、スキーマごとにテーブル・カラム・制約を読み取るループ構造に書き換えた（`isSystemSchema()`を`RdbmsDialectStrategy`に追加）。この過程で、スキーマ取込時の`applySchemaSwitch()`呼び出し（セッションのスキーマ切替）はJDBCのメタデータ取得がschemaPattern引数で直接絞り込めるため不要と判明し削除。`applySchemaSwitch()`はUNIT-06（クエリ実行時の対象スキーマ指定）専用のメソッドとなった。実際にdevenvのPostgreSQLへ`sales`スキーマを追加作成した上で動作確認し、`public`/`sales`両スキーマのテーブルが正しくスキーマ名付きで取り込まれることを確認した

## テスト結果

Mockitoベースのユニットテスト（`RdbmsConnectionServiceTest`）に加え、`SchemaIntrospectionServiceTest`は対象RDBMS役として実際にH2をTCPサーバモードで起動し、本物のJDBC接続・`DatabaseMetaData`読取を通じて検証した（モックだけでは`ResultSet`の複雑な相互作用を検証しきれないため）。

| クラス | テスト数 | 主な検証内容 |
|---|---|---|
| `ConnectionCredentialCipherTest` | 4 | 暗号化・復号往復、鍵ローテーション後も旧鍵での復号が可能、未知の`keyId`でのエラー |
| `RdbmsDialectStrategyTest` | 6 | 4方言の`buildJdbcUrl()`出力形式（`&`区切り/`;`区切り）、`requiresSchemaSwitch()`、`RdbmsDialectStrategyResolver`の解決・未登録時のエラー |
| `RdbmsConnectionServiceTest` | 12 | 登録・更新（パスワード変更あり/なし）・削除、DataSourceキャッシュの生成・エビクション、BR-RDBMS-04エラー分類（`classify()`） |
| `SchemaIntrospectionServiceTest` | 4 | 実H2 TCPサーバに対するテーブル/カラム/PK/FK取込、1接続内の複数スキーマ自動検出とシステムスキーマ除外（UNIT-04 Functional Designにて追加）、全置換（再取込での置き換え）、対象RDBMS到達不能時の`SchemaImportFailedException`送出と旧スナップショット保持 |

いずれも`./gradlew :backend:test`で全件成功（既存UNIT-01/02テスト含め全137件超）。

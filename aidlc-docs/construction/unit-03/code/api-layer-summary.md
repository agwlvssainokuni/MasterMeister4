# UNIT-03 RDBMSセットアップ - API Layer Summary

`unit-03-code-generation-plan.md` Section 8〜10の実行結果サマリ。

## エンドポイント一覧（`/api/admin/rdbms-connections`、管理者ロール必須）

| メソッド・パス | 説明 |
|---|---|
| `GET /api/admin/rdbms-connections` | 一覧取得（`schemaImportedAt`を含む） |
| `POST /api/admin/rdbms-connections` | 新規登録（BR-RDBMS-01, BR-RDBMS-02） |
| `PUT /api/admin/rdbms-connections/{id}` | 更新（BR-RDBMS-03） |
| `DELETE /api/admin/rdbms-connections/{id}` | 削除（BR-RDBMS-09、無条件カスケード） |
| `POST /api/admin/rdbms-connections/{id}/test` | 接続テスト（保存済み接続、BR-RDBMS-04） |
| `POST /api/admin/rdbms-connections/test` | 接続テスト（未保存の入力値、BR-RDBMS-11） |
| `POST /api/admin/rdbms-connections/{id}/schema-refresh` | スキーマ取込（BR-RDBMS-06〜08） |
| `GET /api/admin/rdbms-connections/{id}/schema` | スキーマ詳細取得（未取込の場合404 `SCHEMA_NOT_IMPORTED`） |

いずれも既存の`SecurityConfig`（`/api/admin/**`パターン、`hasRole('ADMIN')`）にそのまま含まれるため、追加のセキュリティ設定は不要だった。

## DTO（`cherry.mastermeister.rdbmsconnection.dto`）

`RdbmsConnectionRequest`（登録・更新共通、Bean Validation）, `ConnectionTestRequest`（未保存テスト用、passwordは必須）, `RdbmsConnectionResponse`（一覧・詳細共用、パスワードフィールドを含めない設計、BR-RDBMS-12）, `ConnectionTestResult`, `SchemaSnapshotResponse`/`SchemaTableResponse`/`SchemaColumnResponse`/`SchemaConstraintResponse`。

## APIエラー例外

`RdbmsConnectionNotFoundException`（404）、`SchemaImportFailedException`（502）、`SchemaNotImportedException`（404）。既存の`GlobalExceptionHandler`（UNIT-02）でBR-API-01形式に変換される。

## テスト結果

`@WebMvcTest(RdbmsConnectionController.class)` + 実`SecurityConfig`を`@Import`し、JWT付きMockMvcで検証。

| テスト | 検証内容 |
|---|---|
| `list_returnsConnections_withoutPasswordField` | レスポンスJSONに`password`/`encryptedPassword`フィールドが存在しないこと |
| `register_delegatesToService` | サービス呼び出しへの委譲、JWT subjectからの操作者ID抽出 |
| `register_rejectsInvalidPort_withValidationError` | Bean Validationによる400エラー |
| `delete_delegatesToService` | サービス呼び出しへの委譲 |
| `update_returnsNotFound_whenConnectionMissing` | `RdbmsConnectionNotFoundException`→404、BR-API-01形式 |
| `testSaved_returnsClassifiedFailure` | BR-RDBMS-04エラー分類のレスポンス反映 |
| `testUnsaved_delegatesToServiceWithoutPersisting` | 未保存テストエンドポイントの委譲 |
| `getSchema_returnsNotFound_whenNotYetImported` | 未取込時の404 |
| `adminEndpoint_returnsUnauthorized_whenNotAuthenticated` | 未認証401 |
| `adminEndpoint_returnsForbidden_whenAuthenticatedWithoutAdminRole` | 非ADMINロール403 |

全10テスト成功。`./gradlew :backend:test`で既存テスト（UNIT-01/02含む）とあわせて全件成功を確認。

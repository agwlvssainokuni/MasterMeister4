# UNIT-07 クエリビルダー - API Layer Summary

## エンドポイント一覧

パッケージ`cherry.mastermeister.querybuilder.api`。単一の`QueryBuilderController`に3エンドポイントをまとめる（Q5=A）。

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/query-builder/{connectionId}/tables?schemaName=...` | アクセス可能テーブル/カラム一覧（BR-QUERYBUILDER-01） |
| POST | `/api/query-builder/{connectionId}/generate` | SQL生成（FR-5.5） |
| POST | `/api/query-builder/{connectionId}/parse` | リバースエンジニアリング（FR-5.7） |

接続一覧・スキーマ一覧はUNIT-06の既存エンドポイント（`GET /api/queries/connections`・`GET /api/queries/{connectionId}/schemas`）を再利用するため、本Controllerには含めない。

## 確認済みの既存インフラ再利用（追加実装なし）

- **`GlobalExceptionHandler`**: 新規例外4種はいずれも`ApiException`のサブクラスであり、既存の汎用`@ExceptionHandler(ApiException.class)`で自動処理される。追加実装不要（UNIT-05/06と同じ結論）
- **SecurityFilterChain**: `SecurityConfig`の既存ルール`.requestMatchers("/api/**").authenticated()`が`/api/query-builder/**`をそのままカバーする。新規ルール追加不要（UNIT-05/06と同じ結論、一般ユーザ向け機能でロール不問）
- **OpenAPI/Swagger UI**: springdoc-openapiの自動生成のみ、追加実装不要

## テスト結果

`QueryBuilderControllerTest`（`@WebMvcTest`、実SecurityFilterChain有効化）: 8件
- 一般ユーザ（非ADMIN）でもアクセス可能なことの確認（既存の`/api/**`→`authenticated()`ルールの実証）
- 未認証時の401
- SQL生成の正常系・Bean Validation違反時の400・GROUP BY整合性違反時の400
- リバースエンジニアリングの正常系・構文非対応時の422・アクセス権限不足時の403

全件成功（`./gradlew :backend:test --tests "cherry.mastermeister.querybuilder.api.*"`）

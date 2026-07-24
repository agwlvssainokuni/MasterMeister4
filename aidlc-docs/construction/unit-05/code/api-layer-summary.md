# UNIT-05 マスタメンテナンス - API Layer Summary

Code Generation計画Step 8〜10の実施結果。

## エンドポイント一覧

全エンドポイントとも`/api/master-data/**`配下。既存のSecurityFilterChain設定（`/api/admin/**`→ADMIN限定の次に`/api/**`→`authenticated()`という汎用ルール）がそのまま適用され、認証済みであればロール不問でアクセス可能（詳細は下記「実装時の設計訂正」参照）。

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/master-data/connections` | アクセス可能な接続一覧（BR-MASTER-13） |
| GET | `/api/master-data/connections/{connectionId}/tables` | アクセス可能なテーブル/ビュー一覧（BR-MASTER-01〜02） |
| GET | `/api/master-data/connections/{connectionId}/tables/{schemaName}/{tableName}/records` | レコード一覧（ページング・構造化フィルタ・SQL手入力。BR-MASTER-04〜05, 10, 14〜15） |
| POST | `/api/master-data/connections/{connectionId}/tables/{schemaName}/{tableName}/records/batch` | 一括反映（作成/更新/削除混在。BR-MASTER-06〜09） |

### レコード一覧のクエリパラメータ
- `page`（既定0）, `pageSize`（既定50）
- `filter`（省略可）: 構造化フィルタ条件をJSON配列としてエンコードした文字列。例: `[{"columnName":"status","operator":"EQ","value":"active"}]`
- `where`, `orderBy`（省略可）: SQL手入力のWHERE/ORDER BY句（BR-MASTER-04）

新規DTO: `AccessibleConnectionResponse`, `AccessibleTableResponse`, `RecordColumnResponse`, `RecordPageResponse`, `RecordFilterRequest`, `BatchOperationRequest`/`BatchOperationItemRequest`, `BatchOperationResultResponse`/`BatchOperationItemResultResponse`（`cherry.mastermeister.masterdata.dto`）。

## 実装時の設計訂正（Part 1計画からの変更点）

### 1. GlobalExceptionHandlerへの個別ハンドラ追加は不要
`InvalidQueryConditionException`/`BatchSizeExceededException`はいずれも既存の`ApiException`のサブクラスとして実装したため、`GlobalExceptionHandler`の汎用`@ExceptionHandler(ApiException.class)`がそのまま処理する。既存の他ユニットの例外（`SchemaNotImportedException`等）と同じパターンであり、新規`@ExceptionHandler`メソッドの追加は不要だった。

### 2. SecurityFilterChainへの新規ルール追加は不要
既存の`SecurityConfig`（UNIT-02）には、`/api/admin/**`（ADMIN限定）ルールの次に`/api/**`→`authenticated()`という汎用ルールが既に設定されていた。`/api/master-data/**`は`/api/admin/**`に一致しないため、この既存の汎用ルールがそのまま適用され（認証済みであればロール不問でアクセス可能）、当初計画していた新規ルール追加は不要と判明した。`MasterDataControllerTest`で、`ROLE_USER`（非ADMIN）のJWTでも`200 OK`が返ることを実証して確認した。

### 3. filter クエリパラメータの契約確定
frontend-components.md §3では「確定的な契約はCode Generation段階で定める」とされていたため、構造化フィルタはJSON配列文字列としてエンコードし、`ObjectMapper`でデコードする方式とした（複数条件を1つのGETクエリパラメータで表現するための実装判断）。

## テスト結果

`MasterDataControllerTest`: 10件成功（`@WebMvcTest`、実SecurityFilterChain有効化）
- 一般ユーザ（`ROLE_USER`）でも各エンドポイントに`200 OK`でアクセス可能なこと（ADMIN限定でないことの実証）
- 未認証時は`401 Unauthorized`
- 構造化フィルタ（`filter`パラメータ）のJSONデコードが正しく`MasterDataService`へ渡されること
- `filter`パラメータが不正なJSONの場合`400`+`INVALID_QUERY_CONDITION`
- SQL手入力WHERE句が拒否された場合`400`+`INVALID_QUERY_CONDITION`
- 一括反映: 成功時のレスポンス、`operations`空の場合の`400`（Bean Validation）、バッチ上限超過時の`400`+`BATCH_SIZE_EXCEEDED`

注記: `@WebMvcTest`スライスには`JacksonAutoConfiguration`由来の`ObjectMapper` Beanが含まれないため（`MasterDataController`が`ObjectMapper`を直接コンストラクタインジェクションする初のコントローラ）、テスト側で`@TestConfiguration`により明示的に提供した。実アプリケーション（`spring-boot-starter-web`によるフル自動設定）では通常どおり`ObjectMapper`が利用可能であり、本番動作には影響しない。

`./gradlew :backend:compileJava :backend:compileTestJava`および対象テストは成功。OpenAPI/Swagger UIへの反映は既存コントローラ同様、追加のアノテーション実装なしにspringdocが自動生成する（Step 16の起動確認で最終確認）。

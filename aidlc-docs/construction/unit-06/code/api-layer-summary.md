# UNIT-06 クエリ保存・実行 - API Layer Summary

全エンドポイントとも`/api/queries/**`配下。既存のSecurityFilterChain設定（`/api/admin/**`→ADMIN限定の次に`/api/**`→`authenticated()`という汎用ルール）がそのまま適用され、認証済みであればロール不問でアクセス可能（UNIT-05のMasterDataControllerと同じ結論、新規ルール追加は不要）。

## エンドポイント一覧

### QueryController（COMP-14対応）

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/queries/connections` | アクセス可能な接続一覧 |
| GET | `/api/queries/{connectionId}/schemas` | アクセス可能なスキーマ一覧（BR-QUERY-02） |
| POST | `/api/queries/{connectionId}/execute` | ad-hoc実行（BR-QUERY-01, BR-QUERY-04） |

### SavedQueryController（COMP-15対応）

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/queries/{connectionId}/saved` | 保存クエリ一覧（BR-QUERY-05, BR-QUERY-08、`visibility`/`includeOwnRetired`クエリパラメータ） |
| POST | `/api/queries/{connectionId}/saved` | 新規保存（BR-QUERY-05） |
| GET | `/api/queries/{connectionId}/saved/{savedQueryId}` | 取得（BR-QUERY-09、アクセス不可時404） |
| PUT | `/api/queries/{connectionId}/saved/{savedQueryId}` | 更新（作成者のみ、BR-QUERY-07） |
| POST | `/api/queries/{connectionId}/saved/{savedQueryId}/execute` | 実行（BR-QUERY-09） |
| POST | `/api/queries/{connectionId}/saved/{savedQueryId}/retire` | 非表示化（作成者のみ、BR-QUERY-08） |

## 実装時の確認事項

- `GlobalExceptionHandler`への個別ハンドラ追加は不要（Step 5.2の5例外はいずれも`ApiException`のサブクラスで、既存の汎用`@ExceptionHandler(ApiException.class)`が処理する）
- SecurityFilterChainへの新規ルール追加も不要（既存の`/api/**`→`authenticated()`汎用ルールが適用される）

## 実装時の発見

Jackson 3系（`tools.jackson`パッケージ）は、プリミティブ型（`boolean`/`int`）のレコード構成要素に対応するJSONフィールドがリクエストボディに存在しない場合、`null`を割り当てようとして`MismatchedInputException`（`HttpMessageNotReadableException`経由、既存の汎用500ハンドラに到達）を送出する。実際のフロントエンドは`QueryExecutionRequest`/`SavedQueryExecutionRequest`の全フィールドを常に送信するため実害はないが、テストのJSONペイロードは全フィールドを含める必要があると判明し、該当テストを修正した。

## テスト結果

- `QueryControllerTest`（7件）: 一般ユーザ（非ADMIN）でもアクセス可能なことの確認、ad-hoc実行のバリデーション・エラー応答（`NON_READ_ONLY_QUERY`/`QUERY_SCHEMA_NOT_ACCESSIBLE`）
- `SavedQueryControllerTest`（9件）: CRUD・実行・非表示化、作成者以外による編集/非表示化の404相当拒否（`SAVED_QUERY_NOT_ACCESSIBLE`、フェイルクローズ方針）
- 全件成功

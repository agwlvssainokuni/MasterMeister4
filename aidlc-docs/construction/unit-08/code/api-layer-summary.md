# UNIT-08 クエリ履歴 - API Layer Summary

## エンドポイント一覧

`QueryHistoryController`（`/api/query-history`）:

- `GET /api/query-history/connections?executedByScope=ALL|MINE` — 履歴実績ベースの接続一覧（BR-QUERYHISTORY-11）
- `GET /api/query-history/{connectionId}/schemas?executedByScope=ALL|MINE` — 対象接続の履歴実績ベースのスキーマ名一覧（BR-QUERYHISTORY-10）
- `GET /api/query-history/{connectionId}?executedByScope=ALL|MINE&executedAtFrom=...&executedAtTo=...&schemaName=...&sqlKeyword=...&page=0&pageSize=50` — 履歴一覧取得（絞込・ページング）

3エンドポイントとも、`@AuthenticationPrincipal Jwt principal`からJWTの`role`クレームを判定し、一般ユーザが`executedByScope=ALL`を指定した場合はController内の`resolveExecutedByFilter`で強制的に自分の`userId`へ絞り込む（BR-QUERYHISTORY-03のフェイルクローズ）。Service層にはロールではなく絞込済みの`executedByFilter`のみを渡す。

## 実装時の判断・発見

- **`QueryHistorySearchRequest`を作らない判断**（Business Logic Generationで既述、Step 1.1参照）: 個々の`@RequestParam`で受け取り、日時範囲の相関チェック・ページサイズ上限チェックはController内で明示的に実施する
- **新規例外`QueryHistoryInvalidParameterException`（400）を追加**: 上記の明示的チェック違反時に送出する。既存の`ApiException`パターンに沿い、`GlobalExceptionHandler`の汎用`@ExceptionHandler(ApiException.class)`でそのまま処理される（追加のハンドラ実装は不要）
- ページサイズ上限は200件（`MAX_PAGE_SIZE`、デフォルト50件は既存の`MasterDataController.DEFAULT_PAGE_SIZE`と同値）
- `Page<QueryHistoryRecordResponse>`はSpring Data JPAの`PageImpl`をそのまま返さず、独自の`QueryHistoryPageResponse`（`content`, `page`, `pageSize`, `totalElements`, `totalPages`）に変換して返す（`masterdata.dto.RecordPageResponse`と同じ設計方針）
- `GlobalExceptionHandler`への追加は不要と確認（新規例外は`ApiException`サブクラスのため既存の汎用ハンドラで処理される、UNIT-05〜07と同じ結論）
- SecurityFilterChainへの新規ルール追加は不要と確認（既存の`/api/**` → `authenticated()`ルールでカバーされる、UNIT-05〜07と同じ結論）
- `messages_ja.properties`・`messages_en.properties`に`error.QUERY_HISTORY_INVALID_PARAMETER`を追加

## テスト結果

`QueryHistoryControllerTest`（`@WebMvcTest`、実フィルタチェーン有効）: 8件

- 接続一覧の一般ユーザアクセス可否・未認証時401
- 実行者スコープのフェイルクローズ実証（一般ユーザは`ALL`指定でも自分のuserIdで呼び出されること、管理者は`null`（全ユーザ対象）で呼び出されることをMockitoの`verify`で確認）
- スキーマ名一覧・履歴一覧の正常系
- ページサイズ超過・日時範囲不正時の400応答（`QUERY_HISTORY_INVALID_PARAMETER`）

全件成功

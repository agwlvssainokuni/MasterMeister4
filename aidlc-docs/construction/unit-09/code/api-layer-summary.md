# UNIT-09 監査ログ閲覧 - API Layer Summary

## 作成したクラス

- **`audit/api/AuditLogController.java`**（新規）: 監査ログ一覧取得の単一エンドポイント（`GET /api/admin/audit-log`）。既存の`SecurityConfig`の`/api/admin/**`ルールでエンドポイント全体が保護されるため、ロール判定ロジックは持たない
- **`common/exception/AuditLogInvalidParameterException.java`**（新規）: ページサイズ上限超過・発生日時範囲の相関違反時に送出する400エラー（UNIT-08の`QueryHistoryInvalidParameterException`と同じパターン）
- **`messages_ja.properties`/`messages_en.properties`**（既存修正）: `error.AUDIT_LOG_INVALID_PARAMETER`を追加

## 実装確認事項

- `GlobalExceptionHandler`への追加: 不要（`ApiException`の汎用ハンドラで処理される）
- `SecurityFilterChain`設定への追加: 不要（既存の`/api/admin/**`→`hasRole("ADMIN")`ルールでカバーされる）
- OpenAPI/Swagger UIへの反映: 既存の自動生成のみで追加実装不要

## テスト結果

`AuditLogControllerTest`（`@WebMvcTest`、`SecurityConfig`を実際にインポートしたうえでのテスト）— 5件全件成功

- 正常系（監査ログ一覧取得、対象ユーザ名解決の反映確認）
- ページサイズ上限超過時の400応答
- 発生日時範囲の相関違反（開始>終了）時の400応答
- 未認証リクエストの401応答
- 一般ユーザ（`ROLE_USER`）ロールでのリクエストの403応答（管理者専用エンドポイントであることの実証）

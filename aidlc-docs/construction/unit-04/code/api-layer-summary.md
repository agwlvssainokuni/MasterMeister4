# UNIT-04 アクセス制御 - API Layer Summary

`unit-04-code-generation-plan.md` Section 8〜10の実行結果サマリ。

## エンドポイント一覧

| メソッド | パス | コントローラ | 権限 |
|---|---|---|---|
| GET | `/api/admin/groups` | `GroupController` | ADMIN |
| POST | `/api/admin/groups` | `GroupController` | ADMIN |
| PUT | `/api/admin/groups/{id}` | `GroupController` | ADMIN |
| DELETE | `/api/admin/groups/{id}` | `GroupController` | ADMIN |
| GET | `/api/admin/groups/{id}/members` | `GroupController` | ADMIN |
| POST | `/api/admin/groups/{id}/members` | `GroupController` | ADMIN |
| DELETE | `/api/admin/groups/{id}/members/{userId}` | `GroupController` | ADMIN |
| GET | `/api/admin/permissions/{connectionId}?principalType=&principalId=` | `PermissionController` | ADMIN |
| PUT | `/api/admin/permissions/{connectionId}` | `PermissionController` | ADMIN |
| DELETE | `/api/admin/permissions/{connectionId}?principalType=&principalId=&schemaName=&tableName=&columnName=` | `PermissionController` | ADMIN |
| GET | `/api/admin/permissions/{connectionId}/export` | `PermissionController` | ADMIN |
| POST | `/api/admin/permissions/{connectionId}/import` | `PermissionController` | ADMIN |

全エンドポイントとも既存の`SecurityFilterChain`（`/api/admin/**`）にそのまま合致するため追加のセキュリティ設定は不要。

## DTO

| パッケージ | DTO | 内容 |
|---|---|---|
| `cherry.mastermeister.group.dto` | `GroupRequest` | `name`（`@NotBlank`）。作成・改名共通 |
| 同上 | `GroupResponse` | `id`, `name`, `memberCount`, `createdAt` |
| 同上 | `GroupMemberRequest` | `userId`（`@NotNull`） |
| 同上 | `GroupMemberResponse` | `userId`, `email`, `fullName` |
| `cherry.mastermeister.permission.dto` | `PermissionEntryRequest` | 対象キー＋設定値。`tableName`/`columnName`は任意 |
| 同上 | `PermissionEntryResponse` | `PermissionEntryRequest`と同じ属性＋`updatedAt`/`updatedBy` |
| 同上 | `PermissionImportRequest` | `yaml`（`@NotBlank @Size(max=1_048_576)`、tech-stack-decisions.md §9） |

`PermissionImportResult`はnfr-design/logical-components.md §2で想定していたが実装せず。成功時は204 No Content、失敗時は`PermissionYamlImportRejectedException`経由のBR-API-01形式エラーレスポンスで十分に表現できるため、専用DTOは追加しない実装判断とした（YAGNI）。

## 実装時の判断・トラブルシューティング

- **必須クエリパラメータ欠落時の500エラー（テスト作成時に発見）**: `PermissionController`のGET一覧取得・DELETE解除エンドポイントで初めて必須の`@RequestParam`（デフォルト値なし）を使用したところ、パラメータ欠落・型不一致時にSpringが送出する`MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException`が`GlobalExceptionHandler`（UNIT-02で新設）に未捕捉のまま`Exception`ハンドラに落ち、500 Internal Server Errorとして返っていた。両例外をBR-API-01の`VALIDATION_ERROR`（400）として扱うハンドラを追加して解消した（UNIT-02のドキュメントに追記注記を追加）。
- **Spring 7での非推奨API**: `PermissionYamlImportRejectedException`の`HttpStatus.UNPROCESSABLE_ENTITY`、テストコードの`MockMvcResultMatchers.isUnprocessableEntity()`がいずれもSpring Framework 7.0で非推奨と判明し、それぞれ`UNPROCESSABLE_CONTENT`/`isUnprocessableContent()`へ置き換えた。
- **YAMLエクスポートのダウンロード応答**: `GET .../export`は`Content-Disposition: attachment`ヘッダを付与し、ブラウザでのファイルダウンロードとして扱われるようにした（`application/x-yaml`のContent-Type）。

## テスト結果

`@WebMvcTest`による2コントローラ・テスト（`GroupControllerTest` 7件、`PermissionControllerTest` 8件、計15件）、すべて成功。管理者ロールチェック（401/403）、バリデーションエラー、DELETE時のクエリパラメータ必須確認、YAML importのサイズ超過エラー、サービス例外のHTTPステータス変換を確認済み。バックエンド全体の回帰テストも212件全件成功を確認した。

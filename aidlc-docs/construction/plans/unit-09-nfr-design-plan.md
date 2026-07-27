# UNIT-09 監査ログ閲覧 - NFR Design 計画

nfr-requirements.md／tech-stack-decisions.mdの決定事項（`JpaSpecificationExecutor`による動的絞込、`(connection_id, occurred_at)`複合インデックス新設、絞込パラメータの入力検証、`/api/admin/**`によるエンドポイント全体遮断、`findAllById`による名前解決、ページサイズ設定の独立定数化）を、具体的な設計パターン・論理コンポーネントに落とし込む。

## Scalability Patterns（前提確認）

requirements.mdの前提（同時利用者約10名規模）により、新規のスケーリング機構は不要（N/A、UNIT-01〜08と同様）。

## Reliability Patterns（既に決定済みの事項の確認）

新規マイグレーション（`V??__add_index_audit_log_entry_connection_occurred_at.sql`、番号はCode Generation時に確定）をCode Generation時に追加する（既存マイグレーションを修正せず新規ファイルとして追加、Flywayの原則どおり）。

## Security Patterns（既に決定済みの事項の確認）

`/api/admin/audit-log/**`は、既存の`SecurityConfig`の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールでカバーされる（UNIT-02の`AdminUserController`、UNIT-04の`GroupController`と同様）。UNIT-08のような、Controller層でのJWT `role`クレーム判定によるフェイルクローズ実装は不要（BR-AUDITVIEW-03、Functional Design Q2=Aで確定済み）。本ステージでは追加の質問とせず、対応済みとして扱う。

## 計画チェックリスト

- [ ] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `nfr-design-patterns.md`（エラー表現・例外設計、絞込パターン）を作成する
- [ ] Step D: `logical-components.md`（新設する論理コンポーネント、Controller構成）を作成する
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Security Patterns）
絞込パラメータ（ページサイズ超過、日時範囲の開始＞終了）の検証エラー表現は？

A) Bean Validation（`@Valid`＋制約アノテーション、相関検証は`@AssertTrue`）でリクエストDTOレベルの検証を行い、UNIT-02〜08で確立済みの標準400エラー応答（`MethodArgumentNotValidException`ハンドリング）で統一する。専用例外は新設しない

B) 専用の`ApiException`サブクラス（`AuditLogInvalidParameterException`）を新設する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 2（Logical Components、重要）
監査ログ一覧の絞込・ページング・名前解決（対象ユーザ名・対象接続名の一括解決）ロジックの配置は？

A) 新規`AuditLogQueryService`を新設し、絞込・ページング・名前解決の3責務を1クラスに集約する（UNIT-08の`QueryHistoryService`と同程度の粒度の単一サービスクラス。記録専用の既存`AuditLogService`とは責務を分離し、閲覧系ロジックには手を入れない）

B) 絞込・ページングと名前解決を別クラスに分離する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 3（Logical Components）
Controller構成は？

A) 新規`AuditLogController`を`/api/admin/audit-log`に配置し、監査ログ一覧取得の単一エンドポイントのみを持つ（対象ユーザ・対象接続セレクタの選択肢は既存の`GET /api/admin/users`・`GET /api/admin/rdbms-connections`を再利用するため、本ユニット固有の補助エンドポイントは不要、frontend-components.md参照）

B) 複数のエンドポイント・Controllerに分割する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 4（Logical Components）
動的絞込クエリの`Specification`実装の構成は？

A) UNIT-08の`QueryHistorySpecifications`と同じパターンで、静的ファクトリメソッド群（`occurredAtFrom`、`occurredAtTo`、`eventTypeEquals`、`userIdEquals`、`connectionIdEquals`、`resultStatusEquals`）を持つ`AuditLogSpecifications`クラスを新設する

B) `AuditLogQueryService`内にインラインで実装する（専用クラスを設けない）

C) Other (please describe after [Answer]: tag below)

[Answer]: 

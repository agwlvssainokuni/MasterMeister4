# UNIT-09 監査ログ閲覧 - NFR Design Patterns

`unit-09-nfr-design-plan.md`の回答（Q1〜Q4、推奨どおり全問A）に基づく実装パターンを記載する。

---

## 1. Security（入力検証・エラーハンドリング）

### 1.1 絞込パラメータの検証エラー表現（Q1=A、SECURITY-05）

- ページサイズ上限は`AuditLogController`自身が持つ独立した定数（`DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE`、`QueryHistoryController`と共有しない、NFR Requirements完了報告後のユーザー指摘に基づく方針、tech-stack-decisions.md §4参照）に対する明示的な検証で表現する
- 発生日時範囲の相関検証（`occurredAtFrom`≤`occurredAtTo`）は、設計上はリクエストDTOに`@AssertTrue`メソッド（例: `isDateRangeValid()`）を設け検証する想定とする。UNIT-07で発見済みの教訓（`@AssertTrue`メソッドがJacksonのgetter規則に合致しレスポンスJSONへ漏れる問題）を踏まえ、追加する際は`@JsonIgnore`を忘れず付与する
- **Code Generation時の実装方式についての留意事項**: UNIT-08は当初Bean Validation付きのリクエストDTO（`QueryHistorySearchRequest`）を想定していたが、実装段階で既存のGETエンドポイント（`SavedQueryController`、`MasterDataController`等）がいずれも個々の`@RequestParam`で受け取るパターンで統一されていたため、既存パターンとの一貫性を優先し個々の`@RequestParam`に変更した経緯がある。本ユニットも同じ判断基準（既存パターンとの一貫性）をCode Generation計画時に再確認し、必要であれば同様に変更する
- 検証失敗時はUNIT-02〜08で確立済みの標準400エラー応答（`MethodArgumentNotValidException`のグローバルハンドラ処理、または`AuditLogInvalidParameterException`による明示的な400応答）とする

### 1.2 アクセス制御（既に決定済みの事項の確認、SECURITY-06・最重要）

`AuditLogController`は`/api/admin/audit-log`配下に配置し、既存の`SecurityConfig`の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールでエンドポイント全体を保護する（BR-AUDITVIEW-03、Functional Design Q2=A）。UNIT-08のような、Controller層でのJWT `role`クレーム判定によるフェイルクローズ実装（一般ユーザのリクエストを`MINE`へ強制する等）は不要。管理者ロールを持つリクエストのみがController到達前のフィルタ層を通過し、到達した時点で全件が閲覧対象となる。`RdbmsConnectionController`（UNIT-03）と同じ「エンドポイント単位でのアクセス制御」パターンを踏襲する。

---

## 2. Performance（動的クエリ・インデックス）

### 2.1 Specificationによる動的絞込（Q4=A、tech-stack-decisions.md §1の詳細化）

`AuditLogSpecifications`（UNIT-08の`QueryHistorySpecifications`と同じパターンの静的ファクトリメソッド集、新設）に、各条件に対応する`Specification<AuditLogEntry>`生成メソッドを用意する: `occurredAtFrom(Instant)`、`occurredAtTo(Instant)`、`eventTypeEquals(AuditEventType)`、`userIdEquals(Long)`、`connectionIdEquals(Long)`、`resultStatusEquals(ResultStatus)`。`AuditLogQueryService`がこれらを条件の有無に応じて`Specification.where(...).and(...)`で連結する。

### 2.2 インデックス（NFR Requirements確定事項）

新規マイグレーション（`V??__add_index_audit_log_entry_connection_occurred_at.sql`、番号はCode Generation時に確定）で`(connection_id, occurred_at)`の複合インデックスを追加する（既存マイグレーションは変更しない）。

---

## 3. Logical Components（配置方針の要約、詳細はlogical-components.md）

- 絞込・ページング・名前解決: `AuditLogQueryService`（Q2=A、3責務を1クラスに集約。記録専用の既存`AuditLogService`とは別クラスとし、閲覧系ロジックの追加によって記録処理に手を入れない）
- 動的クエリ構築: `AuditLogSpecifications`（Q4=A、新設、静的ファクトリメソッド集）
- Controller: 単一の`AuditLogController`（Q3=A、監査ログ一覧取得の単一エンドポイントのみ）

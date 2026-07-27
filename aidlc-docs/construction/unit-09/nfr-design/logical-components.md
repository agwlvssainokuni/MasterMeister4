# UNIT-09 監査ログ閲覧 - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.audit`（既存、UNIT-02実装済み。本ユニットはController・閲覧用Service・関連DTOを同パッケージに追加する）。

---

## 1. 監査ログ閲覧ドメイン（`cherry.mastermeister.audit`）

### AuditLogController（新規、Q3=A）

単一のControllerに監査ログ一覧取得の1エンドポイントのみを持つ。

- `GET /api/admin/audit-log?occurredAtFrom=...&occurredAtTo=...&eventType=...&userId=...&connectionId=...&resultStatus=...&page=0&pageSize=...` — 絞込・ページング付きの監査ログ一覧取得

対象ユーザ・対象接続セレクタの選択肢は、本Controllerでは提供しない。フロントエンドは既存の`GET /api/admin/users`（UNIT-02）・`GET /api/admin/rdbms-connections`（UNIT-03）をそのまま利用する（frontend-components.mdで確認済み、いずれも管理者専用・全件取得のため情報漏洩リスクなし）。

既存の`SecurityConfig`の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールでカバーされるため、新規のSecurityFilterChainルール追加は不要（`RdbmsConnectionController`と同様、クラスJavadocに「全エンドポイントとも既存のSecurityFilterChain設定により管理者ロール必須」である旨のコメントを付与する）。ロール判定・実行者スコープのフィルタ変換（UNIT-08の`executedByFilter`のような処理）はController・Service層のいずれにも存在しない。

ページサイズの上限は本Controller固有の`private static final int DEFAULT_PAGE_SIZE`・`MAX_PAGE_SIZE`定数として定義する（`QueryHistoryController`の定数を共有・参照しない、NFR Requirements完了報告後のユーザー指摘に基づく方針）。値はひとまずUNIT-08と同じ`DEFAULT_PAGE_SIZE=50`・`MAX_PAGE_SIZE=200`とする。

### AuditLogQueryService（新設、Q2=A）

絞込・ページング・名前解決の3責務を1クラスに集約する。既存の`AuditLogService`（記録専用、`@TransactionalEventListener`による同期記録）とは責務を分離し、本クラスの追加によって既存の記録処理には一切変更を加えない。

- `listAuditLog(AuditLogSearchCriteria criteria, Pageable pageable): Page<AuditLogEntryView>`
  - `AuditLogSpecifications`（nfr-design-patterns.md §2.1）で、絞込条件（発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータス）のうち指定されたものだけを動的に`Specification<AuditLogEntry>`として組み立てる
  - 組み立てた`Specification`で`AuditLogEntryRepository.findAll(spec, pageable)`（`JpaSpecificationExecutor`）を呼び出す
  - 取得した`Page<AuditLogEntry>`の内容から`userId`（非null）・`connectionId`（非null）をそれぞれユニークに集約し、`UserRepository.findAllById(...)`（既存標準メソッド）・`RdbmsConnectionRepository.findAllById(...)`（既存標準メソッド）で一括取得
  - 上記を結合して`AuditLogEntryView`のページ結果に変換して返す。`userId`/`connectionId`が非nullだが解決できない場合はそれぞれ「(不明なユーザ)」「(削除済み接続)」のプレースホルダーを付与する

### AuditLogSpecifications（新設、Q4=A、tech-stack-decisions.md §1・nfr-design-patterns.md §2.1）

`Specification<AuditLogEntry>`を生成する静的ファクトリメソッド集。

- `occurredAtFrom(Instant from)` / `occurredAtTo(Instant to)`
- `eventTypeEquals(AuditEventType eventType)`
- `userIdEquals(Long userId)`
- `connectionIdEquals(Long connectionId)`
- `resultStatusEquals(ResultStatus resultStatus)`

---

## 2. Repository拡張

### AuditLogEntryRepository（既存、UNIT-02実装済み、現状は空の`JpaRepository`）

- `JpaSpecificationExecutor<AuditLogEntry>`を追加実装（Q4=A関連の動的クエリのため）
- 追加のカスタムクエリメソッドは不要（絞込条件はすべて`AuditLogSpecifications`で表現できるため）

### UserRepository（既存、UNIT-02実装済み）

- 変更なし。標準`JpaRepository.findAllById(Iterable<Long>)`をそのまま利用

### RdbmsConnectionRepository（既存、UNIT-03実装済み）

- 変更なし。標準`JpaRepository.findAllById(Iterable<Long>)`をそのまま利用

---

## 3. DTO設計

- `AuditLogEntryResponse`（`AuditLogEntryView`のフィールドに対応: `id`, `occurredAt`, `userId`, `userDisplayName`, `connectionId`, `connectionDisplayName`, `eventType`, `targetResource`, `resultStatus`, `detail`） — 監査ログ一覧APIレスポンス1件
- `AuditLogSearchCriteria`（`occurredAtFrom`, `occurredAtTo`, `eventType`, `userId`, `connectionId`, `resultStatus` — Service層（`AuditLogQueryService.listAuditLog`）に渡すDTO。UNIT-08の`executedByScope`→`executedByFilter`のような変換は本ユニットには存在しないため、Controllerが受け取ったクエリパラメータをほぼそのまま本DTOに詰め替える）
- リクエスト側のバインド方式（個々の`@RequestParam` vs `@ModelAttribute`によるDTOバインド）はCode Generation計画時に、既存GETエンドポイントとの一貫性を踏まえて確定する（nfr-design-patterns.md §1.1参照）

---

## 4. 依存関係の追加

なし。Spring Data JPAの`JpaSpecificationExecutor`は既存の`spring-boot-starter-data-jpa`に含まれる標準機能。

---

## 5. 設定（`AppProperties`拡張）

新規設定項目なし。ページサイズ上限は`AuditLogController`固有の定数として直接埋め込む（UNIT-08と同じ判断: 運用時調整可能な設定値ではなく固定的な安全上限のため、`application.yml`外部化は不要）。

---

## 6. Spring Security設定の変更

なし。既存の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールが`/api/admin/audit-log/**`をカバーする（UNIT-02/04の前例と同様の確認結果）。

---

## 7. マイグレーション

新規マイグレーション（`V??__add_index_audit_log_entry_connection_occurred_at.sql`、番号はCode Generation時に既存マイグレーション一覧を確認のうえ確定）を追加する。

```sql
CREATE INDEX idx_audit_log_entry_connection_occurred_at
    ON audit_log_entry (connection_id, occurred_at);
```

---

## 8. 監査ログ連携

なし。本ユニットはDB更新を伴わない読み取り専用の閲覧処理のみであり、`AuditEventPublisher`への新規イベント発行は行わない（NFR Requirements Q2=Aで確定済み: 監査ログ閲覧自体は新たな監査記録対象としない）。

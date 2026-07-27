# UNIT-09 監査ログ閲覧 - Tech Stack Decisions

`unit-09-nfr-requirements-plan.md`の回答（Q1〜Q5、推奨どおり全問A）に基づく。新規外部ライブラリの追加はなし。

---

## 1. 絞込クエリの実装方式（Functional Design Q3=A、UNIT-08と同じパターンの再利用）

`AuditLogEntryRepository`に`JpaSpecificationExecutor<AuditLogEntry>`を実装させ、`Specification<AuditLogEntry>`を動的に組み立てる。絞込条件（発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータス）はいずれも任意指定のため、指定された条件に対応する`Predicate`のみを`Specification.where(...).and(...)`で連結する。未指定の条件は`Specification`自体を追加しない（`null`分岐によるANDスキップ）ことで対応する。UNIT-08の`QueryHistorySpecifications`と同様に、静的ファクトリメソッド群を持つ`AuditLogSpecifications`クラスとして実装する（クラス名・パッケージ配置はCode Generation時に確定）。

```java
// イメージ（詳細実装はCode Generation時に確定）
Specification<AuditLogEntry> spec = Specification.where(null);
if (occurredAtFrom != null) {
    spec = spec.and(occurredAtFrom(occurredAtFrom));
}
if (occurredAtTo != null) {
    spec = spec.and(occurredAtTo(occurredAtTo));
}
if (eventType != null) {
    spec = spec.and(eventTypeEquals(eventType));
}
// ...以下同様（userId, connectionId, resultStatus）
```

## 2. インデックス設計（BR-AUDITVIEW-10、Functional Design Q7=A）

新規マイグレーション（Code Generation時に番号確定）で、`(connection_id, occurred_at)`の複合インデックスを`audit_log_entry`テーブルに追加する。

```sql
CREATE INDEX idx_audit_log_entry_connection_occurred_at
    ON audit_log_entry (connection_id, occurred_at);
```

本ユニットの主要な絞込・ソートパターン（対象接続で絞り込み発生日時降順にソート）をこのインデックスでカバーする。既存の`occurred_at`・`event_type`・`user_id`単独インデックスは変更しない。

## 3. 長期的なデータ量増加への対応（Q3=A）

アーカイブ・パーティショニング等の追加機構は本ユニットでは導入しない。`audit_log_entry`は削除機能を持たず増加し続けるテーブルだが、社内10名規模の想定利用量ではインデックス追加（上記§2）により当面の絞込・ページング性能は確保できると判断する。将来的にデータ量が実用上の問題となった場合は、別ユニット・別課題として検討する（本ユニットのスコープ外）。

## 4. 絞込パラメータの入力検証（Q1=A、SECURITY-05）

- ページサイズ: UNIT-08と同じ既定値・上限値を踏襲する（Q4=A、具体的な数値はUNIT-08の`DEFAULT_PAGE_SIZE=50`/`MAX_PAGE_SIZE=200`をそのまま踏襲、Code Generation時に確定）。上限超過時は400エラー
- 発生日時範囲: `occurredAtFrom`が`occurredAtTo`より後の場合は400エラー
- `eventType`/`resultStatus`: 許容値以外は400エラー（enumバインドの標準的な失敗応答）
- `userId`/`connectionId`: JPA Criteria APIのパラメータバインドで扱われるため追加のサニタイズ処理は不要
- 上記のうちBean Validationで表現しにくいもの（日時範囲の相関検証）は、UNIT-08の`QueryHistoryInvalidParameterException`と同じパターンの新規`AuditLogInvalidParameterException`（400 BAD_REQUEST）で表現する

## 5. 監査ログ閲覧自体の監査記録要否（Q2=A、SECURITY-14）

監査ログの閲覧（大量閲覧・網羅的な閲覧を含む）は、新たな監査記録対象としない。`AUDIT_LOG_VIEWED`のような新規イベント種別は追加しない。

**理由（実装確認による訂正）**: 当初「UNIT-05〜08の閲覧系機能はいずれも監査対象としていない」という一般化で説明したが、これは事実誤認だった。UNIT-05の`MasterDataService`は、`recordPage.rows().size() >= appProperties.audit().bulkAccessThreshold()`の場合に`MASTER_DATA_BULK_ACCESSED`イベントを実際に記録しており（`MasterDataService.java:170-174`）、大量閲覧を明示的な監査対象としている。

正しい理由は次のとおり。UNIT-05のマスタデータ参照は一般ユーザ向け機能であり、アクセス制御下にあるとはいえ大量データの一括取得はデータ抽出・情報漏洩の兆候として検知する価値がある。一方、本ユニット（監査ログ閲覧）は管理者専用機能（BR-AUDITVIEW-03）であり、想定される利用者は少数の管理者に限定される。管理者が監査ログ全件を閲覧する行為は、不正の兆候ではなくむしろ想定された通常の業務行為（インシデント調査、定期監査等）であるため、大量アクセスを検知・記録する必要性がUNIT-05とは異なり低い。アクセス自体の追跡はアプリケーションログ（SECURITY-03、既存基盤）に委ねる。

## 6. アクセス制御レイヤー（Functional Design Q2=A、SECURITY-06、確認・復唱）

`AuditLogController`（仮称）を`/api/admin/audit-log`配下に配置し、既存の`SecurityConfig`の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールでエンドポイント全体を保護する。UNIT-08のような、Controller層でのJWT `role`クレーム判定によるデータ絞込（フェイルクローズ）は不要（本ユニットは管理者専用であり、アクセスできた時点で全件が閲覧対象となるため）。

## 7. 対象ユーザ・対象接続の表示名解決方式（Q5=A）

新規の`AuditLogQueryService`（仮称）が一覧取得結果（`Page<AuditLogEntry>`）から、`userId`・`connectionId`（いずれも非null）をそれぞれユニークに集約し、既存の`UserRepository.findAllById(...)`・`RdbmsConnectionRepository.findAllById(...)`で一括取得する。取得結果を`Map<Long, String>`に変換し、各`AuditLogEntry`を`AuditLogEntryView`へ変換する際に表示名を解決する。新規のキャッシュ層（Caffeine等）は導入しない。

## 8. 新規依存関係

なし。Spring Data JPAの`JpaSpecificationExecutor`は既存の`spring-boot-starter-data-jpa`に含まれる標準機能であり、追加のライブラリ依存は発生しない。

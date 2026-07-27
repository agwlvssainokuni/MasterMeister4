# UNIT-09 監査ログ閲覧 - NFR Requirements

`unit-09-nfr-requirements-plan.md`の回答（Q1〜Q5、推奨どおり全問A）に基づく。

---

## 1. Scalability（規模）

- requirements.mdの前提（同時利用者約10名規模の社内ツール）により、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜08と同様）
- `audit_log_entry`テーブルはUNIT-02〜08の全イベントが記録され続け削除機能を持たないため、`(connection_id, occurred_at)`の複合インデックスを新規追加し、主要アクセスパターンに対する長期的なパフォーマンス劣化を防ぐ（BR-AUDITVIEW-10、Functional Design Q7=A）
- 長期的なデータ量増加に対し、アーカイブ・パーティショニング等の追加機構は現時点では導入しない（Q3=A）。社内10名規模の想定利用量では複合インデックス追加で当面は実用上問題ないと判断し、将来必要になった場合は別途検討する
- 一覧のページサイズは`AuditLogController`自身の独立した定数として定義し、値はひとまずUNIT-08と同じ既定値・上限値（既定50件・上限200件）を採用する（Q4=A、ユーザー指摘により追記: 実装上の設定項目はUNIT-08と共有せず分離し、監査ログ固有のデータ量・利用パターンに応じて本ユニット単独で将来調整できるようにする。詳細はtech-stack-decisions.md §4参照）

## 2. Performance（性能）

- 絞込条件の動的な組み合わせは、Functional Design段階で確定済みの`JpaSpecificationExecutor`（Specification API、UNIT-08で確立したパターンの再利用）で実装する
- 対象ユーザ・対象接続の表示名解決は、一覧取得のたびに該当IDを集約して`findAllById`で一括取得する（Q5=A、N+1回避）。新規キャッシュ層は追加しない
- `(connection_id, occurred_at)`複合インデックス（BR-AUDITVIEW-10）により、主要な絞込・ソートパターンをインデックスでカバーする

## 3. Availability（可用性）

- requirements.mdの前提により大規模な高可用性要件はN/A（UNIT-01〜08と同様）

## 4. Security（セキュリティ、Security Baseline拡張）

該当ルール評価:

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 新規DB永続化エンティティなし（既存AuditLogEntryの閲覧のみ） |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | N/A（対応済み） | UNIT-02〜08で確立済みのログ基盤をそのまま利用 |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | ページサイズ上限、発生日時範囲の妥当性（開始≤終了）検証（Q1=A）。`eventType`/`resultStatus`はenum値、`userId`/`connectionId`はID値でありJPA Criteria APIのパラメータバインドを通るためSQLインジェクションのリスクはない |
| SECURITY-06（最小権限アクセスポリシー） | 該当・最重要 | 管理者専用エンドポイント（`/api/admin/**`、Functional Design Q2=A、BR-AUDITVIEW-03）でエンドポイント全体を遮断。UNIT-08（一般ユーザ向けロールベース絞込）とは異なる制御方式 |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | 既存のJWT認証、`/api/admin/**`は`SecurityConfig`の`hasRole("ADMIN")`ルールで保護済み（UNIT-02/04で確立済み） |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | 記録の不変性（BR-AUDITVIEW-08）、管理者専用アクセス制御（BR-AUDITVIEW-03） |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 読み取り専用の閲覧処理のみ、DB更新なし |
| SECURITY-14（アラート・監視） | 該当（判断確定） | 監査ログの閲覧自体（大量閲覧含む）は新たな監査記録対象としない（Q2=A）。**事実確認と理由の訂正**: 当初「UNIT-05〜08の閲覧系機能はいずれも監査対象としていない」としたが誤りで、UNIT-05の`MasterDataService`は`bulkAccessThreshold`超過時に`MASTER_DATA_BULK_ACCESSED`として大量閲覧を実際に監査記録している（`MasterDataService.java`）。正しい理由は、UNIT-05のマスタデータ参照が一般ユーザ向け機能でありデータ抽出・情報漏洩の兆候検知が目的であるのに対し、本ユニットは管理者専用機能（BR-AUDITVIEW-03）であり、管理者が監査ログ全件を閲覧する行為自体は通常の業務行為であって不正の兆候とはみなせない点にある。この違いにより、本ユニットでは大量アクセス検知の仕組みを導入しない。アクセス自体はアプリケーションログ（SECURITY-03）で追跡可能 |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。絞込パラメータ不正時は新規`AuditLogInvalidParameterException`（UNIT-08の`QueryHistoryInvalidParameterException`と同じパターン）で対応 |

## 5. Reliability（信頼性）

- 絞込パラメータの入力検証（Q1=A）により、異常な入力（不正な日時範囲、過大なページサイズ）に対する安全側の挙動を確保する
- 管理者専用アクセス制御（BR-AUDITVIEW-03）により、エンドポイント単位で権限昇格リクエストを遮断する
- 例外はUNIT-02のグローバル例外ハンドラで処理する

## 6. Maintainability / Tech Stack

- 詳細は`tech-stack-decisions.md`を参照

## 7. Property-Based Testing（拡張）

- STORY-9.1にPBT対象外と明記済み（stories.md）。追加のPBT対象識別は不要（N/A）

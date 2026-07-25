# UNIT-06 クエリ保存・実行 - NFR Requirements

`unit-06-nfr-requirements-plan.md`の回答（Q1〜Q6、全問A）に基づく。

---

## 1. Scalability（規模）

- requirements.mdの前提（同時利用者約10名規模の社内ツール）により、アプリケーション自体の大規模スケーラビリティ要件はN/A
- 対象RDBMS側は任意規模のマスタデータ（UNIT-05前提を踏襲、数万〜数十万件のテーブル）を直接対象とするため、ページング無効時の結果件数に安全上限（10,000件）を設ける（Q4=A）

## 2. Performance（性能）

- ページング有効時は、ユーザの任意SELECT文をサブクエリでラップしLIMIT/OFFSETを適用する方式とする（Q2=A）。4対象方言（PostgreSQL/MySQL/MariaDB/H2）はいずれもLIMIT/OFFSET構文をサポートするため、UNIT-05の`RecordQueryService`と同じ直書き方式を踏襲できる
- スキーマ許可リスト判定は、既存の`EffectivePermissionResolver.resolvePrimary`（UNIT-04のCaffeineキャッシュ対象）をループ呼び出しする方式とし、新規の集約キャッシュ層は追加しない（Q6=A）

## 3. Availability（可用性）

- requirements.mdの前提により大規模な高可用性要件はN/A（UNIT-01〜UNIT-05と同様）
- 個々のクエリ実行がハングした場合にアプリケーション全体へ影響しないよう、クエリ単位のタイムアウト制御を設ける（Q3=A）

## 4. Security（セキュリティ、Security Baseline拡張）

該当ルール評価:

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 新規永続化エンティティ（`SavedQuery`/`QueryExecutionRecord`）は機微情報を含まない |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | `QUERY_EXECUTED`等のAuditEventPublisher記録（BR-QUERY-10） |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | SQL読み取り専用検証（BR-QUERY-01）、接続管理方式（Q1=A）、スキーマ許可リスト判定（Q6=A） |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | スキーマ単位のアクセス制御（BR-QUERY-02〜04） |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | 公開範囲・作成者限定操作の判定（BR-QUERY-05〜09） |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | フェイルクローズなスキーマ許可リスト（BR-QUERY-02〜03）、読み取り専用検証（BR-QUERY-01） |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 読み取り専用のみ |
| SECURITY-14（アラート・監視） | 該当 | クエリタイムアウト（Q3=A）、結果件数上限（Q4=A）で長時間実行・過大結果を制御。専用の監視・アラート機構は新設しない（UNIT-02のログベース検知の対象範囲内） |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用 |

## 5. 監査ログ（requirements.md §6.1関連）

- `QUERY_EXECUTED`（BR-QUERY-10）は実行のたびに結果件数（rowCount）を含めて必ず記録するため、UNIT-05のような閾値ベースの専用「大量データ取得」イベントは追加しない（Q5=A）。管理者は`QUERY_EXECUTED`をrowCountで絞り込むことで大量データ取得を把握できる

## 6. Reliability（信頼性）

- クエリ実行タイムアウト（Q3=A、JDBC標準`Statement.setQueryTimeout`）
- ページング無効時の結果件数上限（Q4=A、10,000件超で400エラー）
- 上記いずれも、例外はUNIT-02のグローバル例外ハンドラで処理する

## 7. Maintainability / Tech Stack

- 詳細は`tech-stack-decisions.md`を参照

## 8. Property-Based Testing（拡張）

- フレームワークはUNIT-02でjqwikに確定済み。追加決定なし（N/A）
- business-logic-model.md §9のテスト可能プロパティ（SQL読み取り専用検証、パラメータ検出、スキーマ許可リスト判定）は、そのままNFR Design/Code Generationで具体化する

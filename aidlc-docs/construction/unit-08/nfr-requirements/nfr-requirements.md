# UNIT-08 クエリ履歴 - NFR Requirements

`unit-08-nfr-requirements-plan.md`の回答（Q1〜Q5、推奨どおり全問A）に基づく。

---

## 1. Scalability（規模）

- requirements.mdの前提（同時利用者約10名規模の社内ツール）により、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜07と同様）
- `query_execution_record`テーブルは削除機能を持たずクエリ実行のたびに増加し続けるため、`(connection_id, executed_at)`の複合インデックスを新規追加し、主要アクセスパターン（接続指定＋日時降順ソート）に対する長期的なパフォーマンス劣化を防ぐ（Q2=A）
- 一覧のページサイズはNFR Design/Code Generation段階で上限を確定する（Q3=A、Bean Validation等での検証）

## 2. Performance（性能）

- 絞込条件の動的な組み合わせは`JpaSpecificationExecutor`（Specification API）で実装する（Q1=A）。指定された条件のみを動的にAND結合するため、未指定条件の分岐によるクエリの複雑化を避けられる
- 実行者表示名・保存クエリ名の解決は、一覧取得のたびに該当IDを集約して`findAllById`で一括取得する（Q5=A、N+1回避）。新規キャッシュ層は追加しない
- `(connection_id, executed_at)`複合インデックス（Q2=A）により、主要な絞込・ソートパターンをインデックスでカバーする

## 3. Availability（可用性）

- requirements.mdの前提により大規模な高可用性要件はN/A（UNIT-01〜07と同様）

## 4. Security（セキュリティ、Security Baseline拡張）

該当ルール評価:

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 新規DB永続化エンティティなし（既存QueryExecutionRecordの閲覧のみ） |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | N/A | 閲覧のみでデータ変更を伴わないため既存ログ基盤で十分 |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | ページサイズ上限、日時範囲の妥当性（開始≤終了）、`executedByScope`の値域検証（Q3=A）。SQLキーワードはJPA Criteria APIのパラメータバインドで扱うためSQLインジェクションのリスクはない |
| SECURITY-06（最小権限アクセスポリシー） | 該当・最重要 | 実行者スコープ「全ユーザ」は管理者限定、Controller層でロール判定しフェイルクローズで一般ユーザは「自分のみ」に強制（Q4=A、BR-QUERYHISTORY-03） |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | JWT認証必須（ロール不問）、`/api/query-history/**`はBearer認証必須 |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | 記録の不変性（BR-QUERYHISTORY-04）、実行者スコープのフェイルクローズ（BR-QUERYHISTORY-03） |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 読み取り専用の閲覧処理のみ、DB更新なし |
| SECURITY-14（アラート・監視） | N/A | 記録済みデータの閲覧のみで実行を伴わない |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用 |

## 5. Reliability（信頼性）

- 絞込パラメータの入力検証（Q3=A）により、異常な入力（不正な日時範囲、過大なページサイズ）に対する安全側の挙動を確保する
- 実行者スコープのフェイルクローズ（Q4=A）により、権限昇格を試みる不正なリクエストに対しても安全側に倒す
- 例外はUNIT-02のグローバル例外ハンドラで処理する

## 6. Maintainability / Tech Stack

- 詳細は`tech-stack-decisions.md`を参照

## 7. Property-Based Testing（拡張）

- STORY-8.1・8.2ともにPBT対象外と明記済み（stories.md）。追加のPBT対象識別は不要（N/A）

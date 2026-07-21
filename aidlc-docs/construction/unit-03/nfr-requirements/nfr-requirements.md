# UNIT-03 RDBMSセットアップ - NFR Requirements

`unit-03-nfr-requirements-plan.md`の回答（Q1=B, Q2=A, Q3=B, Q4=A, Q5=A, Q6=A, Q7=B, Q8=A, Q9=A）に基づく。カテゴリ別のNFR要件と、Security Baseline拡張・Property-Based Testing拡張の該当ルール評価を記載する。技術選定の詳細（アルゴリズム名・ライブラリ等）はtech-stack-decisions.md参照。

---

## 1. Scalability — N/A

requirements.mdの前提（同時利用者約10名規模の社内ツール）により、本ユニット固有のスケーリング設計は不要と判断する。対象RDBMS接続数・スキーマ規模の前提はQ6のとおり、接続数十数件程度、1接続あたり数十〜百数十テーブル程度とする（NFR-03-01）。

## 2. Availability — N/A

requirements.mdにHA・DR要件の記載はない。既存方針（NFR-2.x）と整合する前提で、本ユニット固有のAvailability要件は設けない。

## 3. Performance

- **NFR-03-02**: スキーマ取込の接続タイムアウトは5秒とする
- **NFR-03-03**: スキーマ取込処理全体にもタイムアウト（60秒）を設ける。超過時は失敗として扱う（Q3=B、BR-RDBMS-07のオールオアナッシングに従う）。具体的な実装方式（非同期処理＋タイムアウト監視か、同期処理＋JDBCタイムアウト設定の組み合わせか）はNFR Design段階で確定する

## 4. Reliability

- **NFR-03-04**: 対象RDBMS接続の疎通失敗・スキーマ取込失敗について、専用のアラート機構（通知・監視）は設けない（Q9=A）。管理者による手動操作の結果はUI上のエラー表示とアプリケーションログ（失敗時のみ）で完結させる。UNIT-02のNFR-4.5（ログイン失敗多発時のアラート、外部攻撃者による自動化された試行が対象）とは性質が異なると判断する

## 5. Maintainability

- **NFR-03-05**: 接続情報のバリデーションはBean Validation（`jakarta.validation`）で実装し、UNIT-02の登録・認証系エンドポイントと同じ方式に揃える（Q4=A）
- **NFR-03-06**: テストフレームワークはNFR-9.1・NFR-5.2のとおりJUnit5 + Mockito、必要に応じてjqwikを踏襲する。本ユニットはbusiness-logic-model.md §7のとおりPBT対象プロパティなしと判断済みのため、jqwikの新規適用対象はない

## 6. Usability — N/A

Functional Design（frontend-components.md）で対応済み。追加のUsability要件は設けない。

---

## 7. 対象RDBMS接続に関するセキュリティ運用ガイダンス（Q7=B）

- **NFR-03-07**: `backend/README.md`に、対象RDBMS接続に使用するDBユーザは最小権限（読取専用、または本アプリの用途に必要な範囲のみ）とすることを推奨する旨のドキュメントを追加する（SECURITY-06）。アプリケーション側での権限チェック・強制は行わない（DBユーザの権限設定自体は運用者の責任範囲）

---

## 8. Security Baseline拡張 該当ルール評価

| ルール | 判定 | 本ユニットでの対応方針 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | 該当 | 接続パスワードはAES-256-GCMで可逆暗号化して保存（Q1=B）。対象RDBMSとの通信時暗号化（TLS）はデフォルト無効とし、管理者が`additionalParams`で明示的に有効化する運用とする（Q2=A） |
| SECURITY-02（ネットワーク中継のアクセスログ） | N/A | リバースプロキシ等の中間機器は本ユニットのスコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（CONNECTION_REGISTERED/UPDATED/DELETED, SCHEMA_IMPORTED）で対応済み。追加検討事項なし |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み・再決定不要） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（入力バリデーション） | 該当 | Bean Validationによる形式チェック（Q4=A、NFR-03-05） |
| SECURITY-06（最小権限アクセスポリシー） | 該当 | README等での運用ガイダンス記載（Q7=B、NFR-03-07）。アプリケーションによる強制は行わない |
| SECURITY-07（制限的なネットワーク構成） | N/A | Infrastructure DesignがSKIP判定のユニットであり対象外 |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み・再確認） | UNIT-02のJWT認証・ロールチェックを流用し、本ユニットの全エンドポイントを管理者専用とする（frontend-components.md §4） |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | 該当 | TLSデフォルト無効の判断（Q2=A）はローカル開発環境（devenv）の実態に合わせた現実的な設定であり、本番運用では管理者が対象RDBMSの実際のTLS設定に応じて`additionalParams`を設定する運用とする（README等での注記が必要。tech-stack-decisions.md参照） |
| SECURITY-10（ソフトウェアサプライチェーン） | 該当 | 新規追加するJDBCドライバ（MySQL/MariaDB/PostgreSQL/H2）は、UNIT-01で導入済みのOWASP Dependency-Checkプラグインの既存スキャン対象に自動的に含まれる（Q8=A、追加設定不要） |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | フェイルクローズ・管理者専用境界の明示、オールオアナッシングによるスキーマ取込の一貫性保証（BR-RDBMS-07） |
| SECURITY-12（認証・認証情報管理） | 該当 | 接続パスワードの暗号化アルゴリズム・鍵ローテーション方式をQ1=Bとして確定（詳細はtech-stack-decisions.md） |
| SECURITY-13（データ整合性検証） | N/A | 本ユニットに署名検証等のデータ整合性要件はない |
| SECURITY-14（アラート・監視） | N/A | 対象RDBMS接続テスト・スキーマ取込は管理者による手動操作であり、外部からの自動化された攻撃対象ではないため専用のアラート機構は不要と判断（Q9=A） |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。エラー分類（BR-RDBMS-04）は業務要件としてFunctional Designで確定済み |

## 9. Property-Based Testing拡張

該当なし。Functional Design（business-logic-model.md §7）で、型正規化・方言解決のロジックは入力値が有限かつ少数でありPBT対象プロパティなしと判断済み。本ステージでの追加検討も不要。

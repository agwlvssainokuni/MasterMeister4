# UNIT-07 クエリビルダー - NFR Requirements

`unit-07-nfr-requirements-plan.md`の回答（Q1〜Q6、推奨どおり全問A）に基づく。

---

## 1. Scalability（規模）

- requirements.mdの前提（同時利用者約10名規模の社内ツール）により、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜UNIT-06と同様）
- `generateSql`はDBアクセスを伴わない純粋な変換であり、負荷特性上の懸念はない
- `QueryBuilderState`リクエストの各リスト項目数に上限を設ける（Q5=A: SELECT/WHERE/HAVING/ORDER BY各50件、JOIN20件）。過大なリクエストによるリソース消費・生成SQLの肥大化を防ぐ

## 2. Performance（性能）

- アクセス可能テーブル/カラム一覧取得は、既存の`EffectivePermissionResolver`のCaffeineキャッシュ（`effectivePermission`）にそのまま任せる。新規の専用キャッシュ層は追加しない（Q6=A、UNIT-05の`listAccessibleTables`と同じ方式）
- SQL生成・リバースエンジニアリングはいずれもJSqlParserのオブジェクトモデルを介した変換であり、DBアクセスを伴わないため追加の性能懸念はない（Q3=A）

## 3. Availability（可用性）

- requirements.mdの前提により大規模な高可用性要件はN/A（UNIT-01〜UNIT-06と同様）

## 4. Security（セキュリティ、Security Baseline拡張）

該当ルール評価:

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 新規DB永続化エンティティなし（BR-QUERYBUILDER-08） |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | N/A | DB更新を伴わないためAuditEventPublisher記録の対象外。既存ログ基盤で十分 |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | `QueryBuilderState`各リストの件数上限（Q5=A）、WHERE/HAVING比較値の型安全なSQL埋め込み（Q1=A・Q2=A、SQLインジェクション防止） |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | 列単位の実効権限フィルタリング（BR-QUERYBUILDER-01） |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | JWT認証必須（ロール不問、UNIT-05/06と同様）。新規SecurityFilterChainルールの要否はCode Generationで確認 |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし（JSqlParserを再利用） |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | リバースエンジニアリング失敗時のフェイルクローズ（BR-QUERYBUILDER-07）、列単位フィルタリング（BR-QUERYBUILDER-01） |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 読み取り専用の変換処理のみ、DB更新なし |
| SECURITY-14（アラート・監視） | N/A | SQL生成のみで実行を伴わないため、実行時の長時間実行・大量データ取得の懸念はUNIT-06の対応範囲内 |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-QUERYBUILDER-07・11の検証拒否時エラー応答 |

## 5. Reliability（信頼性）

- `QueryBuilderState`各リストの件数上限（Q5=A）と、リバースエンジニアリング失敗時のフェイルクローズ（BR-QUERYBUILDER-07）により、異常な入力に対する安全側の挙動を確保する
- 例外はUNIT-02のグローバル例外ハンドラで処理する

## 6. Maintainability / Tech Stack

- 詳細は`tech-stack-decisions.md`を参照

## 7. Property-Based Testing（拡張）

- フレームワークはUNIT-02でjqwikに確定済み。追加決定なし（N/A）
- business-logic-model.md §8のテスト可能プロパティ（SQL生成/解析のラウンドトリップ、GROUP BY整合性の不変条件、アクセス可能テーブル/カラム一覧のREAD以上不変条件）は、そのままNFR Design/Code Generationで具体化する

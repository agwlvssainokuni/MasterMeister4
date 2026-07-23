# UNIT-05 マスタメンテナンス - NFR Requirements

`unit-05-nfr-requirements-plan.md`の回答（Q1〜Q8=A）に基づく。カテゴリ別のNFR要件と、Security Baseline拡張・Property-Based Testing拡張の該当ルール評価を記載する。技術選定の詳細（ライブラリ・依存関係等）はtech-stack-decisions.md参照。

---

## 1. Scalability

requirements.mdの前提（同時利用者約10名規模の社内ツール）により、アプリケーション自体の大規模スケーリング設計は不要と判断する（NFR-05-01）。ただし対象RDBMS側のテーブル規模は、社内マスタデータの典型的な規模として中規模（1テーブルあたり数万〜数十万件、カラム数は数十程度まで）を前提とする（Q7=A、NFR-05-02）。一括反映バッチには上限（1バッチあたり1,000件）を設ける（Q4=A、NFR-05-03）。

## 2. Availability — N/A

requirements.mdにHA・DR要件の記載はない。既存方針（NFR-2.x）と整合する前提で、本ユニット固有のAvailability要件は設けない。

## 3. Performance

- **NFR-05-04**: レコード一覧のページング（BR-MASTER-10）における総件数取得は、テーブルサイズに関わらず毎回正確なCOUNTクエリを実行する（Q3=A）。同時利用者約10名規模の社内ツールという前提のもと、中規模テーブル（Q7=A）での都度COUNTのコストは許容範囲と判断する
- **NFR-05-05**: 一括反映バッチの合計件数（作成/更新/削除の合計）に上限（1,000件）を設ける（Q4=A）。超過時は400エラーで拒否し、DBへの反映は行わない

## 4. Reliability

- **NFR-05-06（訂正、レビュー指摘の反映）**: 一括反映（BR-MASTER-07のオールオアナッシング）は、宣言的`@Transactional`（アプリ内部DB用の`PlatformTransactionManager`にバインドされ、実行時に選択される対象RDBMS用`DataSource`の制御には使えない）には頼らず、対象接続ごとに都度生成する`DataSourceTransactionManager`と`TransactionTemplate`により明示的にトランザクションを制御する（Q8=A、詳細はtech-stack-decisions.md §8参照）。接続が処理途中で切断された場合もコミット前であればロールバックとして扱われる。UNIT-03のような明示的なタイムアウト制御（`CompletableFuture.orTimeout`）は導入しない
- **NFR-05-07**: SQL手入力の構文検証拒否・一括反映の失敗が多発した場合の専用アラート機構は設けない（Q6=A）。UNIT-02のログベースの検知（NFR-4.5）の対象範囲内とする

## 5. Security

- **NFR-05-08（最重要）**: STORY-4.2のWHERE句・ORDER BY句手入力は、JSqlParserによる構文解析・Visitorパターンでの構文要素検証を経てパラメータ化する（Q1=A、BR-MASTER-04、SECURITY-05）。自前実装によるパーサではなく、実績のある外部ライブラリでSQLインジェクションリスクを低減する
- **NFR-05-09**: 動的なレコードアクセス（テーブル構造が実行時まで不明）は、既存の`RdbmsConnectionService.getDataSource(connectionId)`（HikariCP、UNIT-03既存）と`NamedParameterJdbcTemplate`の組み合わせで実装し、バインドパラメータで値を渡す（Q2=A）。一括反映時は同一`DataSource`に対して`DataSourceTransactionManager`が管理するトランザクションに参加させる（NFR-05-06）
- **NFR-05-10**: 監査ログの「大量データ取得」閾値（デフォルト100件）は`application.yml`の設定値として持たせ、`AppProperties`経由で参照する（Q5=A、BR-MASTER-12）

## 6. Maintainability

- **NFR-05-11**: テストフレームワークはUNIT-02で確定済みのJUnit5 + Mockito + jqwikを踏襲する。本ユニットはbusiness-logic-model.md §7でPBT対象プロパティを識別済み（SQL手入力の構文検証、オールオアナッシング、表示対象カラムの絞り込み）
- **NFR-05-12**: バックエンドパッケージ構成は`cherry.mastermeister.masterdata`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）とする

## 7. Usability — N/A

Functional Design（frontend-components.md）で対応済み。追加のUsability要件は設けない。

---

## 8. Security Baseline拡張 該当ルール評価

| ルール | 判定 | 本ユニットでの対応方針 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 本ユニットは内部DBに新規永続化なし（Functional Design Q9=A）。対象RDBMS接続情報自体はUNIT-03で暗号化済み |
| SECURITY-02（ネットワーク中継のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（BR-MASTER-12、`MASTER_DATA_BULK_ACCESSED`/`MASTER_DATA_BATCH_APPLIED`）で対応済み |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（入力バリデーション） | 該当・最重要 | SQL手入力の構文検証（NFR-05-08、JSqlParser）、一括反映バッチの件数上限（NFR-05-05） |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | BR-MASTER-14（NONE列の完全非表示）で実装済み |
| SECURITY-07（制限的なネットワーク構成） | N/A | Infrastructure DesignはSKIP判定予定（新規インフラ不要） |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | BR-MASTER-06〜09（作成/更新/削除いずれもUNIT-04の`canCreate`/`canDelete`/実効主権限を都度判定）で対応済み |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | 該当 | JSqlParser（新規依存関係）はUNIT-01で導入済みのOWASP Dependency-Checkプラグインの既存スキャン対象に自動的に含まれる |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | オールオアナッシング（BR-MASTER-07）、フェイルクローズ（NONE列非表示）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | 該当（対応済み） | BR-MASTER-07（制約チェック・オールオアナッシング）で対応済み |
| SECURITY-14（アラート・監視） | N/A | SQL構文検証拒否・一括反映失敗はログベースの検知範囲内とし専用アラートは不要と判断（NFR-05-07） |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-MASTER-04の拒否時400エラーはフェイルセーフの実践例 |

## 9. Property-Based Testing拡張

該当あり。business-logic-model.md §7で識別済みのプロパティ（SQL手入力構文検証の安全性・拒否の健全性、オールオアナッシングの原子性・全件反映、表示対象カラムの非表示の不変条件）を、jqwik（UNIT-02で確定済み）を用いてCode Generation段階で実装する。

# UNIT-09 監査ログ閲覧 - NFR Requirements 計画

## Scalability / Availability

requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜08と同様の判断）。ただし`audit_log_entry`テーブルはUNIT-02〜08の全ユニットにわたりイベントが記録され続け、削除機能を持たないため、`query_execution_record`（UNIT-08で確認）と同様に長期運用でのデータ量増加が本ユニット固有の懸念事項となる（Q3参照）。

## 既存基盤の確認（Functional Designからの追加調査）

NFR Requirements着手にあたり、以下を再確認した。

- `audit_log_entry`テーブルの既存インデックスは`occurred_at`・`event_type`・`user_id`の3本のみで、`connection_id`にはインデックスがない（BR-AUDITVIEW-10で複合インデックス追加を決定済み、Functional Design Q7=A）。
- `AppProperties.Audit.bulkAccessThreshold`（既定100件）は、現状`MasterDataService`のマスタデータ大量アクセス検知（`MASTER_DATA_BULK_ACCESSED`イベント）にのみ使用されている。監査ログ自体の大量閲覧をこの仕組みで検知・記録する実装は存在しない（Q2参照）。
- 絞込条件（発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータス）は任意の組み合わせで指定されるため、UNIT-08と同じくSpring Data JPAの`JpaSpecificationExecutor`（Specification API）による動的クエリ構築がFunctional Design段階で既に確定している（Q3=A）。本ステージでは実装方式そのものは再質問せず、既存決定事項として扱う。

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 本ユニットは新規のDB永続化エンティティを持たない（既存`AuditLogEntry`の閲覧のみ） |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | N/A（対応済み） | UNIT-02〜08で確立済みのログ基盤をそのまま利用 |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | 絞込パラメータ（発生日時範囲の妥当性、ページサイズ上限等）の検証（Q1参照） |
| SECURITY-06（最小権限アクセスポリシー） | 該当・最重要 | 監査ログは管理者専用（BR-AUDITVIEW-03）。UNIT-08（一般ユーザ向けのロールベース絞込）と異なり、エンドポイント全体を`/api/admin/**`で遮断する方式が既にFunctional Design Q2=Aで確定済み。本ステージでは追加質問とせず、対応済みとして評価する |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | 既存のJWT認証＋`SecurityConfig`の`/api/admin/**`ルール（UNIT-02/04で確立済み）をそのまま適用 |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | 記録の不変性（BR-AUDITVIEW-08）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 本ユニットは読み取り専用の閲覧処理のみ（DB更新なし） |
| SECURITY-14（アラート・監視） | 該当（要確認） | 監査ログ自体の大量閲覧・網羅的な閲覧という「監査ログ閲覧の監査」という自己言及的な論点が新規に生じる（Q2参照） |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。絞込パラメータ不正時は新規`AuditLogInvalidParameterException`（UNIT-08の`QueryHistoryInvalidParameterException`と同じパターン）で対応 |

## Property-Based Testing 拡張

STORY-9.1にPBT対象外と明記済み（stories.md）。追加のPBT対象識別は不要（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全5問、推奨どおり全問Aで確定）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 曖昧な回答なし
- [x] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する
- [x] Step D: `tech-stack-decisions.md`（インデックス設計、ページサイズ上限、入力検証方針、名前解決方式、データ量増加への対応方針）を作成する
- [x] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Security Requirements、SECURITY-05）
絞込パラメータの入力検証方針は？

A) UNIT-08と同じ方針を踏襲する: ページサイズの上限検証、発生日時範囲の妥当性（開始≤終了）検証を行う。`eventType`/`resultStatus`はenum値、`userId`/`connectionId`はID値であり、いずれもJPA Criteria APIのパラメータバインドを通るためSQLインジェクションのリスクはなく追加のサニタイズは不要

B) 検証を行わず、クライアント側の入力を信頼する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 2（Security Requirements/Monitoring、SECURITY-14、新規論点）
監査ログの閲覧自体（特に大量閲覧・網羅的な閲覧）を、新たな監査対象として記録しますか？

A) 記録しない。UNIT-02で確立した監査ログ記録方針は「データ変更を伴う操作」または「セキュリティ上重要な操作」が対象であり、既存データの単純な閲覧・絞込（読み取り専用）はいずれのユニットでも監査対象としていない（UNIT-05〜08の閲覧系機能も同様）。管理者専用エンドポイントであり、アクセス自体はアプリケーションログ（SECURITY-03、既存基盤）で追跡可能

B) 新規イベント種別（例: `AUDIT_LOG_VIEWED`）を追加し、`bulkAccessThreshold`超過時に記録する（`MasterDataService`と同じパターン）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 3（Performance/Scalability）
`audit_log_entry`テーブルは削除機能を持たず、UNIT-02〜08の全イベントが記録され続けるため、長期運用でのデータ量増加が懸念される。対応方針は？

A) 現時点でアーカイブ・パーティショニング等の追加機構は導入しない。BR-AUDITVIEW-10の複合インデックス追加（`connection_id`, `occurred_at`）で当面の絞込性能は確保できると判断し、社内10名規模の想定利用量では実用上問題ないと判断する（将来必要になった場合は別ユニットとして検討）

B) 本ユニットでアーカイブ機構（古いレコードの別テーブルへの退避等）を新規に設計する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 4（Performance）
ページサイズの既定値・上限値は？

A) UNIT-08と同じ値を踏襲する（既定50件、上限200件）

B) 異なる値にする

C) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 5（Tech Stack Selection/Performance）
対象ユーザ・対象接続の表示名解決方式は？

A) UNIT-08と同じ方式を踏襲する: 一覧取得のたびに該当IDをユニークに集約し`findAllById`で一括取得する（キャッシュは導入しない。UserおよびRdbmsConnectionは更新される可能性があり、キャッシュ導入によるデータの陳腐化リスクを避ける）

B) UNIT-04/05/06/07と同様のCaffeineキャッシュを新規に導入する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

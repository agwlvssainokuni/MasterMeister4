# UNIT-08 クエリ履歴 - NFR Requirements 計画

## Scalability / Availability

requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜07と同様の判断）。ただし`query_execution_record`テーブルは削除機能を持たず、クエリ実行のたびに増加し続けるため、長期運用でのデータ量増加とインデックス設計は本ユニット固有の懸念事項として扱う（Q2参照）。

## 既存基盤の確認（Functional Designからの追加調査）

NFR Requirements着手にあたり、既存の`query_execution_record`テーブル定義（V16マイグレーション）を再確認した。既存インデックスは`executed_by`・`executed_at`・`saved_query_id`の3本のみで、**`connection_id`列にはインデックスがない**。BR-QUERYHISTORY-02（画面構成は接続選択→履歴一覧の2画面構成）により、本ユニットの主要クエリは常に`connectionId`で絞り込んだうえで`executedAt`降順にソートするパターンとなるため、既存インデックスのままではフルテーブルスキャンになる懸念がある（Q2で確認）。

また、絞込条件（実行日時範囲・実行者スコープ・対象スキーマ・SQLテキスト）は任意の組み合わせで指定されるため、固定的なJPQLクエリでは対応しづらい。動的クエリ構築の実装基盤をQ1で確認する。

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 本ユニットは新規のDB永続化エンティティを持たない（既存`QueryExecutionRecord`の閲覧のみ） |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | N/A（対応済み） | UNIT-02〜07で確立済みのログ基盤をそのまま利用。本ユニットは閲覧のみでデータ変更を伴わないため追加の監査ログ記録は不要 |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | 絞込パラメータ（実行日時範囲の妥当性、`executedByScope`の値検証、ページサイズ上限、SQLキーワードの長さ制限等）の検証（Q3参照） |
| SECURITY-06（最小権限アクセスポリシー） | 該当・最重要 | 実行者スコープ「全ユーザ」は管理者のみ許可、一般ユーザはフェイルクローズで「自分のみ」に強制（BR-QUERYHISTORY-03）。実装レイヤーをQ4で確認 |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | 既存のJWT認証、`/api/query-history/**`はBearer認証必須（ロール不問、一般ユーザ向け機能） |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | 記録の不変性（BR-QUERYHISTORY-04）、実行者スコープのフェイルクローズ（BR-QUERYHISTORY-03）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 本ユニットは読み取り専用の閲覧処理のみ（DB更新なし） |
| SECURITY-14（アラート・監視） | N/A | 長時間実行等の懸念はUNIT-06の実行時点で対応済み。本ユニットは記録済みデータの閲覧のみ |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。既存の例外体系で十分（本ユニット固有の新規例外は現時点で想定していない） |

## Property-Based Testing 拡張

STORY-8.1・8.2ともにPBT対象外と明記済み（stories.md）。追加のPBT対象識別は不要（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する
- [ ] Step D: `tech-stack-decisions.md`（動的クエリ実装方式、インデックス設計、ページサイズ上限、権限判定レイヤー、名前解決方式）を作成する
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Tech Stack Selection、重要）
絞込条件（実行日時範囲・実行者スコープ・対象スキーマ・SQLテキスト、いずれも任意指定）を動的に組み合わせるクエリの実装方式は？

A) Spring Data JPAの`JpaSpecificationExecutor`（Specification API）を`QueryExecutionRecordRepository`に導入し、指定された条件のみを動的にAND結合する。プロジェクト内初導入だが、任意の条件組み合わせに対する最も自然な標準的手法

B) `@Query`（JPQL）で固定クエリを書き、未指定条件は`:param IS NULL OR column = :param`パターンで無効化する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 2（Performance/Scalability、重要）
`query_execution_record`テーブルの既存インデックスは`executed_by`・`executed_at`・`saved_query_id`のみで、本ユニットの主要クエリが常に指定する`connection_id`にはインデックスがない。対応方針は？

A) 新規マイグレーションで`(connection_id, executed_at)`の複合インデックスを追加する（接続指定＋日時降順ソートという主要アクセスパターンに最適化）

B) 既存インデックスのみで運用する（想定データ量・利用者数（社内10名規模）では実用上問題ないと判断し、追加インデックスは見送る）

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 3（Security Requirements、SECURITY-05）
絞込パラメータの入力検証方針は？

A) Bean Validationおよびパラメータバリデーションで、ページサイズの上限、日時範囲の妥当性（開始≤終了）、`executedByScope`の値域を検証する。SQLキーワード自体はLIKE検索の値としてそのままバインドされるため追加のサニタイズは不要（`NamedParameterJdbcTemplate`相当のバインド機構、またはJPA Criteria APIのパラメータバインドによりSQLインジェクションのリスクはない）

B) 検証は行わず、クライアント側の入力を信頼する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 4（Security Requirements、SECURITY-06・最重要）
実行者スコープ（「全ユーザ」は管理者限定、BR-QUERYHISTORY-03）の権限判定はどのレイヤーで実施しますか？

A) Controller層でJWTの`role`クレームを判定し、一般ユーザが`ALL`スコープを指定した場合はService呼び出し前に`MINE`へ強制する（UNIT-05/06で確立したロール判定パターンを踏襲）

B) Service層で判定する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 5（Performance/Tech Stack Selection）
実行者表示名（`User.fullName`）・保存クエリ名（`SavedQuery.name`）の解決方式は？

A) 一覧取得のたびに、該当するIDをユニークに集約して`findAllById`で一括取得する（キャッシュは導入しない。User/SavedQueryは更新される可能性があり、キャッシュ導入によるデータの陳腐化リスクを避ける。想定データ量でも1リクエストあたりの追加クエリは軽微）

B) UNIT-04/05/06/07と同様のCaffeineキャッシュを新規に導入する

C) Other (please describe after [Answer]: tag below)

[Answer]: 

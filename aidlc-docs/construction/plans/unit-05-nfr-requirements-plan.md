# UNIT-05 マスタメンテナンス - NFR Requirements 計画

## Scalability / Availability
requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜UNIT-04と同様の判断）。ただし本ユニットは対象RDBMS側の**任意規模のマスタデータ**を直接扱うため、レコード件数・ページング・バッチサイズの設計上の前提規模はQ7・Q4で個別に確認する。

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 本ユニットは内部DBに新規永続化なし（Q9=A、functional-design）。対象RDBMSへの接続情報自体はUNIT-03で暗号化済み |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（BR-MASTER-12、`MASTER_DATA_BULK_ACCESSED`/`MASTER_DATA_BATCH_APPLIED`）で対応済み |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | SQL手入力（WHERE/ORDER BY）の構文検証方式（BR-MASTER-04）の具体的な実装手段をQ1で確認。NFR-4.3が「マスタメンテナンスの動的SQL生成部分は特に重点対応」と明記 |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | BR-MASTER-14（NONE列の完全非表示）で実装済み |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | BR-MASTER-06〜09（作成/更新/削除いずれもUNIT-04の`canCreate`/`canDelete`/実効主権限を都度判定）で対応済み |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | 該当 | SQL構文検証に使うライブラリ（Q1）の依存関係追加について評価 |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | オールオアナッシング（BR-MASTER-07）、フェイルクローズ（NONE列非表示）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | 該当（対応済み） | BR-MASTER-07（制約チェック・オールオアナッシング）で対応済み |
| SECURITY-14（アラート・監視） | 該当 | SQL構文検証拒否の多発、バッチ失敗多発等に対する監視要否をQ6で確認 |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-MASTER-04の拒否時400エラーはフェイルセーフの実践例 |

## Property-Based Testing 拡張
business-logic-model.md §7でテスト可能プロパティを識別済み（SQL手入力の構文検証、オールオアナッシング、表示対象カラムの絞り込み）。フレームワークはUNIT-02でjqwikに確定済みのため、本ステージでの追加決定は不要（N/A）。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全8問、AI推奨どおり全問Aで確定 2026-07-24T10:00:00Z）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 回答に曖昧性なし。Q3（毎回正確なCOUNT）とQ7（中規模テーブル前提）の組み合わせは許容されるトレードオフとして両方の選択肢文言に明記済み
- [x] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する
- [x] Step D: `tech-stack-decisions.md`（SQL構文検証ライブラリ、動的レコードアクセス方式、監査ログ閾値設定方式等）を作成する
- [x] Step E: 完了メッセージを提示し、承認を得る（レビューで一括反映のトランザクション制御方式の欠陥を修正のうえ承認 2026-07-24T10:20:00Z）

## 質問

### Question 1（Tech Stack Selection・Security Requirements、BR-MASTER-04、最重要）
STORY-4.2のWHERE句・ORDER BY句手入力を安全に構文解析・検証する具体的な実装手段は？

A) JSqlParser（Apache License 2.0のJava製SQLパーサライブラリ）を新規依存関係として追加する。WHERE/ORDER BY句を構文木として解析し、Visitorパターンで許可された構文要素（比較演算子・AND/OR・カラム参照・リテラル値等）のみで構成されているか検証する。SQL方言・構文の広いカバレッジがあり、自前実装より安全性が高い

B) 外部ライブラリを使わず、本ユニット専用の簡易な再帰下降パーサ（許可構文のみを対象にした最小限のトークナイザ＋パーサ）を自前実装する。依存関係は増えないが、実装・テストの工数とレビュー負荷が大きい

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 2（Tech Stack Selection・Performance）
対象RDBMSに対する動的なレコード取得・更新・削除（テーブル構造が実行時まで不明）の実装方式は？

A) 既存の`RdbmsConnectionService.getDataSource(connectionId)`（HikariCPでキャッシュ済みのDataSource、UNIT-03既存）を流用し、Spring JdbcTemplate/NamedParameterJdbcTemplateで動的SQLを実行する（バインドパラメータで値を渡す。JPAエンティティは使わない、テーブル構造が実行時まで不明なため）

B) `java.sql.DriverManager`で都度接続を確立する（UNIT-03の`SchemaIntrospectionService`と同じ方式。ただし接続の都度確立はコネクションプールの利点を活かせない）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3（Performance、BR-MASTER-10）
レコード一覧のページング（オフセットベース）における総件数取得（COUNT）は、テーブルサイズに関わらず毎回正確に実行しますか？

A) 毎回正確なCOUNTクエリを実行する（実装がシンプル。同時利用者約10名規模の社内ツールという前提では、大規模テーブルでの都度COUNTのコストは許容範囲と判断）

B) COUNTクエリを省略し、「次ページが存在するかどうか」のみをLIMIT+1件取得等で判定する（総件数は表示しない）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4（Scalability、BR-MASTER-06〜07）
一括反映バッチ（1リクエストあたりの作成・更新・削除の合計件数）に上限を設けますか？

A) 上限を設ける（例: 1バッチあたり1,000件）。一般ユーザ向け画面からの操作である以上、無制限のバッチサイズはメモリ・トランザクション時間の観点でリスクがあるため、超過時は400エラーで拒否する

B) 上限を設けない（フロントエンドの表示件数（1ページあたり件数）に自然に制約されるため、別途の上限は不要と判断する）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 5（Tech Stack Selection、BR-MASTER-12）
監査ログの「大量データ取得」閾値（デフォルト100件、requirements.md §6.1）の設定方式は？

A) `application.yml`にアプリケーション全体の設定値（例: `mm.app.audit.bulk-access-threshold: 100`）として持たせ、`AppProperties`経由で参照する（UNIT-02/03で確立した設定値管理方式と一貫する）

B) ハードコードする（設定変更には再ビルドが必要）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6（Security Requirements・Reliability、SECURITY-14）
SQL手入力の構文検証拒否（BR-MASTER-04）が多発した場合や、一括反映の失敗（BR-MASTER-07）が多発した場合に対する専用のアラート機構は設けますか？

A) 設けない。UNIT-02のログベースの検知（NFR-4.5、社内ツール規模を踏まえた軽量な仕組み）の対象範囲内とし、本ユニット固有の追加監視は行わない

B) 専用のアラート機構（例: 短時間内の拒否回数閾値超過での通知）を新設する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7（Scalability、前提確認）
対象RDBMSの1テーブルあたりのレコード件数・カラム数について、設計上の前提規模は？

A) 中規模（1テーブルあたり数万〜数十万件、カラム数は数十程度までを主な想定とする。社内マスタデータの典型的な規模）

B) 小規模（1テーブルあたり数千件程度まで）のみを想定する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 8（Reliability、BR-MASTER-07）
一括反映のトランザクション制御において、対象RDBMSへの接続が処理途中で切断された場合の扱いは？

A) JDBCトランザクションの自動ロールバックに委ねる（コミット前の切断は自動的に未反映となる）。UNIT-03のような明示的なタイムアウト制御（`CompletableFuture.orTimeout`）は導入せず、JDBCドライバのデフォルトのタイムアウト・例外処理に従う

B) UNIT-03と同様、明示的なタイムアウト制御を導入し、長時間応答がない場合は強制的に処理を中断する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

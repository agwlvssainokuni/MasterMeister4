# UNIT-06 クエリ保存・実行 - Functional Design 計画

## ユニットスコープ（前提確認）

対応要件: FR-6.1〜FR-6.6（保存クエリのCRUD・公開範囲・実行・編集権限・非表示化）、FR-7.1〜FR-7.9（SQL実行・パラメータ対応・実行時スキーマ指定・ページング・履歴記録・結果表示）
対応コンポーネント: COMP-14 QueryExecutionService、COMP-15 SavedQueryService
対応ストーリー: STORY-6.1, STORY-6.2, STORY-7.1, STORY-7.2, STORY-7.3
前提ユニット: UNIT-01〜UNIT-04（`RdbmsDialectStrategy`によるDB方言吸収、`EffectivePermissionResolver`による実効権限判定に依存）

既存の決定事項（requirements.md、UNIT-01〜05実装済み）:
- 対応RDBMS: MySQL / MariaDB / PostgreSQL / H2（`RdbmsDialectStrategy`既存、方言別JDBC URL構築・識別子クォート等を提供）
- `EffectivePermissionResolver`はテーブル/カラム単位の実効権限判定APIを提供（`resolvePrimary(userId, connectionId, schemaName, tableName, columnName)`等）。スキーマ単位・接続単位の集約判定は現時点で存在せず、UNIT-05の`MasterDataService.listAccessibleConnections/listAccessibleTables`が独自に集約ロジックを実装した前例がある
- UNIT-05でJSqlParser 5.3を用いたSQL構文検証の実装パターンが確立済み（WHERE/ORDER BY句のダミーSELECT埋め込みによる構文解析、パターンマッチングswitchによるASTノード種別の許可リスト検証）
- 実効権限判定はAPI非公開、同一プロセス内のJava直接呼び出しのみ
- UNIT-08（クエリ履歴）はUNIT-06・UNIT-07に依存する後続ユニットであり、本ユニット時点ではまだ存在しない（COMP-17 QueryHistoryServiceは未実装）

## 計画チェックリスト

- [ ] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `business-logic-model.md`（SQL実行フロー、保存クエリのライフサイクル、PBT対象プロパティの識別）を作成する
- [ ] Step D: `domain-entities.md`（保存クエリ・実行記録の新規永続化エンティティ定義）を作成する
- [ ] Step E: `business-rules.md`（公開範囲・編集権限・実行時スキーマ検証・読み取り専用検証等の詳細規則）を作成する
- [ ] Step F: `frontend-components.md`（クエリ実行画面・保存クエリ管理画面のコンポーネント構造）を作成する
- [ ] Step G: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Integration Points・重要な設計上の注意点）
FR-7.7（履歴記録）は、履歴の**閲覧**機能自体はUNIT-08（クエリ履歴、本ユニットの後続）で実装されます。本ユニット（UNIT-06）の時点で、実行記録の永続化はどう扱いますか？

A) UNIT-02の監査ログ基盤（記録はUNIT-02で先行構築、閲覧はUNIT-09で後付け）と同じ考え方で、本ユニットで実行記録用の新規エンティティ（SQL・パラメータ・対象スキーマ・結果件数・実行時間・実行日時・実行者等）を永続化する処理まで実装する。閲覧・絞込機能（UI・API）はUNIT-08で追加する
B) 本ユニットでは実行記録の永続化を行わず、UNIT-08着手時にまとめて実装する（本ユニットのSQL実行機能は記録なしで完結する）
C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 2（Business Rules・セキュリティ上重要）
FR-7.5「実行時スキーマ指定（許可リスト方式で検証）」について、生SQL実行時のアクセス制御範囲はどこまでとしますか？UNIT-05（マスタメンテナンス）はテーブル/カラム単位の実効権限で表示・編集を制御しましたが、本ユニットは任意のSELECT文（JOIN句等を含みうる）を直接実行する機能です。

A) スキーマ単位のみで制御する。ユーザがそのスキーマ内のいずれかのテーブル/カラムに対して`READ`以上の権限を1つでも持っていれば、そのスキーマを実行対象として選択可能とし、選択後はスキーマ内の任意のテーブル/カラムを参照するSQLを実行できる（テーブル/カラム単位の実効権限はスキーマ選択可否の判定にのみ使い、個々のSELECT対象テーブル/カラムへの実行時アクセス制御は行わない）。任意のSELECT文に対してテーブル/カラム単位のアクセス制御を課すことは、JOIN・サブクエリ等を含む一般的なSQLの静的解析が必要となり技術的に大きな負担となるため
B) UNIT-05と同様にテーブル/カラム単位の実効権限も適用する（SELECT対象の全テーブル・カラムについて`READ`以上を要求し、含まれない場合は拒否する）
C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 3（Business Rules）
保存クエリの公開範囲（Public/Private）について、アクセス制御の詳細と管理者の扱いは？

A) Private＝作成者のみ参照・実行・編集・非表示化が可能。Public＝認証済みユーザは誰でも参照・実行可能だが、編集・非表示化は作成者のみ（FR-6.5どおり）。管理者による他ユーザの保存クエリへの特別な閲覧・操作権限は、本ユニット時点では設けない
B) 管理者は公開範囲によらず全ての保存クエリを参照・非表示化できる（監査・運用上のオーバーライド権限を持つ）
C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 4（Data Flow）
FR-6.3「保存クエリはスキーマを保持しない」とあり、実行時にスキーマを指定します。実行対象スキーマの許可リスト判定（Question 2の回答に基づく）は、いつ評価しますか？

A) 保存クエリの保存時点では評価しない。実行するたびに、実行時点でのその実行者自身のアクセス可能スキーマ一覧を都度評価する（作成者と実行者が異なりうるPublicクエリでは、実行者ごとに選択可能なスキーマが変わりうる。また作成者の権限がその後失われても、Publicクエリ自体は影響を受けない）
B) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 5（Domain Model・Data Flow）
パラメータ（`:param`形式、FR-7.3〜7.4）の扱いは？

A) 保存クエリにパラメータのメタデータ（名前・型・デフォルト値等）を別途保存せず、SQL文字列から`:param`形式のトークンをその都度自動検出する（ad-hoc実行・保存クエリ実行のいずれも同じ検出ロジックを都度実行時に適用）。値の型はすべて文字列として受け取り、`NamedParameterJdbcTemplate`へのバインド時にDBのカラム型に応じて解決する（UNIT-05のColumnDataTypeMapper的な型解決は、SELECT結果の対象カラムが動的なため本ユニットでは行わず、文字列としてそのままバインドする）
B) 保存クエリの保存時にパラメータのメタデータ（名前・型等）を別途保存する
C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 6（Business Rules・セキュリティ上重要）
FR-7.2「読み取り専用SQLのみ実行可能」の検証方式は？UNIT-05はWHERE/ORDER BY句という「SQLの一部」の構文検証でしたが、本ユニットは「SQL全体」を検証する必要があります。

A) JSqlParserでSQL全体を構文解析し、パース結果が単一の`Select`文であることのみを検証する（`Insert`/`Update`/`Delete`/DDL/複数文/パース不能な入力は拒否）。UNIT-05のような個々の式レベルでの許可リスト検証（比較演算子・カラム参照等の細かい制限）は行わない。SELECT文である以上、JOIN・サブクエリ・集約関数等は許可する（読み取り専用であることさえ保証できればよいため）
B) Other（より厳しい制限が必要、[Answer]: の後に内容を記述）

[Answer]:

### Question 7（Frontend Components）
画面構成は？

A) 2画面構成: 「クエリ実行」画面（SQL入力・編集、実行時スキーマ選択、パラメータ入力フォーム、ページング付き結果表、この場で名前を付けて保存するアクションも提供）と「保存クエリ一覧」画面（Public/Private・作成者等での絞込、実行画面への遷移、作成者による編集・非表示化操作）。保存クエリを開いて実行する場合、FR-7.9によりSQLは編集不可として表示する
B) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 8（Business Rules）
保存クエリの編集（FR-6.5、作成者のみ）で編集可能な項目は？

A) SQL・名前・公開範囲（Public/Private）のすべてを作成者が編集できる
B) SQLのみ編集可能。名前・公開範囲は保存後変更不可
C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 9（Business Scenarios）
非表示化（retire、FR-6.6）後のアクセスは？

A) 非表示化すると、作成者を含む全ユーザから一覧・実行の対象外となる（論理データは保持するが、UIからは完全に見えなくなる。復元・非表示クエリの閲覧機能は本ユニットでは提供しない）
B) 作成者は絞込フィルタ等で自分の非表示化済みクエリを引き続き参照できる
C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 10（Business Scenarios）
作成者アカウントが無効化（UNIT-02のDISABLED状態）された場合、そのユーザが作成したPublic保存クエリはどう扱いますか？

A) 特別な処理は行わない。Publicクエリは作成者のアカウント状態に関わらず他ユーザから引き続き参照・実行可能（作成者無効化時の自動非表示化等は行わない）。Privateクエリは元々作成者のみアクセス可能なため、作成者がログインできなくなれば事実上アクセス不能になるが、データ自体は残る
B) Other（[Answer]: の後に内容を記述）

[Answer]:

# UNIT-07 クエリビルダー - NFR Requirements 計画

## Scalability / Availability
requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、アプリケーション自体の大規模スケーラビリティ要件はN/A（UNIT-01〜UNIT-06と同様の判断）。`generateSql`はDBアクセスを伴わない純粋な変換であり、`parseToBuilderState`・アクセス可能テーブル/カラム一覧取得はUNIT-03/04の既存キャッシュ済みインフラを利用するため、新規のスケーラビリティ懸念はない。

## 既存基盤の確認（Functional Designからの追加調査）
NFR Requirements着手にあたり、関連する既存実装を確認した:
- UNIT-05の`ColumnDataTypeMapper`は、UNIT-03の`SchemaColumn.normalizedType`（既にJDBC型情報を正規化した`NormalizedType`）から`ColumnDataTypeCategory`へのマッピングのみを行う軽量なコンポーネント。本ユニットも同じ設計思想（BR-QUERYBUILDER-05）を独自クラスとして踏襲できるかをQ4で確認する
- UNIT-05の`RecordQueryService`は、フィルタ条件の値を`NamedParameterJdbcTemplate`の`MapSqlParameterSource`経由でバインドする（実行時パラメータ化）。本ユニットはSQL**文字列自体**を生成する必要があり（FR-5.5、生成結果はUNIT-06のSQL文字列としてそのまま保存・実行される）、UNIT-05と同じ実行時バインド方式は使えない。WHERE/HAVING条件の比較値をどう安全にSQL文字列へ埋め込むかは、Functional Design時点で「リテラル埋め込み」という前提を置いたが、その安全な実装方式（およびUNIT-06の`:param`プレースホルダ方式との使い分け）を本ステージでQ1・Q2で確認する
- UNIT-05/06はいずれもJSqlParserを構文解析（検証・パラメータ検出）に用いるが、SQL文の**構築**（AST組み立てからの文字列化）は前例がない。本ユニットのSQL生成アルゴリズム（§6）の実装基盤をQ3で確認する

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A | 本ユニットは新規のDB永続化エンティティを持たない（BR-QUERYBUILDER-08） |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | スコープ外 |
| SECURITY-03（アプリケーションログ） | N/A（対応済み） | UNIT-02〜06で確立済みのログ基盤をそのまま利用。本ユニット固有の追加ログ要件なし（生成・解析はDB更新を伴わないためAuditEventPublisher記録の対象外） |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（全APIパラメータの入力検証） | 該当・最重要 | `QueryBuilderState`リクエストの妥当性検証（Bean Validation）、各リストの件数上限（Q5）、WHERE/HAVING比較値の安全なSQL埋め込み（Q1・Q2、SQLインジェクション防止の観点で特に重要）を確認 |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | BR-QUERYBUILDER-01（列単位の実効権限フィルタリング）で対応済み |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベル |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み） | 既存のJWT認証・`/api/query-builder/**`はBearer認証必須（ロール不問、UNIT-05/06と同様の一般ユーザ向け機能）。新規のSecurityFilterChainルール追加が必要かはCode Generationで確認 |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | N/A | 新規外部ライブラリ依存なし（JSqlParserはUNIT-05で追加済みを再利用） |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | リバースエンジニアリング失敗時のフェイルクローズ（BR-QUERYBUILDER-07）、列単位フィルタリング（BR-QUERYBUILDER-01）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規要件なし |
| SECURITY-13（データ整合性検証） | N/A | 本ユニットは読み取り専用の変換処理のみ（DB更新なし） |
| SECURITY-14（アラート・監視） | N/A | 長時間実行・大量データ取得等の懸念はUNIT-06の実行時点で既に対応済み。本ユニットはSQL生成のみで実行を伴わない |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-QUERYBUILDER-07・BR-QUERYBUILDER-11の検証拒否時エラー応答はフェイルセーフの実践例 |

## Property-Based Testing 拡張
business-logic-model.md §8でテスト可能プロパティを識別済み（SQL生成/リバースエンジニアリングのラウンドトリップ、GROUP BY整合性の不変条件、アクセス可能テーブル/カラム一覧のREAD以上不変条件）。フレームワークはUNIT-02でjqwikに確定済みのため、本ステージでの追加決定は不要（N/A）。ラウンドトリップPBTのジェネレータ設計（タブUIで表現可能な`QueryBuilderState`の生成方法）は、Code Generation計画時に詳細化する。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する
- [ ] Step D: `tech-stack-decisions.md`（SQL生成/解析の実装基盤、値の安全な埋め込み方式、型分類マッピング、リクエストサイズ上限、キャッシュ戦略）を作成する
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Tech Stack Selection・Functional設計への影響、重要）
WHERE/HAVINGタブでの比較値を、生成するSQL文字列にどう反映しますか？（Functional Design時点では「リテラル埋め込み」を前提としていたが、改めて確認する）

A) リテラル値として直接SQLに埋め込む（例: `WHERE t1.age > 30`）。生成のたびに固定値のSQLとなり、シンプル。UNIT-06の`:param`プレースホルダ機構とは独立して動作する

B) UNIT-06の`:param`プレースホルダ形式で生成する（例: `WHERE t1.age > :where_1`）。生成されるSQLが値非依存で再利用可能になるが、値自体をどう画面間で引き継ぐか（ビルダー画面入力値→実行/保存画面のパラメータ入力欄への自動転記）の追加設計・実装が必要になる

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 2（Security Requirements、SECURITY-05・最重要）
Q1でAを選んだ場合、比較値をSQL文字列へ安全に埋め込む（SQLインジェクションを防ぐ）実装方式は？

A) JSqlParserのExpression構築API（`StringValue`/`LongValue`/`DoubleValue`等のリテラルクラス）を使い、列のデータ型分類（Q4）に応じた型安全なリテラルオブジェクトとして構築し、`.toString()`で文字列化する。文字列連結によるエスケープ漏れを構造的に防止する

B) 手動での文字列エスケープ処理（クォート文字の置換等）を実装する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 3（Tech Stack Selection）
SQL生成（`generateSql`）およびリバースエンジニアリング（`parseToBuilderState`）の実装基盤は？

A) JSqlParserのオブジェクトモデル（`Select`/`Table`/`Join`/`BinaryExpression`等）を構築し`.toString()`で文字列化する（生成）。解析はUNIT-05/06で確立したパターン（`CCJSqlParserUtil.parseStatements`＋ASTのVisitorパターン走査）を踏襲する

B) 生成は独自の文字列テンプレート組み立て（解析のみJSqlParserを使用）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 4（Tech Stack Selection）
`ColumnDataTypeCategory`（演算子絞り込みに使う型分類）のマッピング元は？

A) UNIT-05の`ColumnDataTypeMapper`と同じ設計思想（UNIT-03の`SchemaColumn.normalizedType`からのマッピング）を、`cherry.mastermeister.querybuilder`パッケージ内に独自クラスとして再実装する（UNIT-05への依存はしない、BR-QUERYBUILDER-05どおり）

B) 異なるマッピング方式を採用する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 5（Security Requirements・Reliability）
`QueryBuilderState`リクエスト（SQL生成API・アクセス可能テーブル/カラム一覧取得後にユーザが構築する状態）の各リスト項目数に上限を設けますか？

A) 上限を設ける（例: SELECT項目・WHERE条件・HAVING条件・ORDER BY項目は各50件、JOINは20件までとし、Bean Validationの`@Size`で強制する）。過大なリクエストによるリソース消費・生成SQLの肥大化を防ぐ

B) 上限を設けない（社内ツール規模のため実質的なリスクは低いと判断する）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 6（Performance・Tech Stack Selection）
アクセス可能テーブル/カラム一覧取得（`GET /api/query-builder/{connectionId}/tables`）のキャッシュ戦略は？

A) 既存の`EffectivePermissionResolver`のCaffeineキャッシュ（`effectivePermission`）にそのまま任せる。テーブル/カラムごとの`resolvePrimary`呼び出しはキャッシュヒットする前提のため、新規の専用キャッシュは追加しない（UNIT-05の`listAccessibleTables`と同じ方式）

B) 一覧結果自体（テーブル/カラム一覧全体）を新たに専用キャッシュする

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

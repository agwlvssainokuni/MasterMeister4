# UNIT-03 RDBMSセットアップ - Functional Design 計画

## ユニットスコープ（前提確認）

対応要件: FR-2.1（接続情報の登録・管理）, FR-2.2（スキーマ取込）
対応コンポーネント: COMP-07 RdbmsConnectionService, COMP-08 SchemaIntrospectionService, COMP-09 RdbmsDialectStrategy（Strategy/Adapterパターン、実装群: MySqlDialectStrategy/MariaDbDialectStrategy/PostgresDialectStrategy/H2DialectStrategy）
対応ストーリー: STORY-2.1, STORY-2.2

既存の決定事項（requirements.md §4）: 接続パスワードは可逆暗号化して内部DBに保存する（ログインパスワードのハッシュ化とは異なり、接続時に再利用が必要なため）。暗号化アルゴリズム・鍵管理の詳細技術選定はNFR Requirements/NFR Designで扱う。

## 計画チェックリスト

- [ ] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `business-logic-model.md`（接続登録・スキーマ取込のフロー、方言吸収の適用ポイント）を作成する
- [ ] Step D: `domain-entities.md`（RdbmsConnection、SchemaSnapshot/Table/Column/Constraint等）を作成する
- [ ] Step E: `business-rules.md`（バリデーション、重複・削除・再取込時の方針）を作成する
- [ ] Step F: フロントエンドスコープが「含める」の場合、`frontend-components.md`を作成する
- [ ] Step G: PBT対象プロパティの識別（Property-Based Testing拡張、該当あれば記載）
- [ ] Step H: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Business Logic Modeling）
接続登録時の接続確認（Test Connection）をどう扱いますか？

A) 登録操作時に自動で接続確認を行い、失敗時は登録自体を拒否する

B) 登録とは別に、独立した「接続テスト」操作を用意し、任意実行できるようにする（登録自体は形式チェックのみで通す）

C) 接続確認は行わない（形式チェックのみ。実際に使えるかはスキーマ取込操作時に初めて判明する）

D) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 2（Business Logic Modeling）
スキーマ再取込（`refreshSchema`）時、既存のスキーマ情報への反映方式は？

A) 全置換（既存のスキーマスナップショットを削除し、取込内容で丸ごと置き換える。FR-2.11のYAML importと同じ全置換方針に揃える）

B) 差分マージ（RDBMS側で削除されたテーブルも内部DBには残し、追加・変更分のみ反映する）

C) バージョン管理（旧スナップショットを履歴として保持し、いつでも参照できるようにする）

D) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 3（Domain Model）
スキーマ取込で保持する「制約」情報の範囲は？（FR-2.2は「物理名、コメント、型、制約」とのみ規定）

A) 主キーのみ（FR-2.6/2.7のレコード作成・削除可否判定に主キー情報が必須なため、最低限の範囲）

B) 主キー・外部キー・NOT NULL・一意制約の4種

C) Bに加え、デフォルト値・インデックス情報も含める

D) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 4（Domain Model）
対象RDBMSの型情報（例: `VARCHAR(255)`, `INT`, `TEXT`）はどう保持しますか？

A) DB固有の型名をそのまま文字列で保持する（表示用途中心。方言ごとの表記差異はそのまま残る）

B) DB固有の型名を保持しつつ、あわせて内部共通型（STRING/NUMBER/DATE/BOOLEAN等）へも正規化して保持する（後続ユニットでの型に基づく処理を見据える）

C) 内部共通型へ完全に正規化し、元の型名は保持しない

D) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 5（Business Rules）
同一の物理RDBMSに対して複数の接続情報を登録することを許可しますか？（host/port/DB名が重複するケース）

A) 許可する。接続情報は独立したIDで管理し、重複チェックは行わない（STORY-2.1の「既存の接続に影響なく独立して管理される」という受け入れ基準どおり）

B) host+port+DB名の組み合わせで重複登録を禁止する

C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 6（Business Rules・Business Scenarios）
接続情報の削除機能について、本ユニットでの扱いは？（UNIT-04のアクセス権限やUNIT-05以降のマスタメンテナンスは接続IDを参照するが、それらはまだ存在しない時点での判断）

A) 本ユニットでは削除機能を実装しない（登録・更新・スキーマ取込のみ。削除は参照整合性が絡むためUNIT-04以降、実際に参照が発生する時点であらためて設計する）

B) 削除機能を実装し、参照有無に関わらず無条件でカスケード削除する

C) 削除機能を実装するが、参照チェック自体は本ユニット時点では常に「参照なし」として扱い、実際のチェックロジックはUNIT-04以降で追加する

D) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 7（Error Handling）
対象RDBMSへの接続失敗（ネットワーク不通・認証エラー・タイムアウト等）時のエラーメッセージ方針は？

A) 種別を区別して具体的なメッセージを返す（接続不可／認証エラー／タイムアウト等）

B) 詳細を露出させず、一般的な「接続に失敗しました」という共通メッセージのみ返す（Security Baseline的にはこちらが無難）

C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 8（Error Handling）
スキーマ取込処理の途中で一部テーブルの読取に失敗した場合の挙動は？

A) 全体を失敗として扱う（オールオアナッシング。FR-2.12のYAML import拒否方針と揃える）

B) 読み取れた範囲のみ部分的に取込み、失敗したテーブルは警告として記録する

C) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 9（Frontend Components・スコープ確認）
unit-of-work.mdのUNIT-03「対応コンポーネント」欄はCOMP-07〜09（バックエンドのみ）であり、フロントエンドモジュール一覧（`registration/`, `auth/`, `access-control/`, `master-data/`, `query-builder/`, `query-save/`, `query-execution/`, `query-history/`, `audit-log/`）にもRDBMS接続管理専用のモジュールが見当たりません。一方でSTORY-2.1/2.2の受け入れ基準は「管理者ダッシュボードで操作する」ことを前提としています。本ユニットのスコープにフロントエンド画面を含めますか？

A) 含める。新規フロントエンドモジュール`rdbms-connection/`を本ユニットで新設する（接続一覧・登録フォーム・スキーマ取込操作の最小限画面。UIなしではユニット単体の動作確認が困難なため推奨）

B) 含めない。本ユニットはバックエンドAPIのみとし、UI実装はUNIT-04（アクセス制御、管理者ダッシュボードの本格拡張）とあわせてまとめて行う。本ユニットの動作確認はAPI呼び出し（curl等）で行う

C) Other（[Answer]: の後に内容を記述）

[Answer]:

# UNIT-03 RDBMSセットアップ - Business Logic Model

`unit-03-functional-design-plan.md`の回答（Q1=B, Q2=A, Q3=C, Q4=B, Q5=A, Q6=B, Q7=A, Q8=A, Q9=A）に基づく。技術非依存を原則とし、暗号化アルゴリズム・鍵管理・JDBCドライバ選定等の技術選定はNFR Requirements/NFR Designで扱う。

対応コンポーネント: COMP-07（RdbmsConnectionService）, COMP-08（SchemaIntrospectionService）, COMP-09（RdbmsDialectStrategy）

---

## 1. 接続情報の登録・更新（FR-2.1）

1. 管理者が接続情報（表示名、DB種別`dbType`、host、port、databaseName、~~schemaName（任意）、~~username、password、additionalParams（任意、JDBC URLに付加するクエリパラメータ。BR-RDBMS-10）を入力し登録する（`POST /api/admin/rdbms-connections`）。訂正（UNIT-04 Functional Designにて）: `schemaName`は登録項目から削除。1接続内に複数スキーマが存在しうる前提に変更したため、スキーマ一覧はスキーマ取込時に自動検出する（§3参照）
2. RdbmsConnectionServiceは形式チェックのみ行う（必須項目の入力有無、portが1〜65535の整数であること等）。実際にRDBMSへ接続できるかどうかの確認はここでは行わない（Q1=B、BR-RDBMS-01）
3. パスワードは可逆暗号化して保存する（requirements.md §4の既存決定。アルゴリズム・鍵管理はNFR Requirements/NFR Designで確定）
4. 独立した接続IDを新規発行する。host/port/databaseNameの組み合わせが既存の接続と重複していても登録を拒否しない（Q5=A、BR-RDBMS-02）
5. AuditEventPublisher経由で`CONNECTION_REGISTERED`イベントを発行する（`connectionId`に紐付け）
6. 更新（`PUT /api/admin/rdbms-connections/{id}`）も同様に形式チェックのみとする。更新後は`CONNECTION_UPDATED`イベントを発行する
7. 接続先自体を変更する更新（host/port/databaseName等の変更）を行っても、既存のスキーマスナップショットは自動的に無効化・再取込されない（BR-RDBMS-03）。最新化するには管理者が改めてスキーマ更新操作（§3）を実行する必要がある
8. 一覧取得・詳細取得のいずれのAPIレスポンスにも、暗号化パスワード・復号済みパスワードのいずれも含めない（BR-RDBMS-12、レビュー指摘の反映）。更新時にパスワード欄が空欄で送信された場合は「変更しない」ことを意味し、既存の暗号化パスワードをそのまま保持する

---

## 2. 接続テスト（FR-2.1関連、Q1=B）

接続テストは、保存済みの接続情報に対してだけでなく、登録・編集フォーム入力中の未保存の値に対しても実行できる（レビュー指摘の反映、BR-RDBMS-11）。

1. **保存済み接続に対するテスト**: 管理者は登録済み接続情報に対し、登録・更新とは独立した「接続テスト」操作（`POST /api/admin/rdbms-connections/{id}/test`）を任意のタイミングで実行できる
2. **未保存の入力値に対するテスト**: 管理者は登録・編集フォーム入力中の値に対しても、専用エンドポイント（`POST /api/admin/rdbms-connections/test`、接続情報一式をリクエストボディに含める）で同様にテストできる。サーバ側は永続化を行わずに疎通確認のみを行う（BR-RDBMS-11）
3. いずれの場合も、RdbmsConnectionServiceは対象の接続情報で実際にDataSourceへの疎通を試み、成功／失敗を判定する
4. 失敗時は、接続不可／認証エラー／タイムアウト／その他、のいずれかに分類したエラーを返す（Q7=A、BR-RDBMS-04）。DBドライバの生の例外メッセージ・スタックトレースはレスポンスに含めない
5. 接続テストは状態を変更しない読取専用の操作であるため、AuditLogへの記録対象としない（BR-RDBMS-05）。失敗時のみアプリケーションログ（SLF4J）に記録する（運用調査用。監査ログとは目的が異なる）

---

## 3. スキーマ取込（FR-2.2）

1. 管理者がスキーマ更新操作を実行する（`POST /api/admin/rdbms-connections/{id}/schema-refresh`）
2. SchemaIntrospectionServiceは対象接続の`dbType`から`RdbmsDialectStrategy`を解決する（`RdbmsDialectStrategy.resolveDialect(dbType)`）
3. ~~方言が要求する場合、スキーマ切替を適用する（`requiresSchemaSwitch()` / `applySchemaSwitch()`）。例: PostgreSQLでは`search_path`等でスキーマを切り替える。MySQL/MariaDBは「データベース」がスキーマに相当する単位として扱われるため切替は不要~~ 訂正（UNIT-04 Functional Designにて）: 1接続内に複数スキーマが存在しうる前提に変更したため、取込対象スキーマの一覧を求める（MySQL/MariaDBは接続のdatabaseNameを1件のみ、PostgreSQL/H2はシステムスキーマを除く全スキーマを`DatabaseMetaData.getSchemas()`で自動検出する）。JDBCのメタデータ取得はschemaPattern引数で直接絞り込めるため、事前のセッションスキーマ切替（`applySchemaSwitch`）は不要と判明し行わない
4. スキーマごとに、JDBC `DatabaseMetaData` API等を用いてテーブル/ビューの物理名・コメント・カラム・制約情報（§5、Q3=C）を読み取り、各テーブルに所属スキーマ名を記録する
5. 読取の途中で一部テーブル・ビューの読取に失敗した場合、取込処理全体を失敗として扱う（Q8=A、オールオアナッシング、BR-RDBMS-07）。この場合、既存のスキーマスナップショットは変更しない
6. 全テーブル・ビューの読取に成功した場合、対象接続の既存スキーマスナップショットを削除し、新規取込内容で完全に置き換える（Q2=A、全置換、BR-RDBMS-08）
7. 取込結果（成功／失敗）を`connectionId`に紐づけてAuditEventPublisher経由で記録する（`SCHEMA_IMPORTED`、`resultStatus`は`SUCCESS`/`FAILURE`。FR-2.2の受け入れ基準どおり、成功・失敗いずれの場合も監査ログに記録する）

---

## 4. 接続削除（Q6=B）

1. 管理者が接続情報を削除する（`DELETE /api/admin/rdbms-connections/{id}`）
2. RdbmsConnectionServiceは、参照有無の確認を行わず、対象接続に紐づくスキーマスナップショット（テーブル・カラム・制約情報）を無条件でカスケード削除する（BR-RDBMS-09）
3. AuditEventPublisher経由で`CONNECTION_DELETED`イベントを発行する

**将来の拡張に関する留意点**: 本ユニット時点では`connectionId`を参照する他ユニット（UNIT-04アクセス権限、UNIT-05マスタメンテナンス、UNIT-06以降のクエリ関連）は未実装のため、上記の無条件カスケード削除による実害はない。これらのユニット導入後に`connectionId`への参照（権限設定・保存クエリ等）が追加された場合、削除時の挙動（カスケード範囲の拡張、削除拒否への変更等）は当該ユニットのFunctional Designで改めて検討する（Q6=Bとしてユーザ承認済みの設計判断であり、本ユニットでの実装はスコープに含めない）。

---

## 5. 型・制約情報の取込範囲（Q3=C, Q4=B）

スキーマ取込時、各カラムについて以下を保持する。

- **型情報**: DB固有の型表記（`nativeType`、例: `VARCHAR(255)`, `INT`, `TIMESTAMP`）をそのまま保持しつつ、内部共通型（`normalizedType`: `STRING`/`NUMBER`/`DATE_TIME`/`BOOLEAN`/`BINARY`/`OTHER`）へ正規化した分類もあわせて保持する（Q4=B）。正規化により、後続ユニット（UNIT-05マスタメンテナンスの編集フォーム生成、UNIT-07クエリビルダーの入力コントロール選択等）で型に基づく処理が行いやすくなる
- **NOT NULL制約の有無、デフォルト値**
- **制約情報**（Q3=C）: 主キー・外部キー（参照先テーブル・カラムを含む）・一意制約・インデックスの4種

具体的なJDBC型コードと`normalizedType`の対応表は、実装レベルの詳細としてCode Generation段階で確定する。

---

## 6. 方言吸収の適用範囲（COMP-09、FR-7.5との関係）

本ユニットでは`RdbmsDialectStrategy`インターフェースと4つの実装クラス（`MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgresDialectStrategy`, `H2DialectStrategy`）を新設する。~~主にスキーマ取込時のスキーマ切替（§3手順3）に使用する~~ 訂正（UNIT-04 Functional Designにて）: `applySchemaSwitch()`はスキーマ取込では使用しなくなり、実行時スキーマ指定（FR-7.5、クエリ実行時の動的なスキーマ切替）専用となった。UNIT-06（クエリ保存・実行）で本格的に利用されるが、インターフェース自体は本ユニットで確定させ、UNIT-06から同一インターフェースを再利用する想定とする。

---

## 7. Testable Properties（PBT-01、Property-Based Testing拡張）

本ユニットのFR-2.1/2.2は、requirements.md §5.1で識別済みのPBT候補（権限判定・合成ロジック、YAML入出力、SQL生成ロジック）には含まれていない。改めて評価した結果、型正規化（§5）・方言解決（§6）はいずれも入力値が有限かつ少数（DB種別4種、JDBC型コードの分類程度）であり、境界値・組み合わせ爆発を伴う複雑なロジックではないため、PBTではなく通常の単体テスト（各DB種別・各型コードの網羅的なケース列挙）で十分と判断する。**本ユニットにPBT対象プロパティなし**。

# UNIT-03 RDBMSセットアップ - Frontend Components

`unit-03-functional-design-plan.md`のQ9=A（新規フロントエンドモジュール`rdbms-connection/`を新設）に基づく。UNIT-01で構築した`design-system`コンポーネント（`AppShell`, `PageHeader`, `DataTable`, `Modal`, `ConfirmDialog`, `EmptyState`, `Alert`, `Badge`, `TextInput`, `Select`, `Button`）を利用し、`frontend/src/rdbms-connection/`配下に新規実装する（機能エピック単位のモジュール構成、unit-of-work.md準拠）。

UNIT-01のナビゲーション項目「RDBMS接続設定」（`aidlc-docs/construction/unit-01/functional-design/frontend-components.md` §1.3）に対応する画面を本ユニットで実装する。

---

## 1. RDBMS接続一覧・管理画面（`/rdbms-connections`、`AppShell`）

### コンポーネント構造
```
RdbmsConnectionListPage (AppShell)
└ PageHeader（タイトル「RDBMS接続設定」、右上 Button「接続を追加」）
  ├ DataTable（列: 表示名, DB種別, host:port/databaseName, スキーマ取込状態（未取込／取込日時）, アクション）
  │  └ 行ごとのアクション
  │     - Button（「接続テスト」）… 全ステータス共通
  │     - Button（「スキーマ取込」）… 全ステータス共通
  │     - Link（「スキーマ詳細」）… 取込済みの場合のみ活性（§2へ遷移）
  │     - Button（「編集」）… 全ステータス共通
  │     - Button（「削除」）… 全ステータス共通
  ├ Modal（接続登録・編集フォーム。§1.1）
  ├ ConfirmDialog（削除確認。誤操作防止、BR-RDBMS-09のカスケード削除である旨を明示）
  ├ EmptyState（登録済み接続が0件の場合）
  └ Alert（tone可変。接続テスト結果・スキーマ取込結果・各操作の成功/失敗を表示）
```

### 1.1 接続登録・編集フォーム（Modal内）
- TextInput（displayName、表示名、required。他の接続と重複していても許容する。BR-RDBMS-02）
- Select（dbType、`MYSQL`/`MARIADB`/`POSTGRESQL`/`H2`、required。選択変更時、§1.2のデフォルトポート番号を`port`フィールドに自動入力する（レビュー指摘の反映）
- TextInput（host、required）
- TextInput（port、type=number、required、1〜65535。dbType選択時にデフォルト値が入るが、手動での上書きも可能）
- TextInput（databaseName、required）
- TextInput（schemaName、任意。`dbType`が`POSTGRESQL`または`H2`の場合のみ表示（レビュー指摘の反映、両方言ともスキーマの概念を持つため）
- TextInput（username、required）
- TextInput（password、type=password。編集時は空欄可＝変更しない場合は既存値を保持。APIは既存パスワードを返さないため、編集フォーム表示時点でも常に空欄から始まる。BR-RDBMS-12）
- TextInput（additionalParams、「追加パラメータ」、任意。プレースホルダーでJDBCクエリパラメータの入力例（例: `useSSL=false&serverTimezone=UTC`）を示す。BR-RDBMS-10）
- Button（「接続テスト」、フォーム下部。入力中の値でBR-RDBMS-11の未保存テストエンドポイントを呼び出す。結果はフォーム内にAlertで表示し、モーダルは閉じない）
- Button（type=submit、「登録」/「更新」）

### 1.2 dbType選択時のデフォルトポート自動入力（レビュー指摘の反映）
Selectで`dbType`を選択した時点で、`port`フィールドに以下のデフォルト値を自動入力する（クライアント側のみの利便性機能。BR-RDBMS-01のバリデーション自体は特定のポート番号を強制しない）。

| dbType | デフォルトport |
|---|---|
| `MYSQL` | 3306 |
| `MARIADB` | 3306 |
| `POSTGRESQL` | 5432 |
| `H2` | 9092（TCPサーバモードのデフォルト） |

新規登録時（`port`が未入力の場合）にのみ自動入力する。編集時に既存の`port`値がある場合、`dbType`を変更しても既存の値を上書きしない（管理者が意図的に設定した値を尊重する）。

### 1.3 フォーム内接続テストの挙動（レビュー指摘の反映、BR-RDBMS-11）
フォーム内「接続テスト」ボタンは、保存済みの接続に対する一覧画面側の「接続テスト」（BR-RDBMS-04、`POST /api/admin/rdbms-connections/{id}/test`）とは異なるAPIエンドポイント（`POST /api/admin/rdbms-connections/test`、接続情報一式をリクエストボディに含める、対象IDなし）を呼び出す。サーバ側は永続化を行わずに疎通確認のみを行う。テスト実行はフォームの入力内容を保存しない（Modalは閉じたままとなり、テスト結果のみをフォーム内Alertに表示する）。

### State
- `connections: ConnectionSummary[]`, `loading: boolean`, `errorMessage: string | null`
- `formModal: { mode: 'create' | 'edit', connectionId?: string, values: ConnectionFormValues } | null`
- `confirmDeleteTarget: string | null`（対象connectionId）
- `actionResult: { connectionId: string, kind: 'test' | 'schemaRefresh', success: boolean, message: string } | null`（接続テスト・スキーマ取込結果の一時表示用。BR-RDBMS-04のエラー分類を`message`に反映）

### バリデーション
- クライアント側: 必須項目、portの数値範囲（送信前の基本チェックのみ。BR-RDBMS-01の詳細判定はサーバ側）

### API連携
- `GET /api/admin/rdbms-connections` — 一覧取得
- `POST /api/admin/rdbms-connections` — 新規登録（BR-RDBMS-01, BR-RDBMS-02）
- `PUT /api/admin/rdbms-connections/{id}` — 更新（BR-RDBMS-03）
- `DELETE /api/admin/rdbms-connections/{id}` — 削除（確認ダイアログ経由。BR-RDBMS-09）
- `POST /api/admin/rdbms-connections/{id}/test` — 接続テスト（保存済み接続。BR-RDBMS-04、成功/失敗を`actionResult`に反映）
- `POST /api/admin/rdbms-connections/test` — 接続テスト（フォーム入力中の未保存の値。BR-RDBMS-11、§1.3参照）
- `POST /api/admin/rdbms-connections/{id}/schema-refresh` — スキーマ取込（BR-RDBMS-06〜08、成功/失敗を`actionResult`に反映。成功時は一覧の「スキーマ取込状態」列も再取得して更新）

---

## 2. スキーマ詳細画面（`/rdbms-connections/{id}/schema`、`AppShell`）

取込済みのテーブル・カラム・制約情報を確認する読取専用画面。unit-of-work.mdの動作確認方針（各ユニット完了時にユニット単体の手動確認を行う）を踏まえ、スキーマ取込結果を目視確認できるようにする。

### コンポーネント構造
```
SchemaDetailPage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「スキーマ詳細」、取込日時を表示）
  ├ DataTable（テーブル一覧: テーブル名, 種別（TABLE/VIEW、Badge表示）, コメント, カラム数）
  │  └ 行クリックで選択状態にし、下部のカラム一覧を対象テーブルのものに切り替える
  ├ DataTable（選択中テーブルのカラム一覧: カラム名, 型（nativeType）, 正規化型（normalizedType、Badge表示）, NULL許容, 制約（PK/FK/UNIQUE/INDEXをBadgeで列挙））
  └ EmptyState（スキーマ未取込の場合。「スキーマ取込」導線（§1一覧画面へのリンク）を表示）
```

### State
- `connectionId`（ルートパラメータ）
- `schema: SchemaSnapshotDetail | null`, `loading: boolean`, `errorMessage: string | null`
- `selectedTableName: string | null`（初期表示は一覧先頭のテーブル）

### API連携
- `GET /api/admin/rdbms-connections/{id}/schema` — スキーマスナップショット全体を取得（テーブル・カラム・制約を含む）

---

## 3. トップ画面のFeatureCard更新

UNIT-02で新設したトップ画面（`aidlc-docs/construction/unit-02/functional-design/frontend-components.md` §5）の「RDBMS接続設定」カードを、`implemented: false`（準備中バッジ）から`implemented: true`に変更し、`/rdbms-connections`へのリンクとして活性化する。

---

## 4. アクセス制御に関する前提

本ユニットの画面はいずれも管理者専用機能である（FR-2.1/2.2はいずれも「管理者が」実行する操作）。UNIT-02で実装済みのJWT認証・ロールチェックにより非管理者からのアクセスを403とする想定は踏襲するが、詳細な権限体系（画面単位・操作単位のアクセス制御）はUNIT-04で正式に設計する。本ユニットでは暫定的に「管理者ロール（`ADMIN`）であればすべての操作が可能」という単純な前提で実装する。

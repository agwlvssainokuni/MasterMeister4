# UNIT-05 マスタメンテナンス - Frontend Components

UNIT-02〜UNIT-04で確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき実装する。

**画面構成の訂正（Q11→Q16、追加質問への回答を反映）**: 当初Q11では「テーブル/ビュー一覧画面→レコード一覧画面」の2画面を想定していたが、一般ユーザ向けの「アクセス可能な接続」取得API（BR-MASTER-13）を新設する方針（Q15=A）に伴い、その手前に接続選択画面が必要になったため、3画面構成（接続選択→テーブル/ビュー一覧→レコード一覧）に訂正する（Q16=A）。

**ナビゲーション**: UNIT-01で仮予約済みのナビ項目（`key: 'masterData'`, `labelKey: 'nav.masterData'`, `path: '/master-data'`）をそのまま接続選択画面のルートとして使用する（訂正不要、既存の予約がそのまま合致する）。

**バックエンドAPIパス・パッケージ構成**: 既存の管理者向けAPI規約（`/api/admin/{管理対象エンティティ名の複数形}`）とは異なり、本ユニットは初の一般ユーザ向け（非管理者）機能であるため、新規のトップレベル名前空間`/api/master-data/*`を新設する（Q15=A、管理者ロールを要求しない）。パッケージは`cherry.mastermeister.masterdata`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）、コントローラは`MasterDataController`とする。

---

## 1. 接続選択画面（`/master-data`、`AppShell`）

### コンポーネント構造
```
MasterDataConnectionListPage (AppShell)
└ PageHeader（タイトル「マスタメンテナンス」）
  ├ DataTable（列: 接続の表示名のみ。Q17=A、ホスト名・ポート等は表示しない）
  │  └ 行クリックでテーブル/ビュー一覧画面へ遷移
  ├ EmptyState（アクセス可能な接続が0件の場合）
  └ Alert（取得失敗時）
```

### State
- `connections: AccessibleConnection[]`, `loading: boolean`, `errorMessage: string | null`

### API連携
- `GET /api/master-data/connections` — アクセス可能な接続一覧取得（BR-MASTER-13、新規`MasterDataController`）

---

## 2. テーブル/ビュー一覧画面（`/master-data/{connectionId}`、`AppShell`）

### コンポーネント構造
```
MasterDataTableListPage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「テーブル/ビュー一覧」）
  ├ DataTable（列: スキーマ名, テーブル/ビュー名, 種別（TABLE/VIEW）, アクション）
  │  └ 行クリックでレコード一覧画面へ遷移
  ├ EmptyState（スキーマ未取込の場合。案内メッセージ＋接続選択画面への戻り導線。Q13=A）
  ├ EmptyState（アクセス可能なテーブル/ビューが0件の場合）
  └ Alert（取得失敗時）
```

### State
- `connectionId`（ルートパラメータ）
- `tables: AccessibleTable[]`, `notImported: boolean`, `loading: boolean`, `errorMessage: string | null`

### API連携
- `GET /api/master-data/connections/{connectionId}/tables` — アクセス可能なテーブル/ビュー一覧取得（BR-MASTER-01〜02）

---

## 3. レコード一覧画面（`/master-data/{connectionId}/{schemaName}/{tableName}`、`AppShell`）

### コンポーネント構造
```
MasterDataRecordListPage (AppShell)
└ PageHeader（タイトル: テーブル/ビュー名、右上 Button「新規作成」（creatableな場合のみ活性）・Button「反映」（変更保留中のみ活性））
  ├ 絞込UI
  │  ├ フィルタ条件行（カラムSelect＋演算子Select（BR-MASTER-05、カラム型に応じて選択肢を変える）＋値TextInput）＋「条件追加」Button
  │  └ SQL手入力（折りたたみ可能な詳細セクション。WHERE句・ORDER BY句のTextArea、Q4=A、拒否時はAlertで理由表示）
  ├ DataTable（表示対象カラムのみ。editable=trueのセルはクリックでインライン編集モードへ、Q12=A）
  │  └ 行ごとのアクション: 「削除」Button（deletableな場合のみ活性。押下時点でpending-deleteとしてマーク、反映時にDELETE操作として送信）
  ├ Pagination（オフセットベース、Q8=A）
  ├ Modal（新規作成フォーム。編集可能カラムのTextInput一式）
  ├ Alert（反映結果。全体成功／行ごとの失敗理由一覧。BR-MASTER-07）
  └ EmptyState（レコード0件の場合）
```

### 3.1 インライン編集・一括反映の挙動（Q12=A）
1. `editable`なセルをクリックすると編集モードになり、TextInputで値を変更できる。変更内容はフロントエンド側の一時状態（`pendingChanges`）に保持し、即時送信はしない
2. 行の「削除」を押すと、当該行を`pendingDeletes`としてマークする（表示上は取り消し線等で保留中であることを示す）
3. 「新規作成」Modalで入力し確定すると、`pendingCreates`に追加する（画面上は末尾に保留行として表示）
4. 「反映」Button押下時、`pendingCreates`/`pendingChanges`/`pendingDeletes`をまとめて1回の`BatchOperationRequest`（BR-MASTER-06）としてAPIに送信する
5. 成功時、`pending*`状態をクリアしレコード一覧を再取得する。失敗時、Alertに行ごとの失敗理由を表示し、`pending*`状態は保持する（ユーザが修正して再送信できるように）

### 3.2 絞込・SQL手入力の挙動
- フィルタ条件の変更、SQL手入力の実行はいずれもレコード一覧の再取得をトリガーする（ページは1ページ目に戻す）
- SQL手入力が構文検証エラーで拒否された場合（BR-MASTER-04）、Alertでエラー内容を表示し、一覧は変更しない

### State
- `connectionId`, `schemaName`, `tableName`（ルートパラメータ）
- `columns: RecordColumn[]`, `page: RecordPage | null`, `pageNumber: number`
- `filters: RecordFilterCondition[]`, `rawQuery: { whereClause: string, orderByClause: string } | null`
- `pendingChanges: Map<primaryKeyJson, Map<columnName, string>>`, `pendingCreates: Map<columnName, string>[]`, `pendingDeletes: Set<primaryKeyJson>`
- `createModal: { values: Map<columnName, string> } | null`
- `batchResult: BatchOperationResult | null`（直近の反映結果、行ごとのエラー表示用）
- `loading: boolean`, `errorMessage: string | null`

### API連携
- `GET /api/master-data/connections/{connectionId}/tables/{schemaName}/{tableName}/records?page=&pageSize=&filter=...&where=&orderBy=` — レコード一覧取得（絞込・SQL入力・ページング。BR-MASTER-04〜05, BR-MASTER-10, BR-MASTER-14）
- `POST /api/master-data/connections/{connectionId}/tables/{schemaName}/{tableName}/records/batch` — 一括反映（作成/更新/削除混在。BR-MASTER-06〜09、成功時`MASTER_DATA_BATCH_APPLIED`）

**注記**: 上記エンドポイントのパス・クエリパラメータ形式・リクエストボディ形式は設計時点の想定であり、確定的な契約はCode Generation段階で定める。

---

## 4. トップ画面のFeatureCard活性化

`HomePage.tsx`の`IMPLEMENTED_KEYS`に`'masterData'`を追加し、`/master-data`（接続選択画面）へのリンクとして活性化する（UNIT-01で予約済みのFeatureCardをそのまま活性化するのみで、キー・パス・表示ラベルの変更は不要）。

---

## 5. アクセス制御に関する前提

本ユニットの画面はいずれも一般ユーザ向け機能である（STORY-4.1〜4.4はいずれも「一般ユーザとして」の要求）。UNIT-02のロールチェックにおいて、ログイン済み（`APPROVED`状態）であれば`ADMIN`/一般ユーザいずれでもアクセス可能とする（管理者専用画面のようなロール制限は課さない。実際のデータ・操作可否は実効権限判定＝BR-MASTER-01〜09が担う）。

# UNIT-04 アクセス制御 - Frontend Components

UNIT-02/UNIT-03で実際に確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき本ユニットも実装する。

**ナビゲーション項目の訂正（レビュー指摘の反映）**: UNIT-01では本ユニット向けのナビ項目を「アクセス制御」（`key: 'accessControl'`, `path: '/access-control'`）として仮予約していたが、`/users`→`UserManagementPage`・`/connections`→`RdbmsConnectionListPage`という既存の「ナビ項目＝管理対象エンティティ名のパスに直接対応する単一画面」という規約に照らすと、このナビ項目が実際に遷移する先はグループ管理画面のみ（権限設定本体は接続一覧画面経由の`/permissions/{connectionId}`から独立して到達する）であるため、「アクセス制御」という機能名ではなく管理対象エンティティ「グループ」に合わせたパス・表示名に訂正する。`design-system/components/navigation.ts`の該当エントリを`key: 'groups'`, `labelKey: 'nav.groups'`, `path: '/groups'`に変更し（内部識別子`key`も併せて訂正）、i18nリソース（`design-system.json`の`nav.accessControl`→`nav.groups`＝「グループ管理」、`common.json`の`home.card.accessControl`→`home.card.groups`）を更新した。

**バックエンドAPIパス・画面パス・パッケージ構成の訂正（レビュー指摘の反映）**: 同様の理由で、バックエンドAPIパスも既存の確立された規約（`/api/admin/users`＝UNIT-02、`/api/admin/rdbms-connections`＝UNIT-03。いずれも`/api/admin/{管理対象エンティティ名の複数形}`で、機能・エピック名は含まない）に合わせて訂正する。グループ関連は`/api/admin/groups`（新規`GroupController`）とする。権限設定については、当初UNIT-03の`RdbmsConnectionController`配下（`/api/admin/rdbms-connections/{id}/permissions`）へ追加する案も検討したが、`AccessPermission`関連の機能（COMP-10 AccessControlService, COMP-11 EffectivePermissionResolver, COMP-12 PermissionYamlService）はUNIT-04独自のドメインであり、UNIT-03の`RdbmsConnectionService`/`SchemaIntrospectionService`とはモジュール境界が異なる（`SchemaSnapshot`が接続に完全従属し接続削除でカスケード消滅するのに対し、`AccessPermission`はより独立したドメイン概念）。またグループもトップレベルの独立リソース（`/groups`）としたこととの一貫性も踏まえ、権限設定も同様にトップレベルの独立リソースとする（画面パス`/permissions/{connectionId}`、API`/api/admin/permissions/{connectionId}/*`、新規`PermissionController`）。

**パッケージ構成**: 既存の単数形ドメイン名によるパッケージ命名規約（`registration`, `auth`, `rdbmsconnection`, `audit`）と、UNIT-02が単一エピックを`registration`/`auth`の2パッケージに分割した前例に倣い、`accesscontrol`という機能名の単一パッケージにまとめず、`group`パッケージ（`Group`, `GroupMembership`, `GroupController`）と`permission`パッケージ（`AccessPermission`, `AccessControlService`, `EffectivePermissionResolver`, `PermissionYamlService`, `PermissionController`）の2パッケージに分割する。あわせて、フロントエンドのコンポーネント名も`AccessControlGroupsPage`から`GroupManagementPage`に改める（`UserManagementPage`との命名一貫性のため）。

**設計方針（チャットでの議論により確定）**: 接続の選択は権限設定画面への遷移前（接続一覧画面からの導線）で完了させ、権限設定画面内でのDB接続切替は行わない（UNIT-03の`/connections/{id}/schema`と同じルーティング方針）。権限設定画面の基本構造は「プリンシパル主体（ツリー型）」を採用する: プリンシパル（ユーザ／グループ）を選択すると、リソース階層（スキーマ／テーブル／カラム。1接続内に複数スキーマが存在しうる前提、UNIT-03 Functional Design訂正参照）のツリーが展開され、各ノードで権限を直接編集する。

**ツリーUIについて**: 既存のdesign-systemコンポーネントに汎用的なTreeコンポーネントは存在しない。本ユニットの階層（スキーマ／テーブル／カラムの3階層、各ノードに主権限セレクト＋補助権限チェックボックスという固有の編集UIを持つ）は`DataTable`の行選択モデルにはなじまないため、新規の汎用Treeコンポーネントを設計システムに追加するのではなく、`AccessPermissionTreePage`固有の展開／折りたたみ表示として実装する（UNIT-03の`SchemaDetailPage`がマスタ・ディテール2画面をそれぞれ`DataTable`で素朴に実装したのと同様、必要最小限の実装を優先する）。

---

## 1. グループ管理画面（`/groups`、`AppShell`）

### コンポーネント構造
```
GroupManagementPage (AppShell)
└ PageHeader（タイトル「グループ管理」、右上 Button「グループを作成」）
  ├ DataTable（列: グループ名, 所属ユーザ数, アクション）
  │  └ 行ごとのアクション
  │     - Button（「所属ユーザを管理」）… §1.2のModalを開く
  │     - Button（「改名」）… §1.1のModalを開く（編集モード）
  │     - Button（「削除」）… ConfirmDialog経由
  ├ Modal（グループ作成・改名フォーム、§1.1）
  ├ Modal（所属ユーザ管理、§1.2）
  ├ ConfirmDialog（削除確認。BR-ACCESS-11のカスケード削除である旨を明示）
  ├ EmptyState（登録済みグループが0件の場合）
  └ Alert（各操作の成功/失敗を表示）
```

### 1.1 グループ作成・改名フォーム（Modal内）
- TextInput（name、グループ名、required）
- Button（type=submit、「作成」/「改名」）

### 1.2 所属ユーザ管理（Modal内）
- 現在の所属ユーザ一覧（Badge等でemail／氏名を列挙、各行に「削除」Button）
- ユーザ追加用の検索可能なSelect（UNIT-02の管理者向けユーザ一覧API`GET /api/admin/users`を流用し、承認済み（`APPROVED`）ユーザから選択）＋「追加」Button

### State
- `groups: GroupSummary[]`, `loading: boolean`, `errorMessage: string | null`
- `formModal: { mode: 'create' | 'rename', groupId?: string, name: string } | null`
- `membershipModal: { groupId: string, members: UserSummary[] } | null`
- `confirmDeleteTarget: string | null`（対象groupId）

### API連携
- `GET /api/admin/groups` — 一覧取得（所属ユーザ数を含む）
- `POST /api/admin/groups` — 作成（BR-ACCESS-12、`GROUP_CREATED`）
- `PUT /api/admin/groups/{id}` — 改名（`GROUP_RENAMED`）
- `DELETE /api/admin/groups/{id}` — 削除（確認ダイアログ経由。BR-ACCESS-11、`GROUP_DELETED`）
- `GET /api/admin/groups/{id}/members` — 所属ユーザ一覧取得
- `POST /api/admin/groups/{id}/members` — ユーザ追加（`GROUP_MEMBER_ADDED`）
- `DELETE /api/admin/groups/{id}/members/{userId}` — ユーザ削除（`GROUP_MEMBER_REMOVED`）

---

## 2. 権限設定画面（`/permissions/{connectionId}`、`AppShell`）

### コンポーネント構造
```
AccessPermissionTreePage (AppShell)
└ PageHeader（タイトル: 接続の表示名＋「権限設定」、右上 Button「YAMLエクスポート」・Button「YAMLインポート」）
  ├ プリンシパル選択部
  │  ├ Select（対象種別: ユーザ / グループ）
  │  └ Select（検索可能。対象種別に応じてユーザ一覧またはグループ一覧を選択肢に表示）
  ├ 権限ツリー（プリンシパル未選択時は非表示、EmptyStateで選択を促す）
  │  └ スキーマノード（UNIT-03のスキーマ自動検出結果に基づき、1接続内のスキーマごとに1行。MySQL/MariaDBは常に1件のみ、PostgreSQL/H2は複数件になりうる）
  │     - 主権限Select（未設定 / NONE / READ / UPDATE）
  │     - CREATE Checkbox, DELETE Checkbox
  │     └ ▼展開 → テーブルノード（当該スキーマ内のテーブルごとに1行、Icon（chevron）で展開/折りたたみ）
  │         - 主権限Select（未設定 / NONE / READ / UPDATE）
  │         - CREATE Checkbox, DELETE Checkbox
  │         └ ▼展開 → カラムノード（カラムごとに1行）
  │             - 主権限Select（未設定 / NONE / READ / UPDATE）（補助権限は表示しない。BR-ACCESS-01）
  ├ Modal（YAMLインポート。ファイル選択＋実行Button。§2.2）
  ├ EmptyState（スキーマ未取込の場合。「スキーマ取込」導線＝接続一覧画面へのリンクを表示。Q10=A）
  └ Alert（tone可変。権限変更・YAML入出力の成功/失敗を表示）
```

### 2.1 権限編集の挙動
- 各ノードの主権限Selectで「未設定」以外を選択した時点で、`PUT`（アップサート）APIを呼び出し即時反映する（フォーム送信ボタンなし、行単位の即時保存。UNIT-05以降のインライン編集パターンを先取りする設計とせず、本ユニットでは単純に即時保存とする）
- 「未設定」を選択した場合、`DELETE`（当該プリンシパル・当該リソースの明示設定を解除）APIを呼び出す（BR-ACCESS-01）
- CREATE/DELETE Checkboxの切替も同様に即時保存する（テーブルノード・スキーマノードのみ表示。カラムノードには表示しない）
- 保存の成功/失敗はAlertで一時的に表示する

### 2.2 YAMLエクスポート／インポート
- 「YAMLエクスポート」Buttonは、対象接続の全権限設定をYAMLファイルとしてダウンロードする（プリンシパル選択状態に関わらず、接続全体の設定をエクスポートする。BR-ACCESS-09）
- 「YAMLインポート」Buttonは、ファイル選択Modalを開き、選択したYAMLファイルの内容でインポートを実行する（プレビューなし。Q9=A）。失敗（未解決プリンシパル・重複エントリ）時はAlertでエラー内容を表示し、既存設定は変更されない（BR-ACCESS-10）
- インポート成功時、現在表示中のツリー（選択中プリンシパルの設定）を再取得して表示を更新する

### 2.3 スキーマ未取込時の挙動（Q10=A）
接続がスキーマ未取込（UNIT-03の`SCHEMA_NOT_IMPORTED`）の場合、ツリー・プリンシパル選択部を表示せず、UNIT-03の`SchemaDetailPage`と同様の案内メッセージ（EmptyState＋「接続一覧へ戻る」導線）を表示する。

### State
- `connectionId`（ルートパラメータ、`/permissions/{connectionId}`）
- `schema: SchemaSnapshotDetail | null`（UNIT-03のスキーマ取得APIを流用し、ツリー構造の元データとする）
- `selectedPrincipal: { type: 'USER' | 'GROUP', id: string } | null`
- `permissions: AccessPermissionEntry[]`（選択中プリンシパルの明示設定一覧。ツリー描画時、各ノードにオーバーレイして「未設定」か否かを判定する）
- `expandedTables: Set<string>`（展開中のテーブル名）
- `notImported: boolean`, `loading: boolean`, `errorMessage: string | null`
- `importModal: { file: File | null } | null`

### API連携
- `GET /api/admin/rdbms-connections/{id}/schema` — ツリー構造（テーブル・カラム一覧）取得（UNIT-03既存API流用）
- `GET /api/admin/permissions/{connectionId}?principalType=&principalId=` — 選択中プリンシパルの明示設定一覧取得（新規`PermissionController`、`permission`パッケージ）
- `PUT /api/admin/permissions/{connectionId}` — 権限設定（アップサート。BR-ACCESS-01、`PERMISSION_CHANGED`）。リクエストボディに対象キー（`principalType`, `principalId`, `schemaName`, `tableName`（任意）, `columnName`（任意））と設定値（`primaryPermission`, `createPermission`, `deletePermission`）を含める
- `DELETE /api/admin/permissions/{connectionId}` — 権限設定の解除（未設定に戻す＝当該キーの`AccessPermission`行1件のみを削除。`PERMISSION_CHANGED`）。DELETEはリクエストボディを持たない設計とするため、対象キー（`principalType`, `principalId`, `schemaName`, `tableName`（任意）, `columnName`（任意））はクエリパラメータで指定する（例: `?principalType=USER&principalId=42&schemaName=public&tableName=products&columnName=category_id`）。`connectionId`のみでは対象行を特定できないため、これらのクエリパラメータは必須（`tableName`/`columnName`は対象階層に応じて省略可）
- `GET /api/admin/permissions/{connectionId}/export` — YAMLエクスポート（`PERMISSION_YAML_EXPORTED`）
- `POST /api/admin/permissions/{connectionId}/import` — YAMLインポート（`PERMISSION_YAML_IMPORTED`）

**注記**: 上記エンドポイントのパス・リクエスト形式は設計時点の想定であり、確定的な契約はCode Generation段階で定める。

---

## 3. RDBMS接続一覧画面への導線追加

UNIT-03で実装済みの`RdbmsConnectionListPage`（`/connections`）の行ごとのアクションに、「権限設定」Link（§2の画面へ遷移、`/permissions/{connectionId}`）を追加する。「スキーマ詳細」Linkと同様、スキーマ取込済みの場合のみ活性化する（未取込の場合も遷移自体は可能とし、遷移先でQ10=Aの案内を表示する構成でもよい。具体的な活性/非活性の挙動はCode Generationで確定する）。

---

## 4. トップ画面のFeatureCard更新

UNIT-02で新設したトップ画面の「グループ管理」カード（訂正: 表示名は「アクセス制御」から変更、§前述参照）を活性化する。`HomePage.tsx`の`IMPLEMENTED_KEYS`に`'groups'`を追加し、`/groups`（グループ管理画面）へのリンクとして活性化する。

---

## 5. アクセス制御に関する前提

本ユニットの画面はいずれも管理者専用機能である（FR-2.3〜FR-2.15はいずれも「管理者が」実行する操作）。UNIT-03と同様、本ユニット時点でも「管理者ロール（`ADMIN`）であればすべての操作が可能」という前提を踏襲する（本ユニット自体が一般ユーザ向けの実効権限判定ロジックを提供するが、管理画面自体へのアクセス制御はUNIT-02のロールチェックのままとする）。

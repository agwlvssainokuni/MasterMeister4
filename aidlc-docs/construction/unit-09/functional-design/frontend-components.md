# UNIT-09 監査ログ閲覧 - Frontend Components

UNIT-02〜UNIT-08で確立した規約（`frontend/src/pages/`・`frontend/src/api/`のフラット構成）に基づき実装する。

**ナビゲーション**: UNIT-01で仮予約済みのナビ項目（`key: 'auditLog'`, `labelKey: 'nav.auditLog'`, `path: '/audit-log'`）をそのまま使用する（`navigation.ts`に追加作業は不要）。

**バックエンドAPIパス・パッケージ構成**: パッケージは`cherry.mastermeister.audit`（既存、UNIT-02実装済み。本ユニットはController・閲覧用Service・関連DTOを同パッケージに追加する）。

**APIパス規約（BR-AUDITVIEW-03）**: 既存の`/api/admin/**`名前空間に配置する（UNIT-02の`AdminUserController`、UNIT-04の`GroupController`と同じ方式）。

- `GET /api/admin/audit-log?occurredAtFrom=...&occurredAtTo=...&eventType=...&userId=...&connectionId=...&resultStatus=...&page=0&pageSize=...` — 監査ログ一覧取得（絞込・ページング）

**管理者専用アクセス制御のフロントエンド側の扱い（既存パターンの踏襲）**: `GroupManagementPage`（UNIT-04）等の既存の管理者専用画面は、フロントエンド側でロールに応じた特別なガード（ルーティング分岐等）を持たない。`ProtectedRoute`は認証済みかどうかのみを判定し、実際の権限境界はバックエンドの`/api/admin/**`が返す403 Forbiddenに委ねられている（一般ユーザがアクセスした場合はAPI呼び出し失敗として`Alert`表示される）。本ユニットもこの既存パターンをそのまま踏襲し、フロントエンド側に独自の管理者判定ロジックを追加しない。

---

## 画面構成

### 監査ログ一覧画面（`/audit-log`、`AppShell`、単一画面、BR-AUDITVIEW-02）

```
AuditLogPage (AppShell)
└ PageHeader（タイトル「監査ログ」）
  ├ Alert（取得失敗時。一般ユーザによるアクセス試行時は403エラーがここに表示される）
  ├ 絞込条件一覧（UNIT-08で確立した縦並びレイアウトを踏襲。各行はlabelと入力欄を横並びに配置、
  │  外側はflex-direction:columnで縦に積む。FormField・FilterBarは使用しない、UNIT-08の
  │  承認前レビュー対応と同じ方針）
  │  ├ 発生日時範囲（開始・終了の日時入力2件、片側指定可）
  │  ├ イベント種別セレクタ（Select。全28種別をカテゴリ分けせずフラットな選択肢として提示、
  │  │  BR-AUDITVIEW-09）
  │  ├ 対象ユーザセレクタ（Select。選択肢はUNIT-02の`GET /api/admin/users`から取得する
  │  │  ユーザ一覧。表示はユーザの表示名、値は`userId`）
  │  ├ 対象接続セレクタ（Select。選択肢はUNIT-03の接続一覧APIから取得する接続一覧。
  │  │  表示は接続の表示名、値は`connectionId`）
  │  └ 結果ステータスセレクタ（Select。`SUCCESS`/`FAILURE`の2択）
  ├ DataTable（列: 発生日時、イベント種別、対象ユーザ（表示名、nullの場合は「-」）、
  │  対象接続（表示名、nullの場合は「-」）、対象リソース、結果ステータス、詳細）
  │  └ 行クリックでの詳細表示・他画面への遷移導線は設けない（BR-AUDITVIEW-11、Q8=A）。
  │     `detail`列がそのまま一覧に表示されるため、モーダル等の追加UIは不要
  └ Pagination（design-system既存コンポーネント。page/totalPages/onChange。
     UNIT-08で確立した1-indexed↔0-indexed変換パターンを本画面の1箇所に限定して適用）
```

**State**: `entries: AuditLogEntryView[]`、`page: number`、`totalPages: number`、絞込条件一式（`occurredAtFrom`, `occurredAtTo`, `eventType`, `userId`, `connectionId`, `resultStatus`）、`users: UserSummary[]`（対象ユーザセレクタ用、画面初期表示時に一括取得）、`connections: ConnectionSummary[]`（対象接続セレクタ用、画面初期表示時に一括取得）、`loading: boolean`、`errorMessage: string | null`

**絞込条件変更時のページリセット**: UNIT-08の`onFilterChange`ヘルパーと同じ方式で、絞込条件変更のたびに`page`を0へ戻す。

**API連携**:
- `GET /api/admin/audit-log`（絞込・ページング一覧取得）
- `GET /api/admin/users`（既存、UNIT-02。対象ユーザセレクタの選択肢取得）
- `GET /api/admin/rdbms-connections`（既存、UNIT-03。対象接続セレクタの選択肢取得）。**実装確認済み**: 本エンドポイントは既に`/api/admin/**`配下で管理者専用として保護されており、`RdbmsConnectionService.listConnections()`はユーザの権限に関わらず全接続を返す（UNIT-08のようなユーザ権限に基づく絞込は行っていない）。したがって、そのまま再利用してもUNIT-08のBR-QUERYHISTORY-11のような情報漏洩リスクは生じない

---

## i18n・共通コンポーネント方針

- タイトル・ラベル等は`common.json`（ja/en）に`auditLog.*`名前空間で追加する
- 既存の`DataTable`・`Pagination`・`Select`・`TextInput`（design-system）をそのまま利用し、新規UIコンポーネントの追加は行わない
- イベント種別の表示名（`AuditEventType`の28値それぞれ）は`auditLog.eventType.*`のi18nキーで管理する

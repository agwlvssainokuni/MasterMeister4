# UNIT-09 監査ログ閲覧 - Domain Entities

本ユニットは新規の永続化エンティティを持たない。既存の`AuditLogEntry`（UNIT-02実装済み）を参照専用で利用し、閲覧・絞込用のDTOのみを新規に定義する。

## 1. AuditLogEntry（既存、UNIT-02実装済み、変更なし）

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 一意識別子 |
| `occurredAt` | Instant | 発生日時 |
| `userId` | Long（nullable） | 操作を行ったユーザ。イベント種別によっては存在しない（例: 未認証状態での操作失敗） |
| `connectionId` | Long（nullable） | 対象接続。接続に関わらないイベント種別（`LOGIN`等）ではnull |
| `eventType` | `AuditEventType`（enum, 28値） | イベント種別 |
| `targetResource` | String（nullable） | 対象リソースの識別情報。意味はイベント種別ごとに異なる |
| `resultStatus` | `ResultStatus`（enum: `SUCCESS`/`FAILURE`） | 結果ステータス |
| `detail` | String（nullable、最大2000文字） | 詳細情報 |

インデックス: `occurred_at`, `event_type`, `user_id`（既存）。**`connection_id`を含む複合インデックスを本ユニットで新規追加**（BR-AUDITVIEW-10）。

外部キー制約なし（意図的、UNIT-02の設計判断。対象リソースのライフサイクル変更が監査履歴に影響しないため）。イミュータブル（setterなし）。

## 2. AuditLogSearchCriteria（新規、DTO、永続化なし）

監査ログ一覧取得APIのリクエストパラメータを表す。

| 属性 | 型 | 説明 |
|---|---|---|
| `occurredAtFrom` | Instant（nullable） | 発生日時範囲の開始（片側指定可） |
| `occurredAtTo` | Instant（nullable） | 発生日時範囲の終了（片側指定可） |
| `eventType` | `AuditEventType`（nullable） | イベント種別の絞込 |
| `userId` | Long（nullable） | 対象ユーザの絞込 |
| `connectionId` | Long（nullable） | 対象接続の絞込 |
| `resultStatus` | `ResultStatus`（nullable） | 結果ステータスの絞込 |
| `page` | int | ページ番号（0始まり、Spring Data JPA標準） |
| `pageSize` | int | 1ページあたりの件数 |

## 3. AuditLogEntryView（新規、DTO、永続化なし）

監査ログ一覧APIのレスポンス1件分を表す。`AuditLogEntry`の全フィールドに、表示用の解決済み情報を付加する。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | `AuditLogEntry.id` |
| `occurredAt` | Instant | 発生日時 |
| `userId` | Long（nullable） | 操作を行ったユーザID |
| `userDisplayName` | String（nullable） | ユーザの表示名（`userId`が非nullの場合に`User.fullName`から解決。解決不可の場合は「(不明なユーザ)」、BR-AUDITVIEW-07） |
| `connectionId` | Long（nullable） | 対象接続ID |
| `connectionDisplayName` | String（nullable） | 接続の表示名（`connectionId`が非nullの場合に`RdbmsConnection.displayName`から解決。削除済みの場合は「(削除済み接続)」、BR-AUDITVIEW-07） |
| `eventType` | `AuditEventType` | イベント種別 |
| `targetResource` | String（nullable） | 対象リソースの識別情報 |
| `resultStatus` | `ResultStatus` | 結果ステータス |
| `detail` | String（nullable） | 詳細情報 |

**AuditLogEntryとの関係**: AuditLogEntry 1 – 1 AuditLogEntryView（表示用に変換するのみ、永続化なし）
**Userとの関係（参照のみ）**: User 0..1 – N AuditLogEntry（`userId`、UNIT-02のUserエンティティを参照専用で利用）
**RdbmsConnectionとの関係（参照のみ）**: RdbmsConnection 0..1 – N AuditLogEntry（`connectionId`、UNIT-03のRdbmsConnectionエンティティを参照専用で利用）

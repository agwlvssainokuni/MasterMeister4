# UNIT-08 クエリ履歴 - Domain Entities

本ユニットは新規の永続化エンティティを持たない。既存の`QueryExecutionRecord`（UNIT-06実装済み）を参照専用で利用し、閲覧・絞込用のDTOのみを新規に定義する。

## 1. QueryExecutionRecord（既存、UNIT-06実装済み、変更なし）

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 一意識別子 |
| `executedBy` | Long（UserId） | 実行者 |
| `connectionId` | Long（ConnectionId） | 対象接続 |
| `schemaName` | String | 実行時に指定した対象スキーマ |
| `sql` | String（CLOB） | 実行したSQL |
| `params` | String（CLOB、nullable） | バインドしたパラメータ（JSON文字列） |
| `savedQueryId` | Long（nullable） | 保存クエリ経由で実行した場合の参照。ad-hoc実行の場合はnull |
| `rowCount` | long | 結果件数 |
| `durationMillis` | long | 実行時間（ミリ秒） |
| `executedAt` | Instant | 実行日時 |

インデックス: `executedBy`, `executedAt`, `savedQueryId`（既存のまま、変更なし）。

## 2. QueryHistorySearchCriteria（新規、DTO、永続化なし）

履歴一覧取得APIのリクエストパラメータを表す。

| 属性 | 型 | 説明 |
|---|---|---|
| `connectionId` | Long | 対象接続（パスパラメータ） |
| `executedByScope` | enum（`ALL` / `MINE`） | 実行者スコープ。一般ユーザは`MINE`に強制（BR-QUERYHISTORY-03） |
| `executedAtFrom` | Instant（nullable） | 実行日時範囲の開始（片側指定可） |
| `executedAtTo` | Instant（nullable） | 実行日時範囲の終了（片側指定可） |
| `schemaName` | String（nullable） | 対象スキーマの絞込 |
| `sqlKeyword` | String（nullable） | SQLテキストの部分一致検索キーワード |
| `page` | int | ページ番号（0始まり、Spring Data JPA標準） |
| `pageSize` | int | 1ページあたりの件数 |

## 3. QueryHistoryRecordView（新規、DTO、永続化なし）

履歴一覧APIのレスポンス1件分を表す。`QueryExecutionRecord`の全フィールドに、表示用の解決済み情報を付加する。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | `QueryExecutionRecord.id` |
| `executedBy` | Long | 実行者ユーザID |
| `executorDisplayName` | String | 実行者の表示名（`User.fullName`から解決。解決不可の場合はプレースホルダー、BR-QUERYHISTORY-04の例外扱い） |
| `connectionId` | Long | 対象接続 |
| `schemaName` | String | 対象スキーマ |
| `sql` | String | 実行したSQL |
| `savedQueryId` | Long（nullable） | 保存クエリID |
| `savedQueryName` | String（nullable） | 保存クエリ名（`savedQueryId`が非nullの場合に解決。対象が見つからない場合は「(削除済み)」、BR-QUERYHISTORY-06） |
| `queryType` | enum（`SAVED` / `AD_HOC`） | `savedQueryId`の有無から導出する種別（FR-8.2） |
| `rowCount` | long | 結果件数 |
| `durationMillis` | long | 実行時間 |
| `executedAt` | Instant | 実行日時 |

**QueryExecutionRecordとの関係**: QueryExecutionRecord 1 – 1 QueryHistoryRecordView（表示用に変換するのみ、永続化なし）
**Userとの関係（参照のみ）**: User 1 – N QueryExecutionRecord（`executedBy`、UNIT-02のUserエンティティを参照専用で利用、本ユニットでは新規保存・更新は行わない）
**SavedQueryとの関係（参照のみ）**: SavedQuery 0..1 – N QueryExecutionRecord（`savedQueryId`、UNIT-06のSavedQueryエンティティを参照専用で利用）

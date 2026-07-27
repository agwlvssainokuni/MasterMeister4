# UNIT-09 監査ログ閲覧 Functional Design Plan

## 対象ユニット概要

- **対応エピック**: Epic 9、**対応ストーリー**: STORY-9.1
- **対応要件**: requirements.md §6.1〜6.3（監査ログ要件、FR-IDなし）
- **対応コンポーネント**: COMP-18（閲覧機能）
- **責務**: UNIT-02で構築した監査ログ記録基盤（`AuditLogEntry`）に蓄積されたログの、管理者向け閲覧・絞込機能
- **前提ユニット**: UNIT-01, UNIT-02（記録基盤）。実質的にはUNIT-08まで全ユニットの記録内容が閲覧対象
- **PBT対象**: なし（STORY-9.1にPBT対象外と明記）

## 既存資産の確認結果

- `AuditLogEntry`（UNIT-02実装済み、`audit_log_entry`テーブル）: `id`, `occurredAt`, `userId`（nullable）, `connectionId`（nullable）, `eventType`（`AuditEventType`enum）, `targetResource`（nullable）, `resultStatus`（`ResultStatus`enum: SUCCESS/FAILURE）, `detail`（nullable、最大2000文字）。イミュータブル（setterなし）。外部キー制約は意図的になし（対象リソースのライフサイクル変更が監査履歴に影響しないため）
- 既存インデックス: `occurred_at`, `event_type`, `user_id`の3本。**`connection_id`にはインデックスがない**（UNIT-08で発見した`query_execution_record`と同種の課題）
- `AuditEventType`は既に28値（UNIT-02〜06で追加、UNIT-07/08は新規追加なし、既存の`QUERY_EXECUTED`等を流用。実装確認の結果27値ではなく28値と判明、本ファイル作成時の誤記を訂正）
- `AuditLogService`が`@TransactionalEventListener(phase = AFTER_COMMIT)`＋`REQUIRES_NEW`で同期的に`AuditLogEntry`を永続化する（記録処理自体は本ユニットのスコープ外、変更しない）
- `AuditLogEntryRepository`は空の`JpaRepository`のまま。絞込・ページング用のクエリメソッドは本ユニットで新規に追加する
- Controller・フロントエンド画面は未実装。ナビ項目`{ key: 'auditLog', labelKey: 'nav.auditLog', path: '/audit-log' }`はUNIT-01で仮予約済み
- 大量データ取得閾値（`AppProperties.Audit.bulkAccessThreshold`、既定100件）は設定済みで、`MASTER_DATA_BULK_ACCESSED`イベントとして既に記録されている（STORY-9.1の受け入れ基準と整合、変更不要）
- **UNIT-08との重要な違い**: UNIT-08は一般ユーザも利用可能でロールに応じた絞込（フェイルクローズ）が必要だったが、本ユニットは**管理者専用**（STORY-9.1「一般ユーザはアクセス権不足で拒否」）。ロールベースのデータ絞込ではなく、エンドポイント全体へのアクセス制御が必要

## 実行計画

- [x] Step 1: ユニット定義・関連ストーリー・既存コンポーネント（UNIT-02 AuditLogEntry等）の再確認（完了、本ファイル冒頭に反映）
- [x] Step 2: 本計画ファイルの作成・質問の提示
- [x] Step 3: ユーザからの回答収集・曖昧性チェック（全問A、曖昧性なし）
- [x] Step 4: `business-logic-model.md` 作成（一覧取得・絞込ロジック、ページング方式）
- [x] Step 5: `business-rules.md` 作成（BR-AUDITVIEW-01〜、アクセス制御・絞込条件等のルール化）
- [x] Step 6: `domain-entities.md` 作成（既存AuditLogEntryの参照、表示用DTO定義）
- [x] Step 7: `frontend-components.md` 作成（画面構成、一覧・絞込UIコンポーネント階層、状態管理、API連携ポイント）
- [x] Step 8: 完了メッセージ提示・承認待ち

---

## 質問

以下の質問に回答してください。各質問は文字（A, B, C...）で回答し、最後の選択肢（Other）を選ぶ場合は自由記述してください。

## Question 1
画面構成は？

A) 単一の監査ログ一覧画面（`connectionId`を持たないイベント（ログイン等）も扱うため、接続選択を前提としない一覧画面とし、対象接続は絞込条件の1つとして選択する）

B) UNIT-05〜08と同じ「接続選択画面→履歴一覧画面」の2画面構成

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 2
管理者専用のアクセス制御方式は？

A) 既存の`/api/admin/**`パスパターンに配置し、`SecurityConfig`の`.requestMatchers("/api/admin/**").hasRole("ADMIN")`ルールでカバーする（UNIT-02の`AdminUserController`、UNIT-04の`GroupController`/`PermissionController`と同じ方式。ロールベースのデータ絞込ではなくエンドポイント全体を遮断する）

B) 新規パス（`/api/audit-log/**`等）に配置し、`@PreAuthorize("hasRole('ADMIN')")`で個別制御する

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 3
ページングの実装方式は？

A) UNIT-08で確立したSpring Data JPAの標準`Pageable`/`Page`パターンを踏襲する（動的絞込には同じく`Specification` APIを使う）

B) 異なる方式にする

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 4
提供する絞込条件の範囲は？

A) 日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータスの5種（requirements.md §6.2の記録項目に対応）。対象リソースのテキスト検索は含めない

B) 上記に加え、対象リソース（`targetResource`）のテキスト部分一致検索も含める

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 5
ユーザID・接続IDの表示名解決は？

A) UNIT-08と同じ方式で一括解決する（`UserRepository`/`RdbmsConnectionRepository`の`findAllById`によるN+1回避）。削除済みユーザ・接続は「(削除済み)」等のプレースホルダー表示とする

B) IDのみ表示し、名前解決は行わない

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 6
27種類ある`AuditEventType`の絞込UI表現は？

A) カテゴリ分けせず、全種別をフラットな一覧としてSelectの選択肢にする（シンプルさ優先）

B) 認証イベント／管理操作／データアクセスイベントの3カテゴリでグルーピングして表示する（requirements.md §6.1の分類に対応、UIはやや複雑になる）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 7
`audit_log_entry`テーブルへの新規インデックス追加要否は？（既存は`occurred_at`・`event_type`・`user_id`のみ、`connection_id`にインデックスなし）

A) 新規マイグレーションで`connection_id`を含む複合インデックス（例: `(connection_id, occurred_at)`）を追加する

B) 既存インデックスのみで運用する（想定データ量・利用頻度では問題ないと判断する）

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 8
監査ログ一覧から他画面への遷移導線は？

A) 設けない（純粋な閲覧・絞込機能に留める。STORY-9.1の受け入れ基準にも画面遷移の要件はない）

B) 対象リソース（接続・ユーザ等）への遷移導線を設ける

C) Other (please describe after [Answer]: tag below)

[Answer]: A

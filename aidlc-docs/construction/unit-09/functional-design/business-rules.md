# UNIT-09 監査ログ閲覧 - Business Rules

## BR-AUDITVIEW-01: 閲覧対象データソースは既存のAuditLogEntryのみ（Q1=A）

本ユニットは`AuditLogEntry`（UNIT-02実装済み、UNIT-03〜08でイベント種別を追加）の閲覧・絞込・ページング機能のみを追加する。テーブル・記録ロジック（`AuditLogService`, `AuditEventPublisher`）への変更は行わない。

## BR-AUDITVIEW-02: 画面構成は単一の一覧画面（Q1=A）

`connectionId`を持たないイベント種別（`LOGIN`, `LOGOUT`, `REGISTRATION_REQUESTED`等）も同一画面で扱う必要があるため、UNIT-05〜08のような「接続選択画面→履歴一覧画面」の2画面構成は採らない。単一の監査ログ一覧画面とし、対象接続は他の絞込条件と並列の1つとして扱う。

## BR-AUDITVIEW-03: アクセス制御は管理者専用パスパターンによるエンドポイント全体の遮断（Q2=A）

本ユニットのAPIは`/api/admin/audit-log/**`に配置し、`SecurityConfig`の既存ルール`.requestMatchers("/api/admin/**").hasRole("ADMIN")`（UNIT-02の`AdminUserController`、UNIT-04の`GroupController`/`PermissionController`と同じ方式）でカバーする。一般ユーザのリクエストはController到達前にSpring Securityのフィルタ層で403 Forbiddenとなる。

**UNIT-08との違い**: UNIT-08（クエリ履歴）は一般ユーザも利用可能で、Service層・Controller層でのロールに応じたデータ絞込（フェイルクローズ）が必要だった。本ユニットは管理者専用であり、ロールベースのデータ絞込ロジックは不要（アクセスできた時点で全件が閲覧対象）。エンドポイント単位のアクセス遮断という、UNIT-08とは異なる種類の制御方式を採用する。

## BR-AUDITVIEW-04: ページングはSpring Data JPA標準のPageable/Pageを使用（Q3=A）

UNIT-08で確立した`Pageable`/`Page`機構、および動的絞込の`Specification` APIパターンをそのまま踏襲する。デフォルトソート順は`occurredAt`降順とする。

## BR-AUDITVIEW-05: 絞込条件は5種、対象リソースのテキスト検索は含めない（Q4=A）

提供する絞込条件は、発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータスの5種とする。いずれもrequirements.md §6.2の記録項目に対応する。`targetResource`はイベント種別ごとに意味が異なる自由記述に近いフィールドであり、部分一致検索は本ユニットのスコープに含めない。

## BR-AUDITVIEW-06: 絞込条件はすべてAND結合（UNIT-08 BR-QUERYHISTORY-09と同じ方針）

発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータスの各絞込条件は、指定されたものすべてがAND条件として組み合わされる。OR条件やグルーピングはサポートしない。

## BR-AUDITVIEW-07: ユーザ・接続の表示名解決はUNIT-08と同じ一括解決方式（Q5=A）

一覧取得時、`userId`・`connectionId`の非null値をそれぞれユニークに集約し、`UserRepository`/`RdbmsConnectionRepository`の`findAllById`で一括取得する（N+1回避、UNIT-08と同じ方式）。対象ユーザ・対象接続が既に削除されている場合は、それぞれ「(不明なユーザ)」「(削除済み接続)」等のプレースホルダーで表示する。監査ログの不変性（BR-AUDITVIEW-08）により、表示名解決の成否に関わらずログエントリ自体は常に閲覧可能とする。

## BR-AUDITVIEW-08: 監査ログの閲覧可否はアクセス権の再判定を行わない

`AuditLogEntry`は記録時点の事実として不変に扱う（UNIT-02で確立済みの設計、外部キー制約を意図的に持たない）。対象ユーザ・対象接続が削除されていても、監査ログエントリ自体は常に閲覧可能とする。UNIT-08のBR-QUERYHISTORY-04と同じ「記録の不変性」の考え方を踏襲する。

## BR-AUDITVIEW-09: イベント種別の絞込UIはフラットな一覧（Q6=A）

28種類（本計画作成時点、UNIT-02〜08で追加分含む）の`AuditEventType`は、カテゴリ分けせず単一のSelectの選択肢としてフラットに提示する。カテゴリ分け（認証／管理操作／データアクセス）によるUIの複雑化を避け、シンプルさを優先する。

## BR-AUDITVIEW-10: `connection_id`に複合インデックスを新規追加（Q7=A）

既存の`audit_log_entry`テーブルには`occurred_at`・`event_type`・`user_id`の単独インデックスがあるが、`connection_id`にはインデックスがない。本ユニットの主要な絞込軸の1つが対象接続であるため、新規マイグレーションで`(connection_id, occurred_at)`の複合インデックスを追加する（UNIT-08で`query_execution_record`に対して行った是正と同じ対応）。

## BR-AUDITVIEW-11: 他画面への遷移導線は設けない（Q8=A）

監査ログ一覧は事後的な確認を目的とする純粋な閲覧・絞込機能に留め、対象ユーザ・対象接続等への画面遷移導線は設けない。STORY-9.1の受け入れ基準にも画面遷移の要件はない。

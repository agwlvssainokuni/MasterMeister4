# UNIT-02（ユーザ登録・認証）ドメインエンティティ

`unit-02-functional-design-plan.md`の回答（Q1〜Q9）に基づく、内部DB（JPA）に永続化するエンティティ定義。

## User（`cherry.mastermeister.registration`）

| フィールド | 型 | 説明 |
|---|---|---|
| id | UserId (Long) | PK |
| email | String | 一意制約。ログインID |
| passwordHash | String | bcryptハッシュ（Q1=A） |
| role | Role (enum: `USER`, `ADMIN`) | Q7=A |
| status | UserStatus (enum: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`) | パスワード設定完了時に`PENDING_APPROVAL`で作成 |
| createdAt | Instant | 登録完了日時 |
| approvedAt | Instant（nullable） | 承認/却下日時 |
| approvedBy | UserId（nullable） | 承認/却下した管理者 |

**状態遷移**: (登録開始・完了前は`User`行を作らない) → `completeRegistration()`実行時に`PENDING_APPROVAL`で作成 → `approveUser()`で`APPROVED` / `rejectUser()`で`REJECTED`（終端）。`APPROVED`のみログイン可能（FR-1.7）。

## RegistrationToken（`cherry.mastermeister.registration`）

| フィールド | 型 | 説明 |
|---|---|---|
| id | Long | PK |
| email | String | 登録申請メールアドレス |
| tokenHash | String | トークンのSHA-256ハッシュ（平文はメール送信時のみ使用し保存しない。リフレッシュトークンと同じ方針をQ4の考え方に準拠して適用） |
| expiresAt | Instant | 既定3時間後（`mm.app.user-registration.token-expiry`） |
| used | boolean | 単回使用。使用済みなら再利用不可 |
| createdAt | Instant | |

`completeRegistration(token, password)`実行時: トークンハッシュで照合 → 有効期限・未使用を検証 → `User`作成 → `used=true`に更新。

## RefreshToken（`cherry.mastermeister.auth`）

| フィールド | 型 | 説明 |
|---|---|---|
| id | Long | PK |
| userId | UserId | 所有ユーザ |
| tokenFamilyId | TokenFamilyId (UUID) | ローテーションチェーンの識別子。ログイン時に新規発行、リフレッシュ時は引き継ぐ |
| tokenHash | String | SHA-256ハッシュ（Q4=A、平文はレスポンスとしてのみ返却し保存しない） |
| issuedAt | Instant | |
| expiresAt | Instant | 既定24時間（`mm.app.jwt.refresh-token-expiry`） |
| revoked | boolean | |
| revokedAt | Instant（nullable） | |
| revokedReason | RevokeReason（enum: `LOGOUT`, `ROTATED`, `REUSE_DETECTED`）（nullable） | |

**再利用検知**: `revoked=true`かつ`revokedReason=ROTATED`のトークンが再度提示された場合、同一`tokenFamilyId`の全レコードを`revoked=true, revokedReason=REUSE_DETECTED`に更新する（FR-3.4）。

## LoginLockState（`cherry.mastermeister.auth`）

Q5=A（DB永続化）に基づく、ログイン試行制限用の状態。

| フィールド | 型 | 説明 |
|---|---|---|
| email | String | PK |
| failureCount | int | 直近の連続失敗回数 |
| lockedUntil | Instant（nullable） | この日時までロック中 |
| lastFailureAt | Instant（nullable） | |

**既定値（Q6=A）**: 5回連続失敗で15分間ロック。ログイン成功時に`failureCount=0, lockedUntil=null`にリセット。

## AuditLogEntry（`cherry.mastermeister.audit`）

`unit-of-work.md`のとおり、UNIT-02では**記録機能のみ**構築する（閲覧UIはUNIT-09）。

| フィールド | 型 | 説明 |
|---|---|---|
| id | Long | PK |
| timestamp | Instant | ISO 8601（§6.2） |
| eventType | AuditEventType (enum) | 本ユニットでは6種別（Q9=A、下記） |
| userId | UserId（nullable） | ログイン失敗等、対象ユーザが特定できない場合はnull |
| targetConnectionId | ConnectionId（nullable） | 本ユニットのイベントでは常にnull（対象RDBMS接続に関連しないため） |
| operationType | String | イベント種別に対応する操作名 |
| targetResource | String | 対象（ユーザのメールアドレス等） |
| resultStatus | ResultStatus (enum: `SUCCESS`, `FAILURE`) | |

**AuditEventType（本ユニット分、Q9=A）**: `REGISTRATION_REQUESTED`, `USER_APPROVED`, `USER_REJECTED`, `LOGIN_SUCCESS`, `LOGOUT`, `LOGIN_FAILURE`

パスワード・トークン等の機微情報はいかなるフィールドにも記録しない（SECURITY-03準拠、requirements.md §6.3）。

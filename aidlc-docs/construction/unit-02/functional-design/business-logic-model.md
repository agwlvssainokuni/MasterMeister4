# UNIT-02（ユーザ登録・認証）業務ロジックモデル

主要フローと、担当コンポーネント（component-methods.md）の対応関係。

## フロー1: メールアドレス登録開始（STORY-1.1）

1. `POST /api/registrations` に email を送信
2. `UserRegistrationService.startRegistration(email)`
   - 既存の`RegistrationToken`（未使用・未失効）があれば無効化（再申請時の重複防止）
   - 新規トークンを生成し`RegistrationToken`としてハッシュ化保存
   - `EmailNotificationService.sendRegistrationConfirmation()`で確認メール送信（既登録メールアドレスの場合は内部的に送信をスキップするが、APIレスポンスは同一。BR-1）
   - `AuditEventPublisher.publish(REGISTRATION_REQUESTED)`
3. 常に同一の汎用成功レスポンスを返す

## フロー2: パスワード設定・登録完了（STORY-1.2）

1. `POST /api/registrations/complete` に token, password を送信
2. `UserRegistrationService.completeRegistration(token, password)`
   - トークンのハッシュ照合、有効期限・未使用チェック（BR-2, BR-3）
   - パスワードの最小文字数チェック（BR-4）
   - Have I Been Pwned API照合（BR-5）
   - bcryptでハッシュ化（BR-6）
   - `User`を`PENDING_APPROVAL`状態で作成（BR-7）
   - `RegistrationToken.used = true`に更新
3. 登録完了レスポンスを返す（自動ログインはしない。承認後に別途ログインが必要）

## フロー3: 管理者承認・却下（STORY-1.3）

1. `GET /api/admin/users?status=PENDING_APPROVAL` で一覧取得（`UserRegistrationService.listPendingUsers()`）
2. `POST /api/admin/users/{userId}/approve` または `/reject`
   - `UserRegistrationService.approveUser()` / `rejectUser()`
   - `User.status`を更新、`approvedAt`/`approvedBy`を記録
   - `EmailNotificationService.sendApprovalResult()`
   - `AuditEventPublisher.publish(USER_APPROVED または USER_REJECTED)`（BR-8）

## フロー4: 初期管理者ブートストラップ（STORY-1.4）

1. アプリ起動時（`ApplicationRunner`）、`AdminBootstrapService.bootstrapInitialAdmin()`が実行される
2. 環境変数の存在チェック（BR-10、未設定なら何もしない）
3. 既存ユーザチェック（冪等性）
4. `UserRegistrationService.createApprovedAccount(email, password, ADMIN)`を呼び出し、通常の登録・承認フローを経ずに`APPROVED`状態のアカウントを作成

## フロー5: ログイン（STORY-3.1）

1. `POST /api/auth/login` に email, password を送信
2. `LoginAttemptGuard.isLocked(email)`チェック（BR-17）→ ロック中なら汎用エラー（BR-18）＋`AuditEventPublisher.publish(LOGIN_FAILURE)`
3. `AuthenticationService.login(email, password)`
   - `User`存在確認、`status=APPROVED`確認、パスワード照合（bcrypt）
   - いずれかに失敗: `LoginAttemptGuard.recordFailure(email)` → 汎用エラー（BR-18）＋監査ログ（`LOGIN_FAILURE`）
   - 成功: `LoginAttemptGuard.reset(email)`、アクセストークン（JWT）発行、`RefreshTokenService`で新規`tokenFamilyId`を発行しリフレッシュトークンを保存
   - `AuditEventPublisher.publish(LOGIN_SUCCESS)`
4. `TokenPair`（アクセストークン＋リフレッシュトークン）を返す

## フロー6: トークンリフレッシュ（STORY-3.1, 3.2）

1. `POST /api/auth/refresh` にリフレッシュトークンを送信
2. `RefreshTokenService.refresh(refreshToken)`
   - トークンハッシュで`RefreshToken`を照合
   - `revoked=true かつ revokedReason=ROTATED`（再利用）を検知した場合: `detectReuse()`によりファミリ全体を失効（BR-15）→ エラー（再ログイン要求）
   - 有効なトークンの場合: 旧トークンを`ROTATED`として無効化、新しいアクセストークン・リフレッシュトークンを同一ファミリで発行（BR-14）

## フロー7: ログアウト（STORY-3.1）

1. `POST /api/auth/logout` にリフレッシュトークンを送信
2. `AuthenticationService.logout(refreshToken)` → 該当トークンを`revoked=true, revokedReason=LOGOUT`（BR-19）
3. `AuditEventPublisher.publish(LOGOUT)`

## 横断的関心事: 監査ログ記録（COMP-18記録機能, COMP-19）

上記フロー1〜3, 5, 7で発行される`AuditEvent`は、`AuditEventPublisher.publish()`（Spring `ApplicationEventPublisher`のラッパー）経由で発行され、`AuditLogService.onAuditEvent()`が別トランザクションで`AuditLogEntry`として永続化する（BR-21、`application-design.md`で確定済みの同期・別トランザクション方式）。

# UNIT-02 ユーザ登録・認証 - Business Logic Model

`unit-02-functional-design-plan.md`の回答（Q1=B, Q2=A, Q3=A, Q4=A, Q5=A, Q6=A, Q7=C, Q8=A, Q9=A, Q10=A, Q11=A、および追加事項：メールテンプレート方式）に基づく。技術非依存（Spring等の実装詳細はNFR Design／Code Generationで扱う）を原則としつつ、Application Design段階で既に確定している横断アーキテクチャ方針（イベント駆動の監査ログ連携）はそのまま踏襲する。

対応コンポーネント: COMP-01（UserRegistrationService）, COMP-02（AdminBootstrapService）, COMP-03（AuthenticationService）, COMP-04（RefreshTokenService）, COMP-05（LoginAttemptGuard）, COMP-06（EmailNotificationService）, COMP-18（記録機能のみ）, COMP-19（AuditEventPublisher）

---

## 1. ユーザ登録フロー（2段階、FR-1.1〜FR-1.3, FR-1.8〜FR-1.13）

### 1.1 Step 1: メールアドレス送信（登録開始）
1. ユーザが登録画面でメールアドレスと、その時点で画面上選択している言語（UI言語）を送信する（`POST /api/registrations`）
2. UserRegistrationServiceは、同一メールアドレスがいずれかのステータス（`PENDING`／`APPROVED`／`REJECTED`／`DISABLED`）で既に存在するかを確認する（BR-REG-06訂正版。ステータスによる例外は設けない）
   - 存在有無に関わらず、APIレスポンスは常に同一の内容を返す（BR-REG-04、メールアドレス列挙攻撃対策）
3. 新規メールアドレスの場合のみ、登録用トークンを新規発行する（COMP-01内部でRegistrationTokenの生成・保存を行う。トークン生成・ハッシュ化ロジックはRefreshTokenServiceと共用のユーティリティを用いる。BR-REG-02）
4. EmailNotificationServiceへ登録確認メール送信を依頼する。この時点ではUserレコードが未作成のため、メール生成言語にはリクエスト時点でユーザが画面上選択していた言語をそのまま用いる（BR-MAIL-01）
5. AuditEventPublisher経由で「アカウント登録申請」イベントを発行する

### 1.2 Step 2: パスワード設定・登録完了
1. ユーザがメール内リンク（トークン付きURL）からパスワード設定画面へ遷移し、パスワード・氏名・言語設定を入力して送信する（`POST /api/registrations/{token}/complete`）
2. UserRegistrationServiceはトークンの有効性（存在、未使用、未失効）を検証する（BR-REG-02）
3. パスワードポリシーを適用する: 最小文字数チェック（BR-PWD-01）、既知漏洩パスワードチェック（BR-PWD-02、HIBP等の外部API利用、フェイルオープン）
4. 検証を通過した場合、Userレコードを`PENDING`ステータスで作成する。氏名・言語設定（`preferredLanguage`）をこの時点で保存する（BR-REG-05）。パスワードは適応型ハッシュアルゴリズムでハッシュ化して保存する（BR-PWD-03）
5. 使用済みの登録トークンを無効化する
6. AuditEventPublisher経由で「アカウント登録完了」イベント（`REGISTRATION_COMPLETED`）を発行する。1.1の「アカウント登録申請」（`REGISTRATION_REQUESTED`）とは別タイミング・別アクターの操作のため、別イベント種別として記録する（BR-AUDIT-02）

---

## 2. 管理者承認ワークフロー（FR-1.4〜FR-1.7）

1. 「ユーザ管理」画面（frontend-components.md §4参照。初期表示は`PENDING`フィルタ）が、`PENDING`ステータスのユーザ一覧を取得する（`GET /api/admin/users?status=PENDING`）
2. 管理者が個別ユーザに対し承認または却下を選択する
3. **承認時**（`POST /api/admin/users/{id}/approve`）: UserステータスをPENDING→APPROVEDに遷移させる。ログイン可能となる
4. **却下時**（`POST /api/admin/users/{id}/reject`）: UserステータスをPENDING→REJECTEDに遷移させる。同一メールアドレスでの新規登録（再登録）は許可しない（BR-REG-06訂正版）
5. いずれの場合も、EmailNotificationServiceへ承認結果通知メール送信を依頼する。この時点ではUserレコードが存在するため、Userの`preferredLanguage`（登録時に保存済み）を用いて言語を決定する（BR-MAIL-01）
6. AuditEventPublisher経由で「管理者によるアカウント承認／却下」イベントを発行する

### 2.1 却下の取り消し（訂正版、BR-REG-01）
1. 同じ「ユーザ管理」画面で、管理者がフィルタを`REJECTED`に切り替え、対象ユーザに対し承認を選択する
2. 上記3.と同一の`POST /api/admin/users/{id}/approve`エンドポイントを呼び出す（遷移元がPENDINGかREJECTEDかで処理を分けない）。UserステータスをREJECTED→APPROVEDに遷移させる
3. 上記5.・6.と同様、承認結果通知メールを送信し、`USER_APPROVED`イベントを発行する（却下の取り消しを示す区別は行わない。承認という行為として一貫させる）

---

## 3. 管理者によるアカウント無効化・再有効化（Q1=B、BR-REG-01）

1. 同じ「ユーザ管理」画面（承認待ちに限らず全ステータスのユーザを対象とする。§2の画面と同一。frontend-components.md §4参照。レビュー指摘を受け1画面に統合）から、管理者が対象ユーザに対し無効化または再有効化を選択する
2. **無効化時**（`POST /api/admin/users/{id}/disable`）: `APPROVED`ステータスのユーザのみを対象とし、UserステータスをAPPROVED→DISABLEDに遷移させる。以降、当該ユーザはログイン不可となる。既に発行済みのリフレッシュトークンも無効化する（不正利用防止のため、無効化時点でセッションを即座に無効にする）
3. **再有効化時**（`POST /api/admin/users/{id}/enable`）: `DISABLED`ステータスのユーザのみを対象とし、UserステータスをDISABLED→APPROVEDに遷移させる。ログイン再開可能となる
4. AuditEventPublisher経由で「管理者によるアカウント無効化／再有効化」イベント（`USER_DISABLED` / `USER_ENABLED`）を発行する（BR-AUDIT-02）
5. 無効化・再有効化時のメール通知は行わない（承認・却下時のFR-1.6のような明示的な通知要件がrequirements.mdにないため。必要であれば後日追加検討）

---

## 4. 初期管理者ブートストラップ（FR-1.14）

1. アプリ起動時、AdminBootstrapServiceが環境変数（`mm.app.admin.bootstrap.email` / `mm.app.admin.bootstrap.password`）の設定有無を確認する
2. 設定されている場合、UserRegistrationService.`createApprovedAccount()`を呼び出し、通常の登録フロー（トークン発行・承認待ち）を経ずに`APPROVED`ステータスの管理者アカウントを直接作成する
3. 既に同一メールアドレスのアカウントが存在する場合は何もしない（冪等）

---

## 5. ログイン・ログアウト（FR-3.1〜FR-3.2, FR-3.5〜FR-3.6）

### 5.1 ログイン
1. ユーザがメールアドレス・パスワードを送信する（`POST /api/auth/login`）
2. LoginAttemptGuardが当該メールアドレスのロック状態を確認する。ロック中の場合は認証を行わずエラーを返す（BR-LOGIN-01）
3. AuthenticationServiceが認証情報を検証する（Userの存在確認、`APPROVED`ステータスであること（BR-REG-03）、パスワードハッシュの照合）
4. 検証失敗時: LoginAttemptGuard.recordFailure()を呼び出し、失敗回数を加算する。AuditEventPublisher経由で「ログイン失敗」イベントを発行する
5. 検証成功時: LoginAttemptGuard.reset()で失敗カウンタをリセットする。アクセストークン（JWT）とリフレッシュトークンのペアを発行する（§6参照）。AuditEventPublisher経由で「ログイン」イベントを発行する
6. 同一ユーザの複数端末・複数ブラウザからの同時ログインは制限しない（FR-3.6）

### 5.2 ログアウト
1. クライアントがリフレッシュトークンを送信する（`POST /api/auth/logout`）
2. RefreshTokenServiceが該当トークンを無効化する
3. AuditEventPublisher経由で「ログアウト」イベントを発行する

---

## 6. リフレッシュトークンのローテーション・再利用検知（FR-3.3〜FR-3.4）

1. ログイン成功時、新規のトークンファミリID を採番し、リフレッシュトークンを発行・ハッシュ化して保存する
2. リフレッシュ要求時（`POST /api/auth/refresh`）:
   - RefreshTokenService.detectReuse()で、提示されたトークンが既に「使用済み（無効化済み）」であるかを判定する
   - **再利用検知時（BR-TOKEN-02）**: トークン窃取・再送の兆候とみなし、同一トークンファミリIDに属する全トークンを一括失効させる（revokeFamily()）。以降、当該ファミリでのリフレッシュは一切拒否される。AuditEventPublisher経由で該当イベントを発行する
   - **正常時**: 提示されたトークンを無効化し、同一トークンファミリIDを引き継いだ新しいリフレッシュトークンを発行する（ローテーション、BR-TOKEN-01）。新しいアクセストークンも同時に発行する

---

## 7. ログイン試行制限（FR-3.7）

1. LoginAttemptGuardは、メールアドレス単位で失敗回数を保持する
2. 失敗回数が設定された閾値（デフォルト5回）に達した場合、当該メールアドレスを設定された時間（デフォルト15分）ロックする（BR-LOGIN-01）
3. ロック時間経過後、自動的にロックが解除される（次回ログイン試行時にロック期限切れを判定し、カウンタをリセットする）
4. IPアドレス単位の制限は行わない（requirements.md §6.2、IPアドレス記録は未実装のため）

---

## 8. 監査ログ記録基盤（横断、§6.1〜6.3）

1. 各業務コンポーネント（UserRegistrationService, AuthenticationService, RefreshTokenService等）は、監査対象イベントの発生時にAuditEventPublisher.publish()を呼び出す
2. AuditEventPublisherは、イベント（種別・ユーザID・対象接続ID・操作種別・対象リソース・結果ステータスを含むAuditEvent）を発行する
3. AuditLogServiceがイベントを受信し、業務トランザクションとは独立した別トランザクションでAuditLogEntryとして永続化する（BR-AUDIT-01）。これにより、業務処理がロールバックされても監査ログの記録漏れが起きず、逆に監査ログの記録失敗が業務処理をロールバックさせることもない
4. 本ユニットで発行対象とするイベント種別: ログイン、ログアウト、ログイン失敗、アカウント登録申請、アカウント登録完了、管理者によるアカウント承認／却下、管理者によるアカウント無効化／再有効化（§6.1「認証イベント」「管理操作」の該当部分、BR-AUDIT-02）。他の管理操作・データアクセスイベントは後続ユニット（UNIT-03〜UNIT-08）が同じ基盤を用いて発行する

---

## 9. メール通知の生成（登録確認・承認結果、FR-1.2, FR-1.6）

1. EmailNotificationServiceは、宛先・イベント種別（登録確認／承認結果）・言語・テンプレートに埋め込む変数（トークン付きURL、氏名等）を受け取る
2. テンプレートエンジン（Mustache形式、ユーザー提供の自作実装）を用いて、言語別のテンプレートファイルからメール本文を生成する（BR-MAIL-02）
3. 生成した本文をSMTP経由で送信する（開発環境ではMailPitコンテナ、本番は環境変数によるSMTP設定。NFR-3.1〜3.2、既存決定を踏襲）

---

## Testable Properties（PBT-01対応）

UNIT-02の業務ロジックのうち、以下はPBT拡張の観点で識別されたが、いずれも組み合わせ数が少なく複雑な不変条件を持たないため、PBT-01（Functional Design段階での識別）としては**No PBT properties identified**と判断する。

- 権限判定のような多階層・多次元の組み合わせロジックは本ユニットには存在しない（該当はUNIT-04のEffectivePermissionResolver）
- リフレッシュトークンのローテーション・再利用検知は状態遷移ロジックだが、状態数・遷移パターンが限定的（発行済み→使用済み→[正常/再利用検知]の単純な有限状態機械）であり、ユニットテストでの網羅で十分と判断する

# UNIT-02（ユーザ登録・認証）フロントエンドコンポーネント

Q10=A（4画面）に基づく画面構成。全画面、UNIT-01の`design-system`コンポーネントを利用する。配置は`frontend/src/registration/`（画面1・2）、`frontend/src/auth/`（画面3）、`frontend/src/admin/`（画面4）を想定（`unit-of-work.md`のフロントエンド構成方針、詳細はCode Generation段階で確定）。

なお、管理者ダッシュボードの一覧表示には、UNIT-01で見送った高機能な`Table`コンポーネント（ソート・選択・セル状態）は使用しない。本ユニットで必要なのは承認待ちユーザの単純な一覧のみのため、素の`<table>`要素 + `Badge`/`Button`で実装する（過剰実装を避ける）。

## 画面1: メールアドレス登録画面（`RegistrationStartPage`）

- **対応ストーリー**: STORY-1.1
- **構成**: `FormField`（label="メールアドレス"）+ `TextField`（type="email"）+ `Button`（送信）
- **状態**: `email`（入力値）, `submitting`（送信中）, `submitted`（送信済み。成功後はフォームを隠しBR-1の汎用メッセージを`Alert tone="info"`で表示）
- **バリデーション**: HTML5標準のemail形式チェックのみ（サーバ側がBR-1により常に同一レスポンスを返すため、詳細なクライアント側検証は行わない）
- **API連携**: `POST /api/registrations`
- **`data-testid`**: `registration-start-email-input`, `registration-start-submit-button`

## 画面2: パスワード設定画面（`RegistrationCompletePage`）

- **対応ストーリー**: STORY-1.2
- **URL**: `/register/complete?token=xxx`（Q8=A、クエリパラメータ）
- **構成**: `FormField`（label="パスワード"）+ `PasswordInput` + `FormField`（確認用パスワード）+ `PasswordInput` + `Button`（登録完了）
- **状態**: `password`, `passwordConfirm`, `submitting`, `error`（サーバエラー: 期限切れ／使用済みトークン、パスワードポリシー違反、漏洩パスワード該当）
- **バリデーション**: クライアント側で最小8文字・確認一致をチェック（`FormField`の`error`プロップでフィードバック）。漏洩パスワードチェック（BR-5）はサーバ側のみで実施（クライアントに外部API呼び出しをさせない）
- **API連携**: `POST /api/registrations/complete`（`token`はURLから取得しリクエストボディに含める）
- **`data-testid`**: `registration-complete-password-input`, `registration-complete-password-confirm-input`, `registration-complete-submit-button`

## 画面3: ログイン画面（`LoginPage`）

- **対応ストーリー**: STORY-3.1, 3.3
- **構成**: `FormField`（label="メールアドレス"）+ `TextField` + `FormField`（label="パスワード"）+ `PasswordInput` + `Checkbox`（不要、Q10で対象外と確認済みのため「ログイン状態を保持する」等のオプションはこのユニットでは実装しない） + `Button`（ログイン）
- **状態**: `email`, `password`, `submitting`, `error`（BR-18の汎用メッセージを`Alert tone="danger"`で表示）
- **バリデーション**: 必須入力のみ（詳細な検証はサーバに委ねる。BR-18の汎用エラー方針と整合）
- **API連携**: `POST /api/auth/login`。成功時はアクセストークン・リフレッシュトークンをブラウザに保持（保存方式はNFR設計段階で確定）し、以降の画面へ遷移
- **`data-testid`**: `login-email-input`, `login-password-input`, `login-submit-button`

## 画面4: 管理者向けユーザ承認ダッシュボード（`AdminUserApprovalPage`）

- **対応ストーリー**: STORY-1.3
- **構成**:
  - 承認待ちユーザが0件の場合: `EmptyState`
  - 1件以上の場合: 素の`<table>`（列: メールアドレス、登録日時、`Badge tone="warning"`（状態表示: 承認待ち）、操作列に`Button variant="primary"`（承認）+ `Button variant="danger"`（却下））
  - 各行の承認/却下ボタン押下時、確認のための追加UIは本ユニットのスコープでは設けない（誤操作防止のダイアログ確認は、UNIT-01で見送った`Modal`/`ConfirmDialog`が前提となるため、導入するタイミングでの再検討事項とする）
- **状態**: `pendingUsers`（一覧）, `loading`（`Spinner`表示）, `processingUserId`（承認/却下処理中の行）
- **API連携**: `GET /api/admin/users?status=PENDING_APPROVAL`、`POST /api/admin/users/{userId}/approve`、`POST /api/admin/users/{userId}/reject`
- **`data-testid`**: `admin-user-approval-table`, `admin-user-approval-approve-button-{userId}`, `admin-user-approval-reject-button-{userId}`

## 認可・ルーティングに関する注記

このユニットの時点ではルーティングライブラリを未導入（UNIT-01の判断を継続）。画面遷移方式（クエリパラメータ判定を拡張するか、ルーティングライブラリを導入するか）はCode Generation計画段階で判断する。管理者専用画面4へのアクセス制御（一般ユーザによる直接アクセス防止）もCode Generation段階で具体化する。

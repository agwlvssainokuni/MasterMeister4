# UNIT-02 ユーザ登録・認証 - Frontend Components

`unit-02-functional-design-plan.md`のQ9=A（UNIT-01モックをベースに新規実装、コード流用なし。追加要望: 氏名・言語設定の入力項目を追加）に基づく。UNIT-01で構築した`design-system`コンポーネント（`AuthCard`, `TextInput`, `Button`, `Alert`, `PageHeader`, `DataTable`, `ConfirmDialog`, `EmptyState`, `Dropdown`等）とレイアウト（`PublicLayout`, `AppShell`）を利用し、`frontend/src/pages/`配下に実データ連携版として新規実装する。`/mock/login`・`/mock/register`・`/mock/dashboard`のモックコードは流用しない（デザインのみ参考）。

---

## 1. ログイン画面（`/login`、`PublicLayout`）

### コンポーネント構造
```
LoginPage (PublicLayout)
└ AuthCard
  ├ TextInput（email、type=email、required）
  ├ TextInput（password、type=password、required）
  ├ Alert（tone=danger、認証失敗・ロック中エラー時のみ表示）
  └ Button（type=submit、「ログイン」、送信中はローディング状態）
```

### State
- `email: string`, `password: string`
- `submitting: boolean`
- `errorMessage: string | null`（BR-REG-04によりメールアドレス不存在・パスワード不一致は同一メッセージ。ロック中エラーは別メッセージ）

### バリデーション
- クライアント側: email形式チェック、両フィールド必須（送信前の基本チェックのみ。詳細な認証判定はすべてサーバ側）

### API連携
- `POST /api/auth/login` `{ email, password }` → 成功時`{ accessToken, refreshToken }`。成功時はトークンを保存し、AppShell配下（例: `/dashboard`）へ遷移
- エラーレスポンス（BR-API-01形式）の`code`に応じて表示メッセージを出し分ける（例: `AUTH_INVALID_CREDENTIALS`→通常の認証失敗メッセージ、`AUTH_ACCOUNT_LOCKED`→ロック中メッセージ、`AUTH_ACCOUNT_NOT_APPROVED`→承認待ち/却下済みメッセージ）

---

## 2. ユーザ登録画面 Step 1（メールアドレス送信、`/register`、`PublicLayout`）

### コンポーネント構造
```
RegisterStep1Page (PublicLayout)
└ AuthCard
  ├ TextInput（email、type=email、required）
  ├ Alert（tone=success、送信完了後に表示。「確認メールを送信しました」）
  ├ Alert（tone=danger、送信失敗時のみ表示）
  └ Button（type=submit、「送信」、送信完了後はdisabled）
```

### State
- `email: string`, `submitting: boolean`, `submitted: boolean`, `errorMessage: string | null`

### API連携
- `POST /api/registrations` `{ email, language }`（`language`はアプリルート付近の`LanguageSwitcher`で選択中の言語。BR-MAIL-01）
- 常に同一の成功レスポンスを返す仕様（BR-REG-04）のため、UI側も既存メールアドレスか否かで表示を分岐しない。送信後は一律`submitted: true`として完了表示に切り替える

---

## 3. ユーザ登録画面 Step 2（パスワード設定、`/register/complete?token=...`、`PublicLayout`）

メール内リンクからトークン付きURLで遷移する。

### コンポーネント構造
```
RegisterStep2Page (PublicLayout)
└ AuthCard
  ├ TextInput（fullName、氏名、required） … Q9追加要望
  ├ Select（preferredLanguage、言語設定、`ja`/`en`、required） … Q9追加要望
  ├ TextInput（password、type=password、required）
  ├ TextInput（passwordConfirm、type=password、required、password一致チェック）
  ├ Alert（tone=danger、トークン期限切れ・無効・パスワードポリシー違反時に表示）
  └ Button（type=submit、「登録完了」）
```

### State
- `fullName: string`, `preferredLanguage: 'ja' | 'en'`, `password: string`, `passwordConfirm: string`
- `submitting: boolean`, `errorMessage: string | null`
- 画面初期表示時、URLからトークンを取得できない場合は即座にエラー状態（「無効なリンクです」）とする

### バリデーション
- クライアント側: 全項目必須、`password === passwordConfirm`、パスワード最小文字数（BR-PWD-01、サーバ側の値をNFR Design段階で共通定数化するか検討）
- サーバ側: トークン有効性（BR-REG-02）、パスワードポリシー（BR-PWD-01〜02、漏洩チェック含む）

### API連携
- `POST /api/registrations/{token}/complete` `{ fullName, preferredLanguage, password }` → 成功時、登録完了メッセージを表示し`/login`へ誘導（自動ログインは行わない。承認待ちのため）
- エラーレスポンスの`code`（例: `REGISTRATION_TOKEN_EXPIRED`, `REGISTRATION_TOKEN_INVALID`, `PASSWORD_TOO_SHORT`, `PASSWORD_COMPROMISED`）に応じたメッセージを表示

---

## 4. 管理者ダッシュボード（承認待ちユーザ一覧、`/dashboard`、`AppShell`）

UNIT-01の`/mock/dashboard`のデザインを踏襲し、実データ連携版として実装する。

### コンポーネント構造
```
AdminDashboardPage (AppShell)
└ PageHeader（タイトル「承認待ちユーザ」）
  ├ DataTable（列: 氏名, メールアドレス, 登録日時, アクション）
  │  └ 行ごとに Button（「承認」）, Button（「却下」）
  ├ ConfirmDialog（承認／却下の確認。誤操作防止）
  ├ EmptyState（承認待ちユーザが0件の場合、「承認待ちユーザはいません」）
  └ Alert（tone=danger、一覧取得・承認・却下失敗時）
```

### State
- `pendingUsers: PendingUser[]`, `loading: boolean`, `errorMessage: string | null`
- `confirmTarget: { userId: string, action: 'approve' | 'reject' } | null`（ConfirmDialog表示制御）

### API連携
- `GET /api/admin/users?status=PENDING` → 一覧取得（画面表示時、承認／却下操作後に再取得）
- `POST /api/admin/users/{id}/approve` → 確認ダイアログでの最終確認後に実行
- `POST /api/admin/users/{id}/reject` → 同上
- 管理者ロール以外でのアクセス時は403エラーとなる想定（アクセス制御自体はUNIT-02のJWT認証・ロールチェックで実現。詳細な権限体系はUNIT-04）

---

## 5. AppShell Headerのログアウト導線（実装）

UNIT-01では「プレースホルダー」だったAppShell Headerのログアウト導線を、本ユニットで実装する。

### 変更点
```
Header (AppShell内)
└ HeaderControl
  └ Dropdown（ユーザメニュー、ログイン中ユーザの氏名を表示）
     └ MenuItem「ログアウト」
```

### 挙動
- 「ログアウト」選択時、`POST /api/auth/logout`を呼び出し、保存済みトークンをクリアして`/login`へ遷移する
- アクセストークンの有効期限切れ検知（APIレスポンス401等）時は、リフレッシュトークンでの自動再発行（`POST /api/auth/refresh`）を試み、失敗した場合は自動的にログアウト状態として`/login`へ遷移する

---

## 6. 認証状態管理（画面横断）

- アクセストークン・リフレッシュトークンの保持方式、AppShell配下ルートの認証ガード（未ログイン時は`/login`へリダイレクト）等、フロントエンド全体に関わる実装方式（保存先: メモリ/localStorage等の選定含む）は、セキュリティ上重要な判断のためNFR Design段階で確定する
- 本ドキュメントでは、各画面が「ログイン状態」を前提とするか（`AppShell`配下）／前提としないか（`PublicLayout`配下）の区分のみを定義する

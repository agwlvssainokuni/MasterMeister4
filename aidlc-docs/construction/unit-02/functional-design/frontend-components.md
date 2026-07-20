# UNIT-02 ユーザ登録・認証 - Frontend Components

`unit-02-functional-design-plan.md`のQ9=A（UNIT-01モックをベースに新規実装、コード流用なし。追加要望: 氏名・言語設定の入力項目を追加）に基づく。UNIT-01で構築した`design-system`コンポーネント（`AuthCard`, `TextInput`, `Button`, `Alert`, `PageHeader`, `DataTable`, `ConfirmDialog`, `EmptyState`, `Card`, `Dropdown`等）とレイアウト（`PublicLayout`, `AppShell`）を利用し、`frontend/src/pages/`配下に実データ連携版として新規実装する。`/mock/login`・`/mock/register`・`/mock/dashboard`のモックコードは流用しない（デザインのみ参考）。ログイン後のトップ画面（§5）はUNIT-01のレビュー指摘を受けて新設したものであり、対応するモックは存在しない。

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
- `POST /api/auth/login` `{ email, password }` → 成功時`{ accessToken, refreshToken }`。成功時はトークンを保存し、トップ画面（`/`、§5参照）へ遷移
- エラーレスポンス（BR-API-01形式）の`code`に応じて表示メッセージを出し分ける: `AUTH_INVALID_CREDENTIALS`→認証失敗メッセージ（パスワード不一致・ユーザ不存在・未承認/却下/無効化のいずれも同一のコード・メッセージとする。BR-REG-03によりステータスを理由に別コードを返すとメールアドレス列挙攻撃対策が骨抜きになるため。Code Generation時のレビューで修正）、`AUTH_ACCOUNT_LOCKED`→ロック中メッセージ（BR-LOGIN-01の失敗カウントは存在しないメールアドレスに対しても同様に記録されるため、列挙攻撃には利用できない）

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

## 4. ユーザ管理画面（全ステータスのユーザ一覧、`/users`、`AppShell`）

UNIT-01のナビゲーション項目「ユーザ管理」に対応する。当初は「管理者ダッシュボード（承認待ちユーザ一覧）」と「ユーザ管理（全ステータス）」を別画面として設計していたが、対象データ・操作の大半が重複するため1画面に統合した（レビュー指摘の反映）。UNIT-01の`/mock/dashboard`のデザインを参考にしつつ、全ステータスを扱えるよう拡張して実装する。

### コンポーネント構造
```
UserManagementPage (AppShell)
└ PageHeader（タイトル「ユーザ管理」）
  ├ FilterBar（ステータス絞り込み: 全件/PENDING/APPROVED/REJECTED/DISABLED。初期値はPENDING）
  ├ DataTable（列: 氏名, メールアドレス, ステータス（Badge表示）, 登録日時, アクション）
  │  └ 行ごとに、ステータスに応じたアクションボタンを出し分け
  │     - PENDING → Button（「承認」）, Button（「却下」）
  │     - APPROVED → Button（「無効化」）
  │     - REJECTED → Button（「承認」、却下の取り消し）
  │     - DISABLED → Button（「再有効化」）
  ├ ConfirmDialog（承認／却下／無効化／再有効化の確認。誤操作防止）
  ├ EmptyState（対象ユーザが0件の場合。フィルタがPENDINGのときは「承認待ちユーザはいません」）
  └ Alert（tone=danger、一覧取得・各操作失敗時）
```

### State
- `users: UserSummary[]`, `statusFilter: UserStatus | 'ALL'`（初期値`'PENDING'`）, `loading: boolean`, `errorMessage: string | null`
- `confirmTarget: { userId: string, action: 'approve' | 'reject' | 'disable' | 'enable' } | null`（ConfirmDialog表示制御）

### API連携
- `GET /api/admin/users?status={statusFilter}` → 一覧取得（画面表示時、`statusFilter`変更時、各操作後に再取得。`status`省略（全件表示選択時）は無指定で呼び出す）
- `POST /api/admin/users/{id}/approve` → `PENDING`または`REJECTED`ユーザに対して実行可能（初回承認・却下取り消しの両方でこのエンドポイントを共用。business-logic-model.md §2, §2.1）。確認ダイアログでの最終確認後に実行
- `POST /api/admin/users/{id}/reject` → `PENDING`ユーザに対してのみ実行可能。同上
- `POST /api/admin/users/{id}/disable` → `APPROVED`ユーザに対してのみ実行可能。同上
- `POST /api/admin/users/{id}/enable` → `DISABLED`ユーザに対してのみ実行可能。同上
- 管理者ロール以外でのアクセス時は403エラーとなる想定（アクセス制御自体はUNIT-02のJWT認証・ロールチェックで実現。詳細な権限体系はUNIT-04）

---

## 5. トップ画面（ホーム、`/`、`AppShell`）

ログイン後の着地点として新設する（レビューで発見: 旧「管理者ダッシュボード」が実質的な着地点だったが管理者専用画面であり、一般ユーザ向けの着地点が未定義だった）。SideNavの各ナビ項目（unit-01/functional-design/frontend-components.md §1.3）に対応するカード一覧を表示する、機能説明とリンクを兼ねた画面とする。

### コンポーネント構造
```
HomePage (AppShell)
└ PageHeader（タイトル。アプリ名またはウェルカムメッセージ）
  └ カードグリッド
     └ FeatureCard × 8（Cardをベースにした新設コンポーネント。アイコン, 機能名, 一言説明, リンク）
        - ユーザ管理（UNIT-02、実装済みのため活性）
        - RDBMS接続設定（UNIT-03、未実装のため非活性＋「準備中」バッジ）
        - アクセス制御（UNIT-04、同上）
        - マスタメンテナンス（UNIT-05、同上）
        - 保存クエリ（UNIT-06、同上）
        - クエリビルダー（UNIT-07、同上）
        - クエリ実行履歴（UNIT-08、同上）
        - 監査ログ（UNIT-09、同上）
```

### State
- 静的なカード定義配列（`{ title, description, path, implemented: boolean }[]`）をコンポーネント内に保持。APIからの動的取得は行わない（SideNavの実装済み判定と同じ静的パターンを踏襲）

### 実装方針
- ロールによるカードの出し分けは行わない（SideNavも現状ロール別フィルタを行っておらず、一貫性を優先。実効的なアクセス制御はUNIT-04導入後に各画面側のアクセス権限チェックで担保する）
- `FeatureCard`は`Card`（UNIT-01構築済み）をベースにした新規コンポーネント。`implemented: false`の場合はクリック不可・視覚的に非活性表示とする
- 本画面はAPI連携を持たない（静的なリンク集のため）

---

## 6. AppShell Headerのログアウト導線（実装）

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

## 7. 認証状態管理（画面横断）

- アクセストークン・リフレッシュトークンの保持方式（NFR Design、nfr-design-patterns.md §3.1で確定）: いずれもログイン成功時のレスポンスボディで受け取り、`sessionStorage`に保管する（Cookieは使用しない）
- AppShell配下ルートの認証ガード（未ログイン時は`/login`へリダイレクト）の詳細実装はCode Generation段階で具体化する
- トップ画面（`/`、§5）はAppShell配下のindexルートであるため、上記の認証ガードの対象に含まれる。未ログイン状態で`/`へ直接アクセスした場合も`/login`へリダイレクトする（レビュー指摘の反映）
- 本ドキュメントでは、各画面が「ログイン状態」を前提とするか（`AppShell`配下）／前提としないか（`PublicLayout`配下）の区分のみを定義する

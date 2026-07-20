# UNIT-02（ユーザ登録・認証）Functional Design 計画

## ユニットコンテキスト

- **対応ストーリー**: STORY-1.1〜1.4（ユーザ登録・管理者承認・初期管理者ブートストラップ）、STORY-3.1〜3.3（ログイン・ログアウト・トークンリフレッシュ・再利用検知・ログイン試行制限）
- **対応コンポーネント**: COMP-01 UserRegistrationService, COMP-02 AdminBootstrapService, COMP-03 AuthenticationService, COMP-04 RefreshTokenService, COMP-05 LoginAttemptGuard, COMP-06 EmailNotificationService, COMP-18 AuditLogService（記録機能のみ）, COMP-19 AuditEventPublisher
- **依存ユニット**: UNIT-01（共通UIコンポーネント）
- **バックエンドパッケージ**: `cherry.mastermeister.registration`, `cherry.mastermeister.auth`, `cherry.mastermeister.audit`（`unit-of-work.md`）
- **入力とする既承認の決定事項**: requirements.md FR-1.1〜FR-1.14, FR-3.1〜FR-3.7, §6（監査ログ要件）、component-methods.md（メソッドシグネチャ）

## 実行チェックリスト

- [x] Step 1: ユニット定義・関連要件・コンポーネント定義を分析
- [x] Step 2: Functional Design計画を作成
- [x] Step 3: 質問を作成
- [ ] Step 4: ユーザ回答を収集
- [ ] Step 5: 曖昧な回答がないか分析
- [ ] Step 6: `business-logic-model.md` / `business-rules.md` / `domain-entities.md` / `frontend-components.md` を生成
- [ ] Step 7: 完了メッセージを提示し承認を得る

## 質問

各設問に A/B/C... の記号で回答してください。当てはまる選択肢がない場合は最後の「Other」を選び、[Answer]: の後ろに内容を記述してください。

### Question 1: パスワードハッシュアルゴリズム（FR-1.13）
requirements.mdは「例: bcrypt、Argon2」を挙げていますが、どちらを採用しますか？

A) bcrypt（Spring Securityで標準的にサポートされ、実績豊富）

B) Argon2（2015年のPassword Hashing Competition優勝アルゴリズム、より新しい推奨）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 2: 既知の漏洩パスワードリストとの照合方法（FR-1.12、SECURITY-12）
どのように照合しますか？

A) Have I Been Pwned APIをk-匿名性モデル（パスワードのSHA-1ハッシュ先頭5文字のみを送信）で利用する。外部ネットワーク呼び出しが発生するが、実際のパスワード平文・完全ハッシュは送信されない

B) アプリに同梱する静的な漏洩パスワードリスト（上位N件、例: 1万件程度）とのローカル照合のみ行う。外部通信なし、リストの網羅性は限定的

C) 両方（ローカルリストで即座に弾き、Have I Been Pwned APIでも追加チェック）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 3: JWT実装ライブラリ・署名方式
JWTの発行・検証にどのライブラリ・方式を使いますか？

A) Spring Security（OAuth2 Resource Server機能）+ `jjwt`（io.jsonwebtoken）ライブラリ、対称鍵署名（HMAC-SHA256）

B) Spring Security + `jjwt`、非対称鍵署名（RSA/EC）。鍵ローテーションや複数サービス間検証を見据える場合に有利だが、単独アプリでは過剰な可能性

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 4: リフレッシュトークンのハッシュ化方式（FR-3.2）
パスワードとは異なり、リフレッシュトークンは高エントロピーなランダム値です。保存用ハッシュにどの方式を使いますか？

A) SHA-256（高速。リフレッシュトークン自体が高エントロピーで総当たり困難なため、パスワード用の適応型ハッシュ（bcrypt等）は不要と判断）

B) パスワードと同じ適応型ハッシュアルゴリズム（Question 1の回答）を流用する（処理コストは増えるが実装の一貫性を優先）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 5: ログイン試行制限の実装方式（FR-3.7）
失敗回数のカウントをどこに保持しますか？

A) 内部DBに永続化する（アプリ再起動後もカウントが維持される。監査性が高い）

B) インメモリキャッシュ（Caffeine等）に保持する（再起動でリセットされるが、実装がシンプルで高速）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 6: ログイン試行制限の閾値・ロック時間
具体的な既定値はどうしますか？

A) 5回失敗で15分間ロック（一般的な目安値）

B) 10回失敗で30分間ロック（やや緩め）

X) Other（[Answer]: の後に内容を記述。数値を明記してください）

[Answer]:

### Question 7: ロールモデル
`Role`型（`createApprovedAccount(email, rawPassword, role: Role)`）にはどの値を含めますか？

A) `USER`（一般ユーザ）と`ADMIN`（管理者）の2種類のみ

B) 上記に加えて、将来のロール拡張を見据えた設計にする（現時点では2種類のみ実装するが、enum以外の拡張しやすい構造にする）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 8: 登録確認・承認結果メールのリンク形式
メール内リンクの形式はどうしますか？

A) フロントエンドURL + クエリパラメータ（例: `{mm.app.frontend.base-url}/register/complete?token=xxx`）。フロントエンドがトークンを受け取りAPIを呼び出す

B) フロントエンドURL + パスパラメータ（例: `{mm.app.frontend.base-url}/register/complete/xxx`）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 9: 監査ログ記録対象イベント（本ユニット分、§6.1）
本ユニットで発行するAuditEventの種別は以下で過不足ないですか？

A) `REGISTRATION_REQUESTED`（登録申請）, `USER_APPROVED`（承認）, `USER_REJECTED`（却下）, `LOGIN_SUCCESS`, `LOGOUT`, `LOGIN_FAILURE` の6種別で過不足ない

B) 過不足がある（Otherに具体的に記載してください）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 10: フロントエンド画面構成（本ユニットで実装する画面）
以下の画面構成で過不足ないですか？

A) (1)メールアドレス登録画面, (2)パスワード設定画面（メールリンク先）, (3)ログイン画面, (4)管理者向けユーザ承認ダッシュボード の4画面で過不足ない

B) 過不足がある（Otherに具体的に記載してください）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 11: 却下されたユーザ・ロックされたアカウントのログイン試行時のエラーメッセージ
セキュリティ上、エラーメッセージの詳細度をどうしますか？

A) 認証失敗（パスワード誤り／未承認／却下／ロック中）は全て同一の汎用メッセージ（例:「メールアドレスまたはパスワードが正しくありません」）を返す。アカウント列挙・状態推測を防ぐ

B) 状態に応じて異なるメッセージを返す（例:「アカウントは承認待ちです」「アカウントはロックされています」）。ユーザの利便性を優先

X) Other（[Answer]: の後に内容を記述）

[Answer]:

---

すべての設問に回答後、完了したことを教えてください。回答内容を分析し、曖昧な点があれば追加の確認質問を作成します。

# UNIT-02 ユーザ登録・認証 - Functional Design 計画

## Unit Context

- **対応ストーリー**: STORY-1.1〜1.4（ユーザ登録）、STORY-3.1〜3.3（認証）
- **対応要件**: requirements.md §5.1（ユーザ登録、FR-1.1〜1.14）、§5.3（ユーザ認証、FR-3.1〜3.7）、§6（監査ログ要件、UNIT-02で記録基盤を構築）
- **対応コンポーネント**: COMP-01（UserRegistrationService）, COMP-02（AdminBootstrapService）, COMP-03（AuthenticationService）, COMP-04（RefreshTokenService）, COMP-05（LoginAttemptGuard）, COMP-06（EmailNotificationService）, COMP-18（記録機能のみ）, COMP-19（AuditEventPublisher）
- **前提ユニット**: UNIT-01（共通UIコンポーネント・グランドデザイン・代表画面モックを利用。特にログイン・ユーザ登録画面モックを実データ連携版に置き換える）

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（Q1=B, Q2=A(トークン発行ロジックはrefresh tokenと共用), Q3=A, Q4=A(閾値・ロック時間は設定可能), Q5=A, Q6=A(REQUIRES_NEW明示), Q7=C(UI選択言語→登録後は保存済み言語設定), Q8=A, Q9=A(氏名・言語設定を追加), Q10=A, Q11=A）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— Q2/Q7の解釈をStep Aの回答欄に明記。いずれも一意に解釈可能であり追加質問は不要と判断
- [x] Step C: 成果物を作成する（business-logic-model.md, business-rules.md, domain-entities.md, frontend-components.md）
- [ ] Step D: 完了メッセージを提示し、承認を得る

## 追加事項（質問一覧外）

### メールテンプレート方式
ユーザーより、メールテンプレートはMustache形式とし、自作のMustacheエンジン（サードパーティ製ライブラリ不使用）を提供する旨の申し出があった。`reference/mustache-engine/`（`cherry-mustache-core`, `cherry-mustache-cli`の2 Gradleモジュール）に配置済み。

- **配置場所（確定）**: `cherry-mustache-core`（パッケージ`cherry.mustache`、テストコード一式含む）をワークスペース直下`cherry-mustache-core/`へそのままコピーし、独立したGradleサブプロジェクトとして`settings.gradle.kts`に追加する。パッケージは`cherry.mastermeister`配下へ移動せず`cherry.mustache`のまま維持する（ユーザー指定）。`cherry-mustache-cli`（CLIツール）はバックエンドへの組み込み対象外とし、`reference/`に参照用として残す
- **本ドキュメント（Functional Design）への反映**: business-rules.md BR-MAIL-02に上記方針を記録。unit-of-work.mdのコード構成方針にも同内容を追記済み。実際のファイルコピー・`settings.gradle.kts`／`backend`のGradle依存追加は、本ユニットのCode Generationステージで実施する

## 質問

### Question 1
Userエンティティの状態（ステータス）モデルについて

A) `PENDING`（登録完了・承認待ち）→ `APPROVED`（承認済み・ログイン可能）／`REJECTED`（却下）の3状態とする。却下後の再登録は新規メールアドレス登録として扱う

B) 上記に加え、管理者が事後的にアカウントを無効化できる`DISABLED`状態も設ける

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 2
登録用トークン（メール確認・パスワード設定用）の管理方式について

A) 専用テーブル（例: `registration_token`）でトークン（ハッシュ化して保存）・有効期限・対象メールアドレスを管理する。UNIT-02が新設する、リフレッシュトークンとは独立した仕組みとする

B) リフレッシュトークンと共通のトークン管理基盤を使い回す

C) Other（[Answer]: の後に内容を記述）

[Answer]: A、ただしトークンを発行する機能（ランダムトークン生成・ハッシュ化のユーティリティロジック）はリフレッシュトークンと共用でよい。テーブル・レコードのライフサイクル（有効期限、失効条件）は別管理とする

### Question 3
リフレッシュトークンのローテーション・再利用検知（FR-3.3〜3.4）のデータモデルについて

A) トークンファミリID（初回発行時に採番、ローテーション時は引き継ぐ）を持つ`refresh_token`テーブルとし、再利用検知時は同一ファミリIDの全トークンを一括失効させる

B) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4
ログイン試行制限（FR-3.7）の具体的な閾値について

A) 同一メールアドレスに対して5回連続失敗でロック、ロック時間15分（時間経過で自動解除）

B) 同一メールアドレスに対して10回連続失敗でロック、ロック時間30分

C) Other（[Answer]: の後に具体的な回数・時間を記述）

[Answer]: A（デフォルト値は5回・15分とするが、いずれも設定可能なプロパティとする）

### Question 5
既知漏洩パスワードチェック（FR-1.12、Have I Been Pwned API等）がAPI呼び出し失敗時の挙動について

A) フェイルオープン（API呼び出しに失敗した場合はチェックをスキップし、登録・パスワード変更処理は継続する。可用性を優先）

B) フェイルクローズ（API呼び出しに失敗した場合は登録・パスワード変更処理を拒否する。安全性を優先）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6
監査ログの記録方式（AuditEventPublisher／AuditLogServiceの書き込み経路）について

A) Spring の`ApplicationEventPublisher`を用いてイベントを発行し、`AuditLogService`が`@TransactionalEventListener`（別トランザクション、`AFTER_COMMIT`）で受信して記録する。業務処理の成功可否に関わらず監査ログの記録漏れ・二重ロールバックを防ぐ

B) Other（[Answer]: の後に内容を記述）

[Answer]: A（`AuditLogService`の記録メソッドに`@Transactional(propagation = Propagation.REQUIRES_NEW)`を明示付与する想定。`AFTER_COMMIT`時点では元の業務トランザクションはすでに完了しているため、記録処理用に新規トランザクションを確実に開始する）

### Question 7
メール通知（登録確認・承認結果）の内容・言語について

A) ユーザの登録時点の`Accept-Language`（またはブラウザ言語）に基づき、日本語／英語のいずれかでメール本文を生成する（NFR-7.2: メール文面も多言語対応の適用範囲）

B) 当面は日本語のみで送信する

C) Other（[Answer]: の後に内容を記述）

[Answer]: C 画面の言語選択（UI上の`LanguageSwitcher`で選択中の言語）に基づく。登録完了（Step2でアカウント作成）後は、ユーザの登録情報として保存された言語設定を用いる。具体的には: (1) Step1（メールアドレス送信、確認メール送信）の時点ではアカウントが未作成のため、リクエスト時点でUI上選択されている言語をパラメータとして送信しメール生成に用いる。(2) Step2（パスワード設定、アカウント作成）で氏名・言語設定を登録情報として保存し（Q9参照）、以降の通知（承認/却下結果メール）はこの保存された言語設定を用いる

### Question 8
パスワードリセット（忘れた場合の再設定）機能について

A) UNIT-02のスコープ外とする（requirements.mdに明記された機能ではないため、対応しない）

B) 今回のスコープに含める

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 9
フロントエンドのログイン・ユーザ登録画面について

A) UNIT-01で作成した`/mock/login`・`/mock/register`のモック画面（`design-system`コンポーネント使用）をベースに、実際のAPI呼び出し・状態管理を実装した本番画面（`frontend/src/pages/`等）として新規実装する。モックのコードは流用せず、デザインのみ参考にする（FR-0.5の方針どおり）

B) Other（[Answer]: の後に内容を記述）

[Answer]: A（追加要望: ユーザの登録情報に氏名（フルネーム）と言語設定も持たせたい。Step2のパスワード設定画面に氏名・言語選択の入力項目を追加する）

### Question 10
APIのエラーレスポンス形式について

A) 統一的なエラーレスポンス形式（例: `{ "code": "...", "message": "..." }`）を定義し、UNIT-02以降の全APIで共通利用する（`cherry.mastermeister.common`パッケージに配置）

B) 各ユニットで個別にエラーレスポンス形式を定義する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 11
IPアドレスベースのレート制限について

A) 行わない（requirements.md §6.2に「IPアドレスの記録は未実装」と明記されており、ログイン試行制限はメールアドレス単位のみとする）

B) IPアドレスベースの制限も併せて実装する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

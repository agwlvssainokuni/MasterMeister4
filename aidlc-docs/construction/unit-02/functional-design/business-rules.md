# UNIT-02 ユーザ登録・認証 - Business Rules

business-logic-model.mdで参照する詳細な業務ルールを定義する。各ルールIDはbusiness-logic-model.md・domain-entities.md・frontend-components.mdから参照される。

---

## ユーザステータス（BR-REG-xx）

### BR-REG-01: ステータスモデル（Q1=B）
Userのステータスは以下の4状態とする。

| ステータス | 意味 | ログイン可否 |
|---|---|---|
| `PENDING` | 登録完了・承認待ち | 不可 |
| `APPROVED` | 承認済み | 可 |
| `REJECTED` | 却下済み | 不可 |
| `DISABLED` | 管理者による事後的な無効化 | 不可 |

**状態遷移**:
- `PENDING` → `APPROVED`（管理者承認）
- `PENDING` → `REJECTED`（管理者却下）
- `APPROVED` → `DISABLED`（管理者による無効化。管理者が任意のタイミングで実行可能。`POST /api/admin/users/{id}/disable`）
- `DISABLED` → `APPROVED`（管理者による無効化解除。再有効化を許可する。`POST /api/admin/users/{id}/enable`）
- `REJECTED`から他状態への遷移は行わない。却下されたメールアドレスで再度利用したい場合は、Step 1からの新規登録として扱う（新しいUserレコードを作成する。BR-REG-06参照）

無効化／再有効化の操作は、管理者ダッシュボードとは別の「ユーザ管理」画面（承認待ちに限らず全ステータスのユーザを対象とする）から行う（frontend-components.md参照）。いずれの操作もAuditEventPublisher経由で記録する（BR-AUDIT-02の`USER_DISABLED`/`USER_ENABLED`）。

### BR-REG-02: 登録用トークン管理（Q2=A）
- 専用テーブル（`registration_token`）でトークンを管理する。リフレッシュトークンとは独立したライフサイクル（有効期限・失効条件）を持つ
- トークンは平文を保存せず、ハッシュ化して保存する
- トークンの生成（ランダム値生成）・ハッシュ化のロジックは、RefreshTokenServiceが使うものと共通のユーティリティ（`TokenGenerator`）を利用する（実装レベルの再利用であり、テーブル・ライフサイクル管理自体は別）
- 有効期限は設定可能（`mm.app.user-registration.token-expiry`、デフォルト3時間）
- 1回使用（Step2完了時点）で無効化する。無効化済みトークンでの再度のStep2実行は拒否する
- 同一メールアドレスに対しStep1が複数回実行された場合、新しいトークンを発行し、古い未使用トークンは無効化する（同時に複数の有効なトークンを持たせない）

### BR-REG-03: 承認済みユーザのみログイン可能（FR-1.7）
`APPROVED`ステータス以外のユーザによるログイン試行は、AuthenticationServiceが拒否する。エラーメッセージは、ユーザ列挙を防ぐため「メールアドレスまたはパスワードが正しくありません」等、認証情報不備時と同一の文言とする（BR-REG-04と同じ理由）。

### BR-REG-04: メールアドレス列挙攻撃対策（FR-1.8）
以下のいずれの操作でも、対象メールアドレスの存在有無によってAPIレスポンス（ステータスコード・レスポンスボディ・応答時間の傾向）を変えない。
- 登録開始（Step1）: 既存メールアドレスでも新規メールアドレスでも同一の成功レスポンスを返す（内部的には新規メールアドレスの場合のみ実際にメール送信・トークン発行を行う）
- ログイン: メールアドレス不存在・パスワード不一致のいずれも同一のエラーメッセージ・ステータスコードとする

### BR-REG-05: 登録時に収集する属性（Q9追加要望）
Step2（パスワード設定）で、パスワードに加えて以下をUserの属性として収集・保存する。
- 氏名（`fullName`、必須）
- 言語設定（`preferredLanguage`、必須。選択式。値は`ja`/`en`）

### BR-REG-06: メールアドレスの一意性・再登録可否（レビュー指摘の反映）
`email`の一意制約は「`REJECTED`以外のステータスのUserに対して一意」とする。すなわち、同一メールアドレスで`REJECTED`のUserレコードが存在していても、新規登録（Step1）は妨げない。

Step1の重複チェック（1.1参照）では、対象メールアドレスが`PENDING`／`APPROVED`／`DISABLED`のいずれかで既に存在する場合は新規のトークン発行・メール送信を行わない（BR-REG-04により、この場合もAPIレスポンス自体は成功と同一の内容を返す）。
- `PENDING`／`APPROVED`での重複は、通常の「既に登録済み」ケースとして扱う
- `DISABLED`での重複は、無効化されたアカウントの実質的な迂回（不正な再取得）を防ぐため、意図的にブロックする対象に含める
- `REJECTED`での重複のみ、新規のUserレコードを作成する新規登録として扱う（BR-REG-01）

---

## パスワードポリシー（BR-PWD-xx、FR-1.11〜FR-1.13）

### BR-PWD-01: 最小文字数
8文字以上を要件とする。文字種混在等の複雑さ要件は課さない（設定可能: `mm.app.password.min-length`、デフォルト8）。

### BR-PWD-02: 既知漏洩パスワードチェック（Q5=A）
外部API（Have I Been Pwned等）を用いて、既知の漏洩パスワードリストとの照合を行う。
- **フェイルオープン方針**: 外部API呼び出しが失敗（タイムアウト・エラー応答等）した場合、チェックをスキップし処理を継続する。可用性を優先する
- 漏洩が確認された場合は登録・パスワード変更を拒否する

### BR-PWD-03: ハッシュ化
適応型ハッシュアルゴリズム（bcrypt、Argon2等）でハッシュ化して保存する。平文パスワードは一切保存・ログ出力しない（SECURITY-03準拠）。

---

## リフレッシュトークン（BR-TOKEN-xx、FR-3.2〜FR-3.4）

### BR-TOKEN-01: ローテーション
リフレッシュのたびに新しいリフレッシュトークンを発行し、使用済みの旧トークンを無効化する（1回限り使用可能）。新トークンは元のトークンと同一のトークンファミリIDを引き継ぐ。

### BR-TOKEN-02: 再利用検知
無効化済み（使用済み）のトークンが再度リフレッシュ要求で提示された場合、トークン窃取・再送の兆候とみなし、同一トークンファミリIDに属する全トークン（有効・無効問わず）を一括で失効させる。当該ユーザは以降、再ログインが必要となる。

### BR-TOKEN-03: 有効期限
- アクセストークン: デフォルト10分（`mm.app.jwt.access-token-expiry`）、ステートレス（DB永続化なし）
- リフレッシュトークン: デフォルト24時間（`mm.app.jwt.refresh-token-expiry`）、内部DBにハッシュ化して永続化

---

## ログイン試行制限（BR-LOGIN-xx、FR-3.7、Q4=A）

### BR-LOGIN-01: 閾値・ロック
同一メールアドレスに対する連続ログイン失敗が閾値（デフォルト5回、`mm.app.login-attempt.max-failures`）に達した場合、そのメールアドレスを一定時間（デフォルト15分、`mm.app.login-attempt.lock-duration`）ロックする。ロック中はパスワードが正しい場合でも認証を拒否する。ロック時間経過後は自動的に解除される（明示的な解除操作は不要）。

### BR-LOGIN-02: カウンタリセット
ログイン成功時、当該メールアドレスの失敗カウンタをリセットする。

### BR-LOGIN-03: IPベース制限は行わない（Q11=A）
requirements.md §6.2に基づき、IPアドレスの記録・IPベースのレート制限は行わない。制限はメールアドレス単位のみとする。

---

## 監査ログ記録（BR-AUDIT-xx、§6.1〜6.3、Q6=A）

### BR-AUDIT-01: 記録方式
`ApplicationEventPublisher`でAuditEventを発行し、`AuditLogService`が`@TransactionalEventListener(phase = AFTER_COMMIT)`で受信する。記録メソッドには`@Transactional(propagation = Propagation.REQUIRES_NEW)`を明示付与し、`AFTER_COMMIT`時点で元の業務トランザクションが既に完了していることを踏まえ、記録用の新規トランザクションを確実に開始する。業務処理の成功可否と監査ログの記録は独立して扱う（業務処理がロールバックされても、その前に発行済みのイベントは記録されない。イベントはコミット後にのみ発行されるため）。

### BR-AUDIT-02: 本ユニットで記録するイベント種別
- ログイン／ログアウト／ログイン失敗
- アカウント登録申請（Step1、`REGISTRATION_REQUESTED`）
- アカウント登録完了（Step2、`REGISTRATION_COMPLETED`。Step1の申請とは別タイミング・別アクター（申請はユーザ本人のメール送信、完了はメール内リンク経由のパスワード設定）の操作のため、別イベント種別として記録する）
- 管理者によるアカウント承認／却下（`USER_APPROVED` / `USER_REJECTED`）
- 管理者によるアカウント無効化／再有効化（`USER_DISABLED` / `USER_ENABLED`）

### BR-AUDIT-03: 記録項目
日時（ISO 8601）、ユーザID、対象接続ID（本ユニットのイベントでは通常null）、操作種別、対象リソース、結果ステータス。パスワード・トークン等の機微情報は記録しない（SECURITY-03準拠）。IPアドレスは記録しない（requirements.md §6.2）。

---

## メール通知（BR-MAIL-xx、FR-1.2, FR-1.6, NFR-7.2, Q7=C）

### BR-MAIL-01: 言語決定ルール
- **Step1（登録確認メール）**: Userレコードがまだ存在しないため、リクエスト時点で画面上選択されているUI言語をそのままメール生成言語として使用する
- **Step2完了後（承認結果メール）**: Userレコードの`preferredLanguage`（Step2で保存済み）を使用する

### BR-MAIL-02: テンプレート方式
メール本文はテンプレートエンジン（Mustache形式、ユーザー提供の自作実装。サードパーティ製ライブラリ不使用）を用いて生成する。テンプレートは言語（`ja`/`en`）ごとに用意する。

**配置・組み込み方針（確定）**:
- `reference/mustache-engine/cherry-mustache-core`（パッケージ`cherry.mustache`、`java-library`プラグイン、JUnit5・jqwik（PBT）・OWASP Dependency-Checkが構成済み）を、テスト一式（`src/test/java`, `src/test/resources/spec`）を含めてワークスペース直下`cherry-mustache-core/`へそのままコピーする
- パッケージは`cherry.mustache`のまま変更しない（`cherry.mastermeister`配下への移動は行わない。自己完結した独立ライブラリとして扱う）
- `settings.gradle.kts`に`include("cherry-mustache-core")`を追加し、`backend`から`implementation(project(":cherry-mustache-core"))`として参照する
- `reference/mustache-engine/cherry-mustache-cli`（CLIツール）はバックエンドが直接Java APIを呼び出すため不要と判断し、コピー対象に含めない（`reference/`内に参照用として残置）
- 上記の実際のファイルコピー・Gradle設定変更は、本ユニットのCode Generationステージで実施する（Functional Designでは方針決定のみ）

---

## APIエラーレスポンス（BR-API-xx、Q10=A）

### BR-API-01: 統一形式
UNIT-02以降の全APIで、以下の統一形式のエラーレスポンスを用いる。

```json
{
  "code": "REGISTRATION_TOKEN_EXPIRED",
  "message": "登録用リンクの有効期限が切れています"
}
```

`code`はエラー種別を表す機械可読な識別子、`message`はユーザ向け表示用メッセージ（i18n対応、リクエストの言語設定に応じて生成）とする。詳細な型定義は`cherry.mastermeister.common`パッケージに配置する（NFR Design／Code Generationステージで確定）。

---

## パスワードリセット（Q8=A）

パスワードリセット（忘れた場合の再設定）機能は、requirements.mdに明記された要件ではないため、UNIT-02のスコープ外とする。

# UNIT-02 ユーザ登録・認証 - NFR Design 計画

## Unit Context

NFR Requirements（nfr-requirements.md, tech-stack-decisions.md）を踏まえ、実装パターンと論理コンポーネントを確定する。NFR Requirements段階でいくつかの決定を明示的にNFR Design段階へ据え置いていた（NFR-4.7: リフレッシュトークンのクライアント配信方式、SECURITY-04/05の実装パターン、グローバル例外ハンドラのパターン等）ため、本ステージで確定する。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する（全問AI推奨どおり: Q1=A, Q2=A, Q3=A, Q4=B, Q5=A, Q6=A, Q7=A, Q8=A）
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 全問明確な選択肢の回答であり曖昧性なし
- [x] Step C: 成果物を作成する（nfr-design-patterns.md, logical-components.md）
- [ ] Step D: 完了メッセージを提示し、承認を得る

## カテゴリ評価

| カテゴリ | 判定 | 理由 |
|---|---|---|
| Resilience Patterns | 該当（軽量） | Resiliencyベースライン拡張は非適用（Q2=B、requirements.md §7.6）だが、基本的なエラーハンドリング設計（外部API・SMTP送信失敗時の挙動）はカテゴリとして評価する |
| Scalability Patterns | N/A | NFR Requirementsで確定済み（同時利用者約10名、本ユニット固有のスケーリング設計は不要） |
| Performance Patterns | 該当（軽量） | BCryptコスト係数はNFR Requirementsで確定済み。追加の性能パターンとしては特に無し |
| Security Patterns | 該当（多数） | NFR-4.7、SECURITY-01/04/05/15等、NFR Requirements段階から据え置かれた実装パターンの確定が中心 |
| Logical Components | 該当 | SecurityFilterChain構成、グローバル例外ハンドラ、レート制限コンポーネント等 |

## 質問

### Question 1
リフレッシュトークンのクライアント配信方式について（NFR-4.7、SECURITY-12。requirements.md記載のとおりNFR Design段階で確定する事項）

A) httpOnly + Secure + SameSite=Strict Cookieで配信する。XSSによるトークン窃取リスクを大幅に低減できる（SECURITY-12のセッションCookie属性要件にも合致）

B) レスポンスボディで返却し、フロントエンド側でメモリ上に保持する（ページリロードで失効するがXSS耐性はCookieに劣る。CSRF対策が不要というメリットもある）

C) Other（[Answer]: の後に内容を記述）

[Answer]: B（訂正。アクセストークン・リフレッシュトークンともにレスポンスボディで返却し、クライアント側は両方とも`sessionStorage`に保管する。CSRF対策は不要（Cookieを用いないため）。XSS対策（SECURITY-04のCSP等）が主たる防御層となる）

### Question 2
HTTPセキュリティヘッダ（NFR-4.2、SECURITY-04）の実装パターンについて

A) Spring Securityのデフォルトヘッダ設定（`headers()`DSL）をベースに、CSPのみアプリ用に明示設定する（`default-src 'self'`）

B) 独自の`OncePerRequestFilter`で全ヘッダを手動設定する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 3
入力バリデーション（NFR-4.3、SECURITY-05）の実装パターンについて

A) Bean Validation（`jakarta.validation`アノテーション、`@Valid`）をリクエストDTOに付与する標準的な方式

B) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4
内部DB（H2）の保存時暗号化方針について（SECURITY-01）。パスワードハッシュ・トークンハッシュ以外に、氏名・メールアドレス等のPIIが平文で保存される

A) H2のCIPHER機能（`jdbc:h2:file:...;CIPHER=AES`）でDBファイル自体を暗号化する（アプリ層での多層防御）

B) アプリ層での暗号化は行わず、ホスト・ディスクレベルの暗号化（デプロイ環境が提供）に委ねる方針とし、その旨を運用要件として明記する

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 5
グローバル例外ハンドラの実装パターンについて（SECURITY-15、NFR-02-03、BR-API-01との整合）

A) `@RestControllerAdvice` + `@ExceptionHandler`で例外種別ごとにBR-API-01形式のレスポンスへ変換する。未捕捉の例外は`INTERNAL_SERVER_ERROR`として汎用メッセージのみ返し、詳細（スタックトレース等）はログにのみ出力する

B) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6
SecurityFilterChain・JWT検証フィルタの構成について（Logical Components、SECURITY-08）

A) `spring-boot-starter-oauth2-resource-server`の`JwtDecoder`をベースに、`SecurityFilterChain`で`/api/auth/**`・`/api/registrations/**`を`permitAll()`、`/api/admin/**`を`hasRole('ADMIN')`、その他を`authenticated()`とする構成

B) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7
既知漏洩パスワードチェックAPI（HIBP等）呼び出しのタイムアウト・エラー処理について（Resilience、BR-PWD-02のフェイルオープン方針の実装レベルでの具体化）

A) HTTPクライアントに短めのタイムアウト（例: 3秒）を設定し、タイムアウト・エラー発生時は例外をキャッチしてチェックをスキップする（BR-PWD-02のフェイルオープンをそのまま実装）

B) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 8
メール送信失敗時の登録処理継続方針について（Resilience）

A) メール送信失敗時も登録処理自体（トークン発行・User作成等のDB操作）は成功として扱う。送信失敗はログに記録するのみとする（フェイルオープン的な扱い。ユーザにはStep1/承認完了として案内するが、実際にメールが届かない可能性が残る）

B) メール送信に失敗した場合は登録処理自体もロールバックし、エラーを返す（フェイルクローズ。ユーザは失敗を認識し再試行できる）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A（登録開始のみならず、承認・却下・無効化・再有効化を含む全ての管理者操作通知メールについても同じくフェイルオープンで統一する）

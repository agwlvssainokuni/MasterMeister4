# UNIT-02 ユーザ登録・認証 - NFR Design Patterns

`unit-02-nfr-design-plan.md`の回答（Q1=B〔訂正版〕, Q2=A, Q3=A, Q4=B, Q5=A, Q6=A, Q7=A, Q8=A）に基づく実装パターンを記載する。

---

## 1. Resilience

### 1.1 既知漏洩パスワードチェックAPI（HIBP等）のタイムアウト・エラー処理（Q7=A）
- HTTPクライアント（`RestClient`/`WebClient`）にタイムアウト（接続・読取とも3秒）を設定する
- タイムアウト・接続エラー・5xxエラーいずれの場合も例外をキャッチし、チェックをスキップして処理を継続する（BR-PWD-02のフェイルオープン方針をそのまま実装）
- スキップした事実はログに記録する（監査ログではなくアプリケーションログ、SECURITY-03）

### 1.2 メール送信失敗時の処理継続方針（Q8=A）
- 登録確認・承認結果・却下結果の各メール送信は、送信失敗時も呼び出し元の業務処理（トークン発行・User作成・ステータス変更等のDB操作）をロールバックしない
- 送信失敗はアプリケーションログに記録する（宛先・イベント種別・失敗理由。パスワード等の機微情報は含めない）
- 無効化・再有効化はそもそもメール通知を行わない方針のため対象外（business-logic-model.md §3参照）

---

## 2. Performance

- パスワードハッシュ（BCrypt、コスト係数10）はNFR Requirementsで確定済み。追加の性能最適化パターンは設けない
- アクセストークンの検証はJWT署名検証のみ（DB照会不要）でステートレスに行う

---

## 3. Security

### 3.1 認証トークンのクライアント保持方式（Q1=B、訂正版。NFR-4.7最終確定）
- アクセストークン・リフレッシュトークンはいずれもレスポンスボディで返却する（Cookieは使用しない）
- クライアント側（フロントエンド）は両トークンとも`sessionStorage`に保管する（ブラウザタブを閉じるとクリアされる。ページリロードには耐える）
- Cookieを使用しないため、CSRF対策（トークン検証・SameSite設定等）は不要
- 一方、`sessionStorage`はJavaScriptから読み取り可能なため、XSS対策（3.2のCSP、SECURITY-05の入力バリデーション・出力エスケープ）が、トークン漏洩防止の主たる防御層となる。単一の防御に依存しないよう、CSPと入力バリデーションの両方を確実に実装する（SECURITY-11、多層防御）

### 3.2 HTTPセキュリティヘッダ（Q2=A、NFR-4.2）
- Spring Securityの`headers()` DSLによるデフォルト設定（`X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`等）をベースとする
- `Content-Security-Policy`はアプリ用に明示設定する（`default-src 'self'`を基本とし、フロントエンドが自己ホストの静的リソースのみを参照する構成に合わせる。インラインスクリプト・スタイルの許可有無はCode Generation段階でフロントエンドのビルド成果物を確認して確定する）

### 3.3 入力バリデーション（Q3=A、NFR-4.3）
- 全APIのリクエストDTOにBean Validationアノテーション（`@NotBlank`, `@Email`, `@Size`等）を付与し、コントローラで`@Valid`を用いる
- バリデーション違反時はBR-API-01形式のエラーレスポンス（`code: VALIDATION_ERROR`等）に変換する（3.5のグローバル例外ハンドラで一元処理）

### 3.4 内部DBの保存時暗号化方針（Q4=B、NFR-4.8）
- H2データベースファイル自体のアプリ層暗号化は行わない（`CIPHER`オプションは使用しない）
- requirements.md NFR-4.8として文書化された例外を記録済み。ホスト・ディスクレベルの暗号化に委ねる運用要件とする
- パスワードハッシュ・登録トークン・リフレッシュトークンは、DBファイル暗号化とは独立に、個々のカラムレベルで既にハッシュ化済み（BR-PWD-03, BR-REG-02, FR-3.2）であり、この方針変更の影響を受けない

### 3.5 グローバル例外ハンドラ（Q5=A、SECURITY-15）
- `@RestControllerAdvice`を`cherry.mastermeister.common`パッケージに新設し、`@ExceptionHandler`で例外種別ごとにBR-API-01形式のレスポンスへ変換する
- 業務例外（トークン期限切れ、パスワードポリシー違反等）は、それぞれ固有の`code`とHTTPステータス（400/401/403/404等）にマッピングする
- 未捕捉の例外（`Exception.class`をキャッチする最終フォールバック）は`INTERNAL_SERVER_ERROR`（500）とし、`message`は汎用文言のみ返す。スタックトレース・例外クラス名等の内部情報はレスポンスに含めず、ログにのみ出力する（SECURITY-09）
- 認証・認可の失敗時は、フェイルクローズ（アクセス拒否）を徹底する。バリデーション例外・DBアクセス例外等、いずれの経路でも認可チェックをバイパスしない

### 3.6 SecurityFilterChain・JWT検証（Q6=A、SECURITY-08）
- `spring-boot-starter-oauth2-resource-server`の`JwtDecoder`（HS256、NFR Requirements Q2で確定した対称鍵）でアクセストークンを検証する
- `SecurityFilterChain`のURLパターンマッチング:
  - `permitAll()`: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/registrations/**`
  - `hasRole('ADMIN')`: `/api/admin/**`
  - `authenticated()`: 上記以外の`/api/**`
- ~~CORS設定は許可オリジンを明示的に列挙する（ワイルドカード不使用、NFR-4.6/SECURITY-08）。単一WAR構成（フロントエンドを同一オリジンで配信）のため、実運用では追加のCORS許可設定は最小限で済む見込み~~ 訂正（UNIT-02 Code Generationにて）: 開発時もVite devサーバのプロキシ（`/api`→バックエンド）を導入したため、ブラウザから見て開発時・本番時とも常に同一オリジンとなる。クロスオリジンアクセス自体が発生しないためCORS設定（`corsConfigurationSource()` Bean）は不要と判明し削除した

---

## Testable Properties（PBT-01対応、再確認）

NFR Design段階で追加された実装パターン（トークン保持方式、例外ハンドラ、フィルタチェーン構成）はいずれもインフラ・設定レベルの決定であり、PBT対象となるアルゴリズム的処理を含まない。Functional Design段階の判定（No PBT properties identified）に変更はない。

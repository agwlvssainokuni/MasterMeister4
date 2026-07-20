# UNIT-02 ユーザ登録・認証 - API Layer Summary

`unit-02-code-generation-plan.md` Section 9〜12の実行結果サマリ。

## セキュリティ設定（`backend/src/main/java/cherry/mastermeister/common/security/SecurityConfig.java`）

- `PasswordEncoder`: `BCryptPasswordEncoder`（`AppProperties.Password.bcryptStrength`）
- `JwtEncoder`/`JwtDecoder`: HS256、`AppProperties.Jwt.secret`（32バイト以上をコンストラクタで検証）
- `JwtAuthenticationConverter`: JWTの`role`クレーム→`ROLE_*`権限
- `SecurityFilterChain`: `/api/auth/**`・`/api/registrations/**`は`permitAll()`、`/api/admin/**`は`hasRole('ADMIN')`、他の`/api/**`は`authenticated()`。`/api/**`以外はSPA配信のため`permitAll()`
- CSP: `default-src 'self'`を明示設定

## SPA配信（`common/web/SpaWebConfig.java`）

`WebMvcConfigurer`のリソースハンドラで、存在する静的リソースはそのまま返却し、それ以外は`/index.html`にフォールバックする。`/api/**`はSpring MVCの`RequestMappingHandlerMapping`が優先処理するため、このリソースハンドラには到達しない。

## APIエンドポイント

| メソッド | パス | コントローラ | 認可 |
|---|---|---|---|
| POST | `/api/registrations` | `RegistrationController` | permitAll |
| POST | `/api/registrations/{token}/complete` | `RegistrationController` | permitAll |
| POST | `/api/auth/login` | `AuthController` | permitAll |
| POST | `/api/auth/refresh` | `AuthController` | permitAll |
| POST | `/api/auth/logout` | `AuthController` | permitAll |
| GET | `/api/admin/users?status=` | `AdminUserController` | ADMIN |
| POST | `/api/admin/users/{id}/approve` | `AdminUserController` | ADMIN |
| POST | `/api/admin/users/{id}/reject` | `AdminUserController` | ADMIN |
| POST | `/api/admin/users/{id}/disable` | `AdminUserController` | ADMIN |
| POST | `/api/admin/users/{id}/enable` | `AdminUserController` | ADMIN |

`GlobalExceptionHandler`（`common/GlobalExceptionHandler.java`）が`ApiException`・バリデーション例外・未捕捉例外をBR-API-01形式（`{code, message}`）に変換する。`message`は`MessageSource`経由でリクエストの言語設定に応じて解決する。

OpenAPI/Swagger UIは`springdoc-openapi-starter-webmvc-ui`により`/v3/api-docs`・`/swagger-ui.html`で自動公開される（`common/web/OpenApiConfig.java`でBearer認証スキームを追加）。

## テスト結果

| クラス | テスト数 | 主な検証内容 |
|---|---|---|
| `RegistrationControllerTest` | 5 | 正常系、バリデーションエラー、トークン無効・パスワードポリシー違反時のエラーコード |
| `AuthControllerTest` | 5 | ログイン成功・認証失敗・ロック中、リフレッシュ、ログアウト |
| `AdminUserControllerTest` | 5 | 一覧取得、承認/却下/無効化/再有効化のJWT subject→userId連携（ADMIN権限のJWTで実フィルタチェーンを通す） |
| `SecurityConfigTest` | 4 | `/api/admin/**`の401（未認証）・403（非ADMIN）・200（ADMIN）、`/api/registrations`のpermitAll |

**合計**: 4クラス・19テストケース、すべて成功。`RegistrationControllerTest`/`AuthControllerTest`は`addFilters=false`でコントローラ単体の挙動を検証し、`AdminUserControllerTest`/`SecurityConfigTest`は実際の`SecurityFilterChain`を有効にして認可ルールまで検証する（住み分けは方式のトラブルシューティング参照）。

## 実装時のトラブルシューティング（後続ユニットへの申し送り）

- **Spring Boot 4.1でのAPIパッケージ移動**: `@DataJpaTest`（Section 5と同様）に加え、`@WebMvcTest`/`AutoConfigureMockMvc`も`org.springframework.boot.test.autoconfigure.web.servlet`ではなく`org.springframework.boot.webmvc.test.autoconfigure`（`spring-boot-starter-webmvc-test`）に移動していた。`MacAlgorithm`も`org.springframework.security.oauth2.jwt`ではなく`org.springframework.security.oauth2.jose.jws`。`@MockBean`は廃止され`org.springframework.test.context.bean.override.mockito.MockitoBean`を使用する
- **`@AuthenticationPrincipal`と`addFilters=false`の相性**: `@AuthenticationPrincipal`の引数リゾルバは`@EnableWebSecurity`（`SecurityConfig`）がインポートされて初めて登録される。また`SecurityMockMvcRequestPostProcessors.jwt()`は実際の`SecurityFilterChain`が有効（`addFilters=false`を指定しない）でないと`SecurityContext`へ反映されない。このため`@AuthenticationPrincipal`を使う`AdminUserController`のテストのみ、`SecurityConfig`をインポートしADMIN権限のJWTで実フィルタを通す方式とした
- **`NimbusJwtEncoder`は`JwsHeader`未指定だとデフォルトでRS256を試み、HMAC秘密鍵JWKSourceでは`JwtEncodingException: Failed to select a JWK signing key`になる（Section 18の起動検証で発覚）**: `AuthenticationService.generateAccessToken()`が`JwtEncoderParameters.from(claims)`（`JwsHeader`省略）を使っていたため、`SecurityConfig`で構成した`ImmutableSecret`（HS256用）とアルゴリズムが一致せず失敗していた。`@WebMvcTest`ベースの`AuthControllerTest`は`JwtEncoder`自体を`@MockitoBean`で差し替えるため、これまで検出されなかった。`JwsHeader.with(MacAlgorithm.HS256).build()`を明示的に渡す`JwtEncoderParameters.from(header, claims)`に修正
- **CORS設定は不要と判明し削除（訂正、承認後の修正）**: `SecurityConfig`に設けていた`corsConfigurationSource()`（許可オリジンを`http://localhost:5173`に限定）は、Section 16で追加したVite devサーバの`server.proxy`（`/api`→`http://localhost:8080`）と役割が重複していた。プロキシはサーバサイドで転送するため、ブラウザから見ると常に同一オリジンへのリクエストとなりCORSプリフライト自体が発生しない。ユーザ指摘により`corsConfigurationSource()` Bean・`HttpSecurity.cors(...)`呼び出しを削除

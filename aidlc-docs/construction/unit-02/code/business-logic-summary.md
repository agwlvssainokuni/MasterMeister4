# UNIT-02 ユーザ登録・認証 - Business Logic Summary

`unit-02-code-generation-plan.md` Section 6〜8の実行結果サマリ。

## 作成したコンポーネント（`backend/src/main/java/cherry/mastermeister/`）

| コンポーネント | パッケージ | 対応 |
|---|---|---|
| `TokenGenerator` | `common.security` | BR-REG-02（トークン生成・ハッシュ化の共通ユーティリティ） |
| `PasswordBreachChecker` | `registration` | BR-PWD-02（HIBP k-Anonymity API、3秒タイムアウト、フェイルオープン） |
| `MailTemplateRenderer` | `common.mail` | BR-MAIL-02〜03（`cherry-mustache-core`ラップ、件名抽出） |
| `EmailNotificationService`（COMP-06） | `registration` | business-logic-model.md §9、送信失敗時フェイルオープン |
| `RegistrationRateGuard` | `registration` | BR-REG-07（登録エンドポイントのレート制限） |
| `UserRegistrationService`（COMP-01） | `registration` | business-logic-model.md §1〜4（登録・承認・却下・却下取消・無効化・再有効化・初期管理者作成） |
| `AdminBootstrapService`（COMP-02） | `registration` | `ApplicationRunner`、FR-1.14 |
| `LoginAttemptGuard`（COMP-05） | `auth` | BR-LOGIN-01〜03 |
| `AuthenticationService`（COMP-03） | `auth` | business-logic-model.md §5、`JwtEncoder`によるアクセストークン発行 |
| `RefreshTokenService`（COMP-04） | `auth` | business-logic-model.md §6、BR-TOKEN-01〜04 |
| `AuditEventPublisher`（COMP-19） | `audit` | `ApplicationEventPublisher`ラッパー |
| `AuditLogService`（COMP-18、記録機能のみ） | `audit` | `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` |

## APIエラー例外（`common.exception`、BR-API-01）

`ApiException`（基底、code + HttpStatus）と6種の具象例外（`AuthenticationFailedException`, `AccountLockedException`, `RegistrationTokenInvalidException`, `PasswordPolicyViolationException`, `RefreshTokenInvalidException`, `InvalidUserStateTransitionException`）。ユーザ向けメッセージはNFR-7.3に基づき、`GlobalExceptionHandler`（Section 10）が`MessageSource`経由でリクエストの言語設定に応じて解決する（`messages_ja/en.properties`の`error.<code>`キー）。例外クラス自体はメッセージ文言を持たない。

## メールテンプレート（`backend/src/main/resources/mail-templates/{ja,en}/`）

`registration-confirmation.html`, `approval-result.html`, `rejection-result.html`（各言語×3種）。`<title>`要素を件名として一元管理する（BR-MAIL-03）。

## テスト結果

Mockitoベースのユニットテスト（`@ExtendWith(MockitoExtension.class)`）。

| クラス | テスト数 | 主な検証内容 |
|---|---|---|
| `TokenGeneratorTest` | 4 | トークンの一意性、ハッシュの決定性・非可逆性 |
| `PasswordBreachCheckerTest` | 3 | HIBP応答の一致/不一致判定（`MockRestServiceServer`）、API失敗時のフェイルオープン |
| `MailTemplateRendererTest` | 9 | 件名抽出の境界値（改行、属性付き`<title>`、`<title>`欠落・空の例外送出、HTMLエンティティデコード） |
| `RegistrationRateGuardTest` | 4 | 閾値内/到達時の許可・拒否、時間窓リセット |
| `LoginAttemptGuardTest` | 4 | ロック判定、閾値到達時のロック、リセット |
| `AuthenticationServiceTest` | 8 | ログイン成功・ロック中・認証失敗（不存在/パスワード不一致/未承認各ステータス）の同一例外化、リフレッシュ、ログアウト |
| `RefreshTokenServiceTest` | 8 | 発行、ローテーション、期限切れ・未検出時のエラー、再利用検知によるファミリ一括失効・監査イベント発行、管理者無効化時の一括失効 |
| `UserRegistrationServiceTest` | 21 | 登録Step1/2の正常系・異常系、承認（PENDING/REJECTED双方から）・却下・無効化・再有効化の状態遷移とその禁則、初期管理者作成の冪等性 |
| `AuditLogServiceTest` | 2 | イベント→エンティティのフィールドマッピング、userId未特定時のnull許容 |

**合計**: 9クラス・63テストケース、すべて成功（`./gradlew :backend:test`）。

## 実装時に発見・修正した設計ギャップ

1. **`AUTH_ACCOUNT_NOT_APPROVED`とBR-REG-03の矛盾**: frontend-components.mdが承認待ち/却下済みユーザのログイン試行を別エラーコードとして列挙していたが、これはBR-REG-03（メールアドレス列挙攻撃対策のため認証情報不備時と同一メッセージとする）と矛盾していた。`AUTH_INVALID_CREDENTIALS`に統合し、frontend-components.mdを修正
2. **リフレッシュトークン再利用検知の監査イベント種別が未定義**: business-logic-model.md §6で「該当イベントを発行する」としていたが対応する`AuditEventType`がなかった。`TOKEN_REUSE_DETECTED`を追加し、domain-entities.md・business-rules.md・business-logic-model.mdを整合させた

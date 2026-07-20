# UNIT-02 ユーザ登録・認証 - NFR Requirements

`unit-02-nfr-requirements-plan.md`の回答（全問A）に基づく。カテゴリ別のNFR要件と、Security Baseline拡張・Property-Based Testing拡張の該当ルール評価を記載する。技術選定の詳細（ライブラリ・アルゴリズム名等）はtech-stack-decisions.mdを参照。

---

## 1. Scalability — N/A

NFR-1.1（同時利用者数は約10名を想定）により、本ユニット固有のスケーリング設計（水平分割、キャッシュ層の追加等）は不要と判断する。

## 2. Availability — N/A

requirements.mdにHA（高可用性）・DR（災害復旧）要件の記載はない。既存方針（NFR-2.x、単一の実行可能WAR、自己ホスト運用）と整合する前提で、本ユニットとして追加のAvailability要件は設けない。

## 3. Performance

- **NFR-02-01**: パスワードハッシュ（BCrypt、Q3=A）のコスト係数はデフォルト10とし、設定可能（`mm.app.password.bcrypt-strength`）とする。NFR-1.3（典型的な操作に対する妥当な応答性能）の範囲内に収まることを、Code Generation段階で実測確認する
- **NFR-02-02**: アクセストークンはステートレス（JWT署名検証のみ、DB照会不要）であり、リクエストごとの認証コストを最小化する

## 4. Reliability

- **NFR-02-03**: グローバル例外ハンドラの具体的な実装パターン（`@RestControllerAdvice`の構成等）はNFR Design段階で確定する。本ユニットではBR-API-01（統一エラーレスポンス形式）に準拠した例外処理を行う方針のみ確定する

## 5. Maintainability

- **NFR-02-04**（既存確定事項の再掲）: バックエンドのテストフレームワークはNFR-9.1のとおりJUnit5 + Mockitoとする
- **NFR-02-05**: バックエンドのProperty-Based TestingフレームワークはjqwikとするNFR-5.2の最終確定（Q5=A）。詳細はtech-stack-decisions.md参照

## 6. Usability — N/A

Functional Design（frontend-components.md）で対応済み。追加のUsability要件は設けない。

---

## 7. データアクセス（内部DB、レビュー指摘の反映）

requirements.md §2で内部DBのDBアクセス方式（JPA）・データベース種別（H2 Database）は既に確定済み。UNIT-02はUser・RegistrationToken・RefreshToken・AuditLogEntry等を実際に永続化する最初のユニットのため、具体的な実装方式をここで確定する。

- **NFR-02-06**: Spring Data JPA（リポジトリインターフェースによるCRUD）を用いる（Q8=A）
- **NFR-02-07**: スキーマ管理・マイグレーションはFlywayで行う（Q9=A）。Hibernateの`ddl-auto=update`による自動生成は、本番運用でのスキーマドリフトのリスクを避けるため使用しない
- **NFR-02-08**: H2 Databaseはファイルベース永続化モード（`jdbc:h2:file:...`）で運用する（Q10=A）。DBファイルパスは環境変数（`mm.app.datasource.path`等、詳細名称はCode Generation段階で確定）で指定する（NFR-2.3準拠）。開発・テストではインメモリモード（`jdbc:h2:mem:...`）も使用可とする
- **NFR-02-09**: コネクションプールはSpring Boot標準のHikariCP（デフォルト設定）を用いる。requirements.md §2「コネクションプール」の記載は対象RDBMS（UNIT-03でのユーザ設定接続）向けであり、内部DBには適用されない

---

## 8. Security Baseline拡張 該当ルール評価

| ルール | 判定 | 本ユニットでの対応方針 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | 該当 | パスワードハッシュ・登録トークン・リフレッシュトークンはいずれもハッシュ化して保存（BR-PWD-03, BR-REG-02, FR-3.2）。DB接続のTLS化は環境設定（UNIT-03のRDBMS接続とは別に、内部DB自体の接続設定）で対応。詳細はNFR Design段階 |
| SECURITY-02（ネットワーク中継のアクセスログ） | N/A | 自己ホスト運用でロードバランサ・APIゲートウェイ・CDNを本ユニットのスコープでは使用しない |
| SECURITY-03（アプリケーションログ） | 該当 | SLF4J + Logback（Q6=A）。詳細はtech-stack-decisions.md参照 |
| SECURITY-04（HTTPセキュリティヘッダ） | 該当 | NFR-4.2で必須ヘッダは確定済み。実装パターン（Spring Securityのデフォルト設定を用いるか等）はNFR Design段階で確定 |
| SECURITY-05（入力バリデーション） | 該当 | 全APIエンドポイントでBean Validation（`jakarta.validation`）等による型・長さ・形式チェックを行う。詳細はNFR Design段階 |
| SECURITY-06（最小権限IAM） | N/A | クラウドIAMポリシーを用いない自己ホスト構成のため |
| SECURITY-07（ネットワーク制限設定） | N/A | Infrastructure DesignがSKIPのユニットであり、ネットワーク構成は対象外 |
| SECURITY-08（アプリ層アクセス制御） | 該当 | NFR-4.6。Spring Security + OAuth2 Resource Server（Q1=A）でJWT検証、ロールベースの認可を実現。管理者専用エンドポイント（`/api/admin/**`）はサーバ側ロールチェックを必須とする |
| SECURITY-09（ハードニング） | 該当 | 初期管理者アカウントの機微情報は環境変数経由のみで受け渡し、ログ出力しない（Q7=A）。本番エラーレスポンスはスタックトレース等の内部情報を含めない（BR-API-01の統一形式に準拠） |
| SECURITY-10（サプライチェーン） | 該当 | UNIT-01でOWASP Dependency-Checkプラグイン導入済み（backend）。新規追加する`cherry-mustache-core`にも同プラグインが既に構成済み（reference/mustache-engine/cherry-mustache-core/build.gradle.kts）。UNIT-02のCode Generationで両方の実行を確認する |
| SECURITY-11（セキュアデザイン） | 該当 | 認証・認可ロジックは`cherry.mastermeister.auth`/`cherry.mastermeister.registration`パッケージに分離済み（package-by-feature）。登録開始（Step1）エンドポイントにレート制限を新設（Q4=A、`LoginAttemptGuard`と同様の仕組み） |
| SECURITY-12（認証・認証情報管理） | 該当 | 本ユニットの中核。パスワードポリシー（BR-PWD-01〜02）、BCryptハッシュ化（Q3=A）、ブルートフォース対策（BR-LOGIN-01）、セッション管理（JWTステートレス＋リフレッシュトークンのDB管理）、認証情報のハードコード禁止（Q7=A）をすべて満たす。MFAはNFR-4.1で文書化された適用除外あり |
| SECURITY-13（整合性検証） | 該当（デフォルトで対応済み） | JacksonのデフォルトはポリモーフィックデシリアライズやTyping情報の受け入れを行わないため、明示的な設定なしに安全側の挙動となる。CI/CDパイプラインの整合性はUNIT-10のスコープ |
| SECURITY-14（アラート・監視） | 該当（既存方針を踏襲） | NFR-4.5のとおり、ログベースの軽量な検知で足りるものとし、本格的な監視ダッシュボードは求めない |
| SECURITY-15（例外処理・フェイルセーフ） | 該当 | グローバル例外ハンドラでBR-API-01形式に変換し、認証・認可の失敗時はfail closed（拒否）を徹底する。実装パターンはNFR Design段階で確定 |

## 9. Property-Based Testing拡張

- **PBT-01**（Functional Design段階でのプロパティ識別）: 対応済み。business-logic-model.mdで「No PBT properties identified」と判定・記録済み（リフレッシュトークンのローテーション・再利用検知は状態数が限定的な有限状態機械のため、ユニットテストでの網羅で十分と判断）
- **PBT-09**（フレームワーク選定）: 本ユニットでjqwikに最終確定（Q5=A）。プロジェクト全体で共有する初のバックエンドPBTフレームワーク選定であり、後続ユニット（UNIT-04権限判定・YAML入出力、UNIT-07 SQL生成等）が利用する
- PBT-02〜08、PBT-10は、本ユニットにPBT対象プロパティが存在しないため適用対象なし（N/A）。フレームワーク自体の整備（Code Generation段階でのbuild.gradle.kts設定等）はPBT-09の一部として行う

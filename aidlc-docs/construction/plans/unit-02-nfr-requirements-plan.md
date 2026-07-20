# UNIT-02 ユーザ登録・認証 - NFR Requirements 計画

## Unit Context

Functional Design成果物（business-logic-model.md, business-rules.md, domain-entities.md, frontend-components.md）を踏まえ、非機能要件とバックエンドの技術選定を確定する。UNIT-02は認証・認可の中核ユニットであり、Security Baseline拡張の該当ルールが多い。

## 計画チェックリスト

- [ ] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: 成果物を作成する（nfr-requirements.md, tech-stack-decisions.md）
- [ ] Step D: 完了メッセージを提示し、承認を得る

## NFRカテゴリ評価

| カテゴリ | 判定 | 理由 |
|---|---|---|
| Scalability | N/A | NFR-1.1（同時利用者約10名）。本ユニット固有のスケーリング設計は不要 |
| Availability | N/A | requirements.mdにHA・DR要件の記載なし。単一インスタンスの自己ホスト運用を前提とする既存方針（NFR-2.x）と整合 |
| Performance | 該当 | パスワードハッシュのコスト係数がレスポンス時間に影響するため、アルゴリズム選定時に考慮（NFR-1.3） |
| Security Baseline | 該当（多数） | 下記「Security Baseline該当ルール」参照 |
| Reliability | 該当 | グローバル例外ハンドラの要否（実装パターン自体はNFR Design段階で決定） |
| Maintainability | 該当（一部確定済み） | NFR-9.1でバックエンドはJUnit5+Mockitoと既に確定済み。追加の技術選定はPBTフレームワークのみ |
| Usability | N/A | Functional Design（frontend-components.md）で対応済み |
| Property-Based Testing拡張 | 該当（PBT-09） | PBT-01はFunctional Designで対応済み（本ユニットはNo PBT properties identified）。PBT-09（フレームワーク最終確定）がNFR Requirements段階の対象（NFR-5.2） |

## Security Baseline該当ルール評価

| ルール | 判定 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | 該当 | パスワードハッシュ・トークンハッシュの保存、DB接続のTLS化 |
| SECURITY-02（ネットワーク中継のアクセスログ） | N/A | 自己ホスト運用でロードバランサ・APIゲートウェイ・CDNを本ユニットでは使用しない |
| SECURITY-03（アプリケーションログ） | 該当 | ログ出力基盤の選定が必要 |
| SECURITY-04（HTTPセキュリティヘッダ） | 該当（要件は確定済み） | NFR-4.2で必須ヘッダは既に列挙済み。実装パターンはNFR Design段階 |
| SECURITY-05（入力バリデーション） | 該当 | 全APIエンドポイント対象（NFR-4.3） |
| SECURITY-06（最小権限IAM） | N/A | クラウドIAMポリシーを用いない自己ホスト構成のため |
| SECURITY-07（ネットワーク制限設定） | N/A | Infrastructure DesignがSKIPのユニットであり、ネットワーク構成は対象外 |
| SECURITY-08（アプリ層アクセス制御） | 該当 | NFR-4.6、JWT検証・認可はUNIT-02の中核 |
| SECURITY-09（ハードニング） | 該当 | 初期管理者アカウント設定、エラーレスポンスの情報漏洩防止 |
| SECURITY-10（サプライチェーン） | 該当（一部確定済み） | UNIT-01でOWASP Dependency-Checkプラグイン導入済み。新規追加する`cherry-mustache-core`にも同様の構成が必要（既にbuild.gradle.ktsに含まれている） |
| SECURITY-11（セキュアデザイン） | 該当 | 認証・認可ロジックの分離（既にpackage-by-featureで対応）、レート制限（登録エンドポイントの濫用防止が未検討） |
| SECURITY-12（認証・認証情報管理） | 該当 | 本ユニットの中核。MFAはNFR-4.1で文書化された適用除外あり |
| SECURITY-13（整合性検証） | 該当（デフォルトで対応見込み） | Jacksonのデフォルトはポリモーフィックデシリアライズを行わないため安全。明示的なタイプ指定を追加しない限り対応不要 |
| SECURITY-14（アラート・監視） | 該当（既に方針確定済み） | NFR-4.5で軽量な仕組みとする方針が既に確定 |
| SECURITY-15（例外処理・フェイルセーフ） | 該当 | グローバル例外ハンドラの要否、BR-API-01との整合 |

## 質問

### Question 1
認証基盤のフレームワーク構成について

A) Spring Security + OAuth2 Resource Server機構（`JwtDecoder`等）でJWT検証を行う。Spring Bootとの親和性が高く、SECURITY-08のトークン検証要件を標準機構でカバーできる

B) Spring Securityは認可（ロールベースのアクセス制御）のみに使い、JWT発行・検証は独自の`ServletFilter`＋`jjwt`ライブラリで実装する（登録・ログイン・リフレッシュの業務ロジックとの統合をより細かく制御したい場合）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 2
JWT署名アルゴリズム・鍵管理について

A) 対称鍵（HS256）。環境変数（例: `mm.app.jwt.secret`）から読み込む。単独運用・小規模チームのため鍵配布の複雑さを避ける

B) 非対称鍵（RS256）。鍵ペアを環境変数またはファイルから読み込む。署名鍵と検証鍵を分離できる

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 3
パスワードハッシュアルゴリズムについて（FR-1.13, SECURITY-12）

A) BCrypt（Spring Security標準の`BCryptPasswordEncoder`）。コスト係数はデフォルト10とし、設定可能にする

B) Argon2（Spring Securityの`Argon2PasswordEncoder`）。より新しい推奨アルゴリズムだが計算コストが高く、応答性能への影響を要考慮

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 4
登録開始（Step1、メールアドレス送信）エンドポイントの濫用防止について（SECURITY-11）

A) `LoginAttemptGuard`と同様、メールアドレス単位で一定時間内の再送信回数を制限する仕組みを新設する（大量送信によるメール爆撃・スパム対策）

B) 現時点では実施しない（MVPスコープ外とし、実際の悪用が確認されてから対応する）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 5
バックエンドのProperty-Based Testingフレームワークについて（NFR-5.2、PBT-09。本ユニットが最初のバックエンドユニットのため今回最終確定する。本ユニット自体にはPBT対象プロパティはないが、後続ユニット（UNIT-04権限判定・YAML入出力、UNIT-07 SQL生成等）が利用する）

A) jqwik（JUnit5統合。ユーザー提供の`reference/mustache-engine/cherry-mustache-core`で既に採用実績があり、requirements.md NFR-5.2でも候補として明記済み）

B) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 6
アプリケーションログの出力形式について（SECURITY-03）

A) SLF4J + Logback（Spring Boot標準）。開発環境はコンソール出力（人間可読）、本番相当環境向けには構造化JSON出力（`logstash-logback-encoder`等）に切替可能な設定とする。集中ログ基盤（ELK等）への接続自体は本ユニットのスコープ外とする（NFR-4.5の軽量方針を踏襲）

B) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 7
初期管理者アカウント等の機微な設定情報の取り扱いについて（SECURITY-09, NFR-2.3）

A) 環境変数経由でのみ受け渡す（`mm.app.admin.bootstrap.password`等）。アプリ起動ログ・例外メッセージ・監査ログのいずれにも値を出力しない

B) Other（[Answer]: の後に内容を記述）

[Answer]: 

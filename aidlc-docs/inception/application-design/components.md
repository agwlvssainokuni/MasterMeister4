# コンポーネント定義

`application-design-plan.md`の回答（Q1=A エピック単位、Q2=A Strategy/Adapterパターン、Q4=A 実効権限判定を独立コンポーネント化）に基づき定義する。バックエンド（Spring Boot）のサービス層コンポーネントを対象とし、フロントエンド（React）の詳細はQ5=Aによりこのステージでは扱わない（§6参照）。

各コンポーネントは基本的にstories.mdのエピック単位に対応するが、Epic2（RDBMSセットアップ／アクセス制御）は責務が明確に異なる複数のコンポーネントに、Epic1・監査ログは横断的な補助コンポーネントに分解している。

---

## 1. ユーザ登録・認証ドメイン

### COMP-01: UserRegistrationService
- **目的**: 2段階（メール先行）ユーザ登録フローと管理者承認ワークフローを扱う
- **責務**: メールアドレス登録開始、登録トークン発行・検証、パスワード設定、パスワードポリシー適用（漏洩パスワードチェック含む）、管理者による承認/却下
- **対応要件**: FR-1.1〜FR-1.13
- **インターフェース概要**: REST層（`/api/registrations`, `/api/admin/users`）から呼び出される

### COMP-02: AdminBootstrapService
- **目的**: アプリ初回起動時の初期管理者アカウント自動作成
- **責務**: 環境変数からの管理者情報読込。アカウント作成自体はUserRegistrationServiceの専用メソッド（`createApprovedAccount()`）を呼び出して行い、パスワードハッシュ化等の共通ロジックの重複を避ける。起動ライフサイクルへのフックという性質上、UserRegistrationServiceとは別コンポーネントとして分離
- **対応要件**: FR-1.14

### COMP-03: AuthenticationService
- **目的**: ログイン・ログアウト・アクセストークン発行
- **責務**: 認証情報検証、アクセストークン（JWT）発行、ログアウト時のセッション無効化
- **対応要件**: FR-3.1, FR-3.2, FR-3.5, FR-3.6

### COMP-04: RefreshTokenService
- **目的**: リフレッシュトークンのローテーションと再利用検知
- **責務**: リフレッシュトークンの発行・ハッシュ化保存・検証、使用済みトークンの無効化、トークンファミリ単位の一括失効
- **対応要件**: FR-3.3, FR-3.4

### COMP-05: LoginAttemptGuard
- **目的**: ログイン試行のレート制限・アカウントロック
- **責務**: 失敗回数のカウント、閾値到達時のロック/レート制限判定
- **対応要件**: FR-3.7

### COMP-06: EmailNotificationService
- **目的**: 登録確認・承認結果等のメール送信
- **責務**: メールテンプレート適用（多言語対応、NFR-7.x）、SMTP送信
- **対応要件**: FR-1.2, FR-1.6

---

## 2. RDBMSセットアップドメイン

### COMP-07: RdbmsConnectionService
- **目的**: 対象RDBMS接続情報の登録・管理
- **責務**: 接続情報CRUD、接続パスワードの暗号化保存、コネクションプール構成
- **対応要件**: FR-2.1

### COMP-08: SchemaIntrospectionService
- **目的**: 対象RDBMSのスキーマ取込
- **責務**: テーブル/ビュー構造の読取、内部DBへの取込・更新
- **対応要件**: FR-2.2

### COMP-09: RdbmsDialectStrategy（インターフェース＋実装群）
- **目的**: MySQL/MariaDB/PostgreSQL/H2の方言差を吸収する（Q2=A Strategy/Adapterパターン）
- **責務**: スキーマ切替要否・切替方法（`SET search_path`等）、その他DB固有の差異をカプセル化
- **実装クラス**: `MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgresDialectStrategy`, `H2DialectStrategy`
- **対応要件**: FR-7.5（実行時スキーマ指定）、SchemaIntrospectionService/QueryExecutionServiceから利用

---

## 3. アクセス制御ドメイン

### COMP-10: ~~AccessControlService~~
**訂正（UNIT-04 Functional Design／NFR Designにて）**: UNIT-02の`registration`/`auth`分割前例に倣い、`group`パッケージの`GroupService`（グループ管理責務）と`permission`パッケージの`PermissionService`（主権限/補助権限CRUD責務、`GroupService`との命名一貫性のため`AccessControlService`から改称）の2コンポーネントに分割した（unit-of-work.mdのパッケージ対応表参照）。
- **目的**: アクセス権限設定・グループ管理
- **責務**: 主権限/補助権限のCRUD（`PermissionService`）、ユーザグループの作成・改名・削除・所属管理（`GroupService`）
- **対応要件**: FR-2.3〜FR-2.5, FR-2.13〜FR-2.15

### COMP-11: EffectivePermissionResolver
- **目的**: 実効権限の判定・合成（Q4=A、独立コンポーネント）
- **責務**: 主権限の階層優先判定、補助権限による作成・削除可否判定、グループとの合成、個別設定優先ルール、実効権限のキャッシュ管理（無効化含む）
- **対応要件**: FR-2.6〜FR-2.9
- **PBT対象**: はい（STORY-2.4）

### COMP-12: PermissionYamlService
- **目的**: アクセス権限設定のYAML入出力
- **責務**: YAMLエクスポート生成、全置換方式でのインポート、重複エントリ検出によるインポート拒否（トランザクション単位）
- **対応要件**: FR-2.10〜FR-2.12
- **PBT対象**: はい（STORY-2.5、ラウンドトリップ特性）

---

## 4. マスタメンテナンスドメイン

### COMP-13: MasterDataService
- **目的**: マスタデータの参照・絞込・編集・作成・削除
- **責務**: テーブル/ビュー一覧、ページング付きレコード一覧、絞込・SQL入力検索、単一トランザクションでのオールオアナッシング反映、権限判定（EffectivePermissionResolver利用）
- **対応要件**: FR-4.1〜FR-4.8

---

## 5. クエリドメイン

### COMP-14: QueryExecutionService
- **目的**: SQLの実行
- **責務**: 読み取り専用SQL実行、パラメータ検出・バインド、実行時スキーマ指定（RdbmsDialectStrategy利用）、ページング、実行結果のQueryHistoryServiceへの記録トリガー
- **対応要件**: FR-7.1〜FR-7.9, FR-6.4（保存クエリの実行）

### COMP-15: SavedQueryService
- **目的**: クエリの名前付き保存管理
- **責務**: 保存クエリCRUD、公開範囲（Public/Private）制御、作成者限定編集、論理非表示化
- **対応要件**: FR-6.1〜FR-6.3, FR-6.5〜FR-6.6

### COMP-16: QueryBuilderService
- **目的**: タブUI指定内容からのSQL生成、既存SQLのリバースエンジニアリング
- **責務**: スキーマ非修飾SQLの生成、既存SQL解析による構成要素への分解
- **対応要件**: FR-5.1〜FR-5.7
- **PBT対象**: はい（STORY-5.2）

### COMP-17: QueryHistoryService
- **目的**: クエリ実行履歴の記録・検索
- **責務**: 実行履歴の永続化、履歴一覧・絞込検索
- **対応要件**: FR-8.1〜FR-8.4

---

## 6. 横断ドメイン

### COMP-18: AuditLogService
- **目的**: 監査ログの記録・閲覧
- **責務**: 各種イベント（認証・管理操作・データアクセス）の構造化記録、管理者向け閲覧・絞込
- **対応要件**: §6.1〜6.3、STORY-9.1
- **連携方式**: Q3の回答に基づき、他コンポーネントからのAuditEvent（後述）を同期的に受信し、本業務トランザクションとは別トランザクションで記録する

### COMP-19: AuditEventPublisher（横断的インフラ）
- **目的**: 監査対象イベントの発行
- **責務**: Spring ApplicationEventとしてAuditEvent（種別・ユーザID・対象接続ID・操作種別・対象リソース・結果ステータスを含む）を発行する薄いユーティリティ。各業務コンポーネントがこれを呼び出す
- **対応要件**: §6.1（全イベント種別に対応）

---

## フロントエンド（高レベルのみ、Q5=A）

フロントエンドは機能エピック単位のモジュール構成（例: `registration/`, `access-control/`, `master-data/`, `query-builder/`, `query-save/`, `query-execution/`, `query-history/`, `audit-log/`）を想定するが、画面コンポーネント階層等の詳細は各ユニットのCode Generation段階で決定する。デザインシステム基盤（Epic0）のみ、他モジュールが依存する共通コンポーネント群として最優先で構築する。

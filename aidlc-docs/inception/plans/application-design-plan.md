# Application Design Plan

requirements.mdとstories.mdの分析に基づき、以下のコンポーネント・サービス層設計を進める。

## コンテキスト分析

**主要な機能領域**（stories.mdの10エピックに対応）:
デザインシステム基盤、ユーザ登録、RDBMSセットアップ、アクセス制御、ユーザ認証、マスタメンテナンス、クエリビルダー、クエリ保存、クエリ実行、クエリ履歴、監査ログ

**設計スコープ**: バックエンド（Spring Boot）のサービス層・コンポーネント設計を中心とする。フロントエンド（React）は機能単位のモジュール構成を高レベルで示すに留め、詳細はFunctional Design／Code Generation（ユニットごと）で扱う。

**複雑度**: Complex（多階層アクセス権限モデル、複数RDBMS方言吸収、JWTローテーション、動的SQL生成を含む）

---

## 実行チェックリスト

- [ ] Step A: components.md を作成する（コンポーネント定義・責務・インターフェース概要）
- [ ] Step B: component-methods.md を作成する（メソッドシグネチャ・入出力型。詳細な業務ルールはFunctional Designで扱う）
- [ ] Step C: services.md を作成する（サービス定義・オーケストレーションパターン）
- [ ] Step D: component-dependency.md を作成する（依存関係マトリクス・通信パターン・データフロー）
- [ ] Step E: application-design.md を作成する（上記4文書を統合したサマリ）
- [ ] Step F: 設計の完全性・一貫性を検証する（全FR-x.xがいずれかのコンポーネントでカバーされているか）

---

## 質問（Product Owner / Architect Assessment）

各設問に A/B/C... の記号で回答してください。当てはまる選択肢がない場合は最後の「Other」を選び、[Answer]: の後ろに内容を記述してください。

### Question 1: コンポーネント粒度・組織方針
バックエンドのコンポーネント（サービスクラス）は、どのような単位で分割しますか？

A) stories.mdのエピック単位（10個程度）に対応させる（例: UserRegistrationService, AccessControlService, QueryBuilderService等）。エピックとサービスの対応が明確でトレーサビリティが高い

B) より粒度を細かくし、FR-x.xグループ単位でさらに分割する（例: アクセス制御を「権限設定サービス」「実効権限判定サービス」「YAML入出力サービス」に分ける）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 2: 複数RDBMS対応の設計パターン
MySQL/MariaDB/PostgreSQL/H2という複数の対象RDBMSの方言差（スキーマ切替の要否等）を、どのように吸収する設計にしますか？

A) Strategy/Adapterパターンで、DB種別ごとの方言クラス（例: `MySqlDialectHandler`, `PostgresDialectHandler`）を用意し、共通インターフェースで呼び分ける

B) サービス内でDB種別によるif/switch分岐を行う、よりシンプルな実装とする（コンポーネント数を増やさない）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 3: 監査ログ記録の連携方式
各機能（ユーザ登録、権限変更、データアクセス等）から監査ログサービスへの記録は、どのように連携させますか？

A) 各サービスが監査ログサービスを直接呼び出す（同期・明示的な呼び出し）

B) Spring ApplicationEvent等のイベント機構を使い、各サービスはイベントを発行するだけで、監査ログサービスが非同期または別リスナーで記録する（サービス間の結合度を下げる）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 4: 実効権限判定ロジックの配置
アクセス権限の実効権限判定・合成ロジック（PBT対象、STORY-2.4）は、マスタメンテナンス・クエリビルダー・クエリ実行など複数の機能から参照される横断的ロジックです。どこに配置しますか？

A) 独立した権限判定コンポーネント（例: `EffectivePermissionResolver`）として切り出し、他の全サービスがこれを参照する

B) アクセス制御サービス内のメソッドとして持たせ、他サービスはアクセス制御サービス経由で呼び出す

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 5: フロントエンドのモジュール構成
フロントエンド（React）側のコンポーネント設計はこのApplication Designステージでどこまで扱いますか？

A) このステージでは扱わず、機能エピック単位のモジュール構成があることだけをapplication-design.mdに触れておく程度にとどめ、詳細は各ユニットのCode Generation段階で決定する

B) 主要な画面コンポーネント階層（例: ページ／レイアウト／フォーム部品）まである程度定義しておく

X) Other（[Answer]: の後に内容を記述）

[Answer]:

---

すべての設問に回答後、完了したことを教えてください。回答内容を分析し、曖昧な点があれば追加の確認質問を作成します。

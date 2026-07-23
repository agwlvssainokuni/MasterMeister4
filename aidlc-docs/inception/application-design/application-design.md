# Application Design サマリ

`application-design-plan.md`の回答（Q1=A エピック単位、Q2=A Strategy/Adapterパターン、Q3=Bカスタム イベント機構＋同期＋別トランザクション、Q4=A 独立した実効権限判定コンポーネント、Q5=A フロントエンド詳細は後続ユニットへ）に基づき作成した設計の統合サマリ。詳細は各文書を参照。

- コンポーネント定義: [components.md](components.md)
- メソッド一覧: [component-methods.md](component-methods.md)
- サービス・オーケストレーション: [services.md](services.md)
- 依存関係: [component-dependency.md](component-dependency.md)

## 設計概要

バックエンドを19のコンポーネントに分解した。stories.mdの10エピックを基本単位としつつ、以下の観点で意図的に分解・統合している。

- **Epic2（RDBMSセットアップ／アクセス制御）**: 責務が明確に異なるため、RDBMS基盤系（RdbmsConnectionService, SchemaIntrospectionService, RdbmsDialectStrategy）とアクセス制御系（~~AccessControlService~~ 訂正（UNIT-04 Functional Design／NFR Designにて）: GroupService, PermissionService, EffectivePermissionResolver, PermissionYamlService）の6コンポーネント（訂正後は7コンポーネント）に分解
- **Epic1（ユーザ登録）**: 起動時のみ動作するAdminBootstrapServiceと、メール送信を担うEmailNotificationServiceを補助コンポーネントとして分離
- **Epic3（ユーザ認証）**: トークンローテーション（RefreshTokenService）とログイン試行制限（LoginAttemptGuard）を、責務の異なる補助コンポーネントとして分離
- **横断的コンポーネント**: EffectivePermissionResolver（実効権限判定、複数ドメインサービスから参照）、RdbmsDialectStrategy（複数RDBMS方言吸収）、AuditEventPublisher/AuditLogService（監査ログ記録）の3系統は、いずれか特定のエピックに属さない横断的コンポーネントとして独立させた

## 主要な設計判断

| 判断事項 | 内容 | 根拠 |
|---|---|---|
| コンポーネント粒度 | エピック単位を基本とし、責務が明確に異なる場合は分解 | Q1=A |
| 複数RDBMS対応 | Strategy/Adapterパターン（RdbmsDialectStrategy） | Q2=A |
| 監査ログ連携 | イベント発行（AuditEventPublisher）、ただし同期実行・別トランザクション | Q3=Bカスタム |
| 実効権限判定の配置 | 独立コンポーネント（EffectivePermissionResolver） | Q4=A |
| フロントエンド設計の深度 | 高レベル言及のみ、詳細は各ユニットのCode Generationで決定 | Q5=A |

## 要件カバレッジ検証（Step F）

requirements.mdの全FR-x.xが、いずれかのコンポーネントの対応要件として明記されていることを確認した。

- FR-0.x（デザインシステム基盤）: フロントエンド固有のためバックエンドコンポーネント定義の対象外（components.md「フロントエンド」節に記載）
- FR-1.x: COMP-01, COMP-02, COMP-06でカバー
- FR-2.x: COMP-07〜COMP-12でカバー
- FR-3.x: COMP-03〜COMP-05でカバー
- FR-4.x: COMP-13でカバー
- FR-5.x: COMP-16でカバー
- FR-6.x: COMP-15（一部COMP-14と連携）でカバー
- FR-7.x: COMP-14でカバー
- FR-8.x: COMP-17でカバー
- §6（監査ログ）: COMP-18, COMP-19でカバー

未カバーのFR-x.xは無し。循環依存も存在しない（component-dependency.md参照）。

# Unit of Work 定義

`unit-of-work-plan.md`の回答（Q1=A 登録・認証統合、Q2=A 厳密な逐次実行、Q3=A ユニットごとの単体動作確認、Q4=A 10区分維持、Q5=A package-by-feature、ベースパッケージ`cherry.mastermeister`）に基づき、10ユニットを定義する。

プロジェクト形態はモノリス（単一Spring Bootバックエンド＋単一Reactフロントエンド）。各Unit of Workは開発の作業単位であり、独立デプロイ可能なサービスではない。

---

## UNIT-01: デザインシステム基盤
- **優先度**: P0（最優先）
- **対応エピック**: Epic 0
- **対応ストーリー**: STORY-0.1, STORY-0.2
- **責務**: サードパーティUIライブラリを使わない独自の共通UIコンポーネント（配色・タイポグラフィ・ボタン・フォーム部品等）の構築。加えて、画面グランドデザイン（ヘッダー・ナビゲーション・サイドバー・フッター等の共通レイアウト枠組み）の確定と、代表画面（ログイン、ユーザ登録、管理者ダッシュボード、マスタメンテナンス、権限設定）のモック作成（FR-0.3〜0.5）
- **対応コンポーネント**: フロントエンドのみ（バックエンドコンポーネントなし）。代表画面モックはデザイン確認用であり、各ユニットの本実装とは別物（各ユニットのFunctional Design／Code Generationで改めて実装する）
- **前提ユニット**: なし（最初に着手）
- **リポジトリ骨格構築**: このユニットのCode Generationにて、`settings.gradle.kts`／`backend`（最小限のSpring Boot起動クラスのみ。業務コンポーネントはUNIT-02以降で追加）／`frontend`／`devenv`のプロジェクト構造一式を最初に構築する（プロジェクト構成の再検討、INCEPTION継続審議で決定。requirements.md §4参照）。backendの最小スケルトンには、NFR-7.3（バックエンド・フロントエンドともにi18n基盤を最初の実装ユニットから導入）に基づきSpring側のi18n基盤（`MessageSource`設定、`messages_ja.properties`/`messages_en.properties`の空の雛形）を、NFR-4.4（依存関係の脆弱性スキャン）に基づきOWASP Dependency-Check Gradleプラグインを、それぞれ合わせて用意する
- **参考資材の依頼（要対応）**: UNIT-01のCode Generation着手時（Part 1 Planning開始時）に、デザインシステム構築の参考としたい既存資材（配色・コンポーネント実装例等）をプロジェクトディレクトリに配置してもらうよう、ユーザーに依頼すること。配置場所・取り扱い（誤コミット防止の`.gitignore`要否等）はその時点でユーザーと確認する（INCEPTION継続審議で決定）

## UNIT-02: ユーザ登録・認証
- **優先度**: P1
- **対応エピック**: Epic 1, Epic 3
- **対応ストーリー**: STORY-1.1〜1.4, STORY-3.1〜3.3
- **責務**: メール先行registrationフロー、管理者承認、初期管理者ブートストラップ、JWT認証（ログイン/ログアウト/リフレッシュ/ローテーション/再利用検知/ログイン試行制限）。加えて、以降の全ユニットが利用する**監査ログ記録基盤**（AuditEventPublisher／AuditLogServiceの書き込み経路）をこのユニットで最初に構築する
- **対応コンポーネント**: COMP-01〜COMP-06, COMP-18（記録機能のみ）, COMP-19
- **前提ユニット**: UNIT-01（共通UIコンポーネント利用）

## UNIT-03: RDBMSセットアップ
- **優先度**: P2
- **対応エピック**: Epic 2（前半）
- **対応ストーリー**: STORY-2.1, STORY-2.2
- **責務**: 対象RDBMS接続の登録・管理、スキーマ取込、DB方言吸収（Strategy/Adapterパターン）
- **対応コンポーネント**: COMP-07, COMP-08, COMP-09
- **前提ユニット**: UNIT-01, UNIT-02（管理者ログイン・監査ログ基盤が必要）

## UNIT-04: アクセス制御
- **優先度**: P3
- **対応エピック**: Epic 2（後半）
- **対応ストーリー**: STORY-2.3〜2.6
- **責務**: 主権限/補助権限のCRUD、実効権限判定・合成ロジック（PBT対象）、YAML入出力（PBT対象）、グループ管理
- **対応コンポーネント**: COMP-10, COMP-11, COMP-12
- **前提ユニット**: UNIT-01, UNIT-02, UNIT-03（取込済みスキーマに対して権限を設定するため）

## UNIT-05: マスタメンテナンス
- **優先度**: P4
- **対応エピック**: Epic 4
- **対応ストーリー**: STORY-4.1〜4.4
- **責務**: テーブル/ビュー一覧・レコード一覧、絞込・SQL入力検索、レコード編集の一括反映（オールオアナッシング）、レコード作成・削除
- **対応コンポーネント**: COMP-13
- **前提ユニット**: UNIT-01〜UNIT-04（実効権限判定に依存）

## UNIT-06: クエリ保存・実行
- **優先度**: P5
- **対応エピック**: Epic 6, Epic 7
- **対応ストーリー**: STORY-6.1, 6.2, STORY-7.1〜7.3
- **責務**: SQL手入力実行、パラメータ対応、実行時スキーマ指定、保存クエリのCRUD・公開範囲・実行・編集権限・非表示化
- **対応コンポーネント**: COMP-14, COMP-15
- **前提ユニット**: UNIT-01〜UNIT-04（RdbmsDialectStrategy, EffectivePermissionResolverに依存）

## UNIT-07: クエリビルダー
- **優先度**: P5
- **対応エピック**: Epic 5
- **対応ストーリー**: STORY-5.1〜5.3
- **責務**: タブUIによるSQL組み立て、SQL生成（PBT対象）、実行・保存への連携、既存SQLからのリバースエンジニアリング
- **対応コンポーネント**: COMP-16
- **前提ユニット**: UNIT-01〜UNIT-04, UNIT-06（実行・保存への連携を本実装するため）

## UNIT-08: クエリ履歴
- **優先度**: P5
- **対応エピック**: Epic 8
- **対応ストーリー**: STORY-8.1, 8.2
- **責務**: クエリ実行履歴の一覧・絞込、履歴からの画面遷移（実行/保存/ビルダー）
- **対応コンポーネント**: COMP-17
- **前提ユニット**: UNIT-01〜UNIT-04, UNIT-06, UNIT-07（実行記録の蓄積と、遷移先である実行・保存・ビルダーが揃っている必要があるため）

## UNIT-09: 監査ログ閲覧
- **優先度**: P4〜P5
- **対応エピック**: Epic 9
- **対応ストーリー**: STORY-9.1
- **責務**: UNIT-02で構築した監査ログ記録基盤に蓄積されたログの、管理者向け閲覧・絞込機能
- **対応コンポーネント**: COMP-18（閲覧機能）
- **前提ユニット**: UNIT-01, UNIT-02（記録基盤が必要）。他ユニットの記録内容も閲覧対象となるため、実質的にはUNIT-05以降のいずれかのタイミングで着手するのが自然

## UNIT-10: CI/CD
- **優先度**: 最終
- **対応エピック**: なし（NFR-10.1〜10.3に対応、ユーザストーリー化していない開発タスク）
- **責務**: GitHub Actionsによるビルド・テスト自動化、タグpushトリガーでのGitHub Releases作成
- **対応コンポーネント**: なし（インフラ/開発プロセス）
- **前提ユニット**: UNIT-01〜UNIT-09すべて（最終ユニット）

---

## コード構成方針（Greenfield、Q5=A）

- **プロジェクト構造**: requirements.md §4のとおり `backend/`, `frontend/`, `devenv/` の3ディレクトリ構成を維持。Gradleマルチモジュール構成とし、`frontend`を`backend`のサブプロジェクトとして取り込む。リリースビルド時のみGradle Node Pluginでフロントエンドをビルドし単一WARに内包する（`bootWar`タスク、`SpringBootServletInitializer`継承。NFR-2.2/2.6準拠）。バックエンド単体ビルド・フロントエンド単体開発（`npm run dev`）はそれぞれ従来どおり独立して行える（プロジェクト構成の再検討、INCEPTION継続審議で決定）
- **骨格構築のタイミング**: `settings.gradle.kts`・`backend`（最小起動クラスのみ）・`frontend`・`devenv`一式は、最初に着手するUNIT-01のCode Generationで一括構築する（後続ユニットでの構成変更を避けるため。UNIT-01時点ではbackendは中身のない最小構成）
- **バックエンドパッケージ方針**: package-by-feature。ベースパッケージは `cherry.mastermeister`
- **ユニット→パッケージ対応**:

| ユニット | 主パッケージ |
|---|---|
| UNIT-02（ユーザ登録・認証） | `cherry.mastermeister.registration`, `cherry.mastermeister.auth` |
| UNIT-02（監査ログ基盤） | `cherry.mastermeister.audit` |
| UNIT-03（RDBMSセットアップ） | `cherry.mastermeister.rdbmsconnection` |
| UNIT-04（アクセス制御） | `cherry.mastermeister.accesscontrol` |
| UNIT-05（マスタメンテナンス） | `cherry.mastermeister.masterdata` |
| UNIT-06（クエリ保存・実行） | `cherry.mastermeister.query` |
| UNIT-07（クエリビルダー） | `cherry.mastermeister.querybuilder` |
| UNIT-08（クエリ履歴） | `cherry.mastermeister.queryhistory` |
| UNIT-09（監査ログ閲覧） | `cherry.mastermeister.audit`（UNIT-02の同パッケージを拡張） |
| 共通基盤（セキュリティ設定、JWTフィルタ、例外ハンドリング等） | `cherry.mastermeister.common` |

- **フロントエンド構成方針**: 機能エピック単位のモジュール構成（`registration/`, `auth/`, `access-control/`, `master-data/`, `query-builder/`, `query-save/`, `query-execution/`, `query-history/`, `audit-log/`）を基本とし、UNIT-01で構築する共通UIコンポーネントを全モジュールが参照する。詳細な階層構造は各ユニットのCode Generation段階で決定する（application-design-plan.md Q5=A）

## 動作確認方針（Q3=A）

各ユニット完了時点でそのユニット単体の動作確認（ユニットテスト・簡単な手動確認）を行う。全ユニットを通した統合テストはBuild and Testステージでまとめて実施する。

## 着手順序（Q2=A）

UNIT-01からUNIT-10まで、厳密な逐次実行とする。前ユニットの完了・承認を待ってから次のユニットに着手する。

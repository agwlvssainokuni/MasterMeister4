# UNIT-01 デザインシステム基盤 - Code Generation Plan（Part 1）

本計画は、Code Generation（Part 2実行）における唯一の実行根拠（single source of truth）である。

## Unit Context

- **対応ストーリー**: STORY-0.1（共通デザイン基盤の構築）, STORY-0.2（個別画面デザインの段階的整備）, STORY-0.3（代表画面モックによる早期デザイン確認）
- **対応コンポーネント**: なし（COMP-01〜19はいずれも対象外。UNIT-01はフロントエンドのみ、業務コンポーネントを持たない）
- **前提ユニット**: なし（最初に着手するユニット）
- **依存関係**: なし
- **参照した設計成果物**:
  - `aidlc-docs/construction/unit-01/functional-design/frontend-components.md`
  - `aidlc-docs/construction/unit-01/nfr-requirements/nfr-requirements.md`, `tech-stack-decisions.md`
  - `aidlc-docs/construction/unit-01/nfr-design/nfr-design-patterns.md`, `logical-components.md`
  - `reference/design-system/`, `reference/mocks/`（参考資材。取込方針は上記frontend-components.mdに確定済み）
- **後続ユニットへの申し送り**: `backend`には本ユニットで最小起動クラス・i18n基盤・依存関係スキャンプラグインのみ用意する。業務コンポーネント（COMP-01等）はUNIT-02以降で追加する

## 未決定だった技術選定の補足

- **フロントエンドルーティング**: React Router（`react-router-dom`）を採用する。SPAのルーティングとして標準的な選択肢であり、`/mock/*`のdevビルド限定ルート分離にも利用する

## Icon一覧（ベースライン）

Step 3.5（`Icon`自作SVGアイコンセット）で作成するアイコンのベースライン。過不足があれば以降のステップ・後続ユニットで随時追加する。

| アイコン名 | 用途 |
|---|---|
| `menu` | SideNavの開閉（ハンバーガーメニュー） |
| `chevron-left` / `chevron-right` | Paginationのページ送り、SideNav階層の開閉 |
| `chevron-down` / `chevron-up` | ドロップダウン・折りたたみの開閉表示 |
| `close` | Modal・Toast・FilterBarのクリア操作 |
| `sun` / `moon` | ThemeToggle（ライト/ダーク切替） |
| `user` | HeaderControl（アカウント表示） |
| `logout` | HeaderControl（ログアウト導線） |
| `globe` | LanguageSwitcher（言語切替） |
| `check` / `check-circle` | Alert(success)、管理者ダッシュボードの承認アクション |
| `warning-triangle` | Alert(warning)、ConfirmDialog |
| `x-circle` | Alert(danger)、管理者ダッシュボードの却下アクション |
| `info` | Alert(info) |
| `edit` | DataTable行アクション、マスタメンテナンス画面の編集 |
| `delete` | DataTable行アクション、マスタメンテナンス画面の削除 |
| `add` | マスタメンテナンス画面のレコード追加 |
| `save` | 各種保存ボタンの補助アイコン |
| `search` | FilterBarの検索 |
| `copy` | CodeBlockのコピー操作 |
| `wrap-text` | CodeBlockの折り返し切替 |
| `sort` | DataTable列見出しのソート表示（表示のみ、実ソートロジックは後続ユニット） |

## 計画チェックリスト

### 1. Project Structure Setup（Greenfield）

- [x] Step 1.1: ルートに`settings.gradle.kts`を作成し、`backend`・`frontend`をサブプロジェクトとして定義する
- [x] Step 1.2: `backend/build.gradle.kts`を作成する（Spring Boot 4.1, Java 25, `war`パッケージング, OWASP Dependency-Check Gradleプラグイン, 最小限の依存関係）
- [x] Step 1.3: `backend/src/main/java/cherry/mastermeister/MasterMeisterApplication.java`を作成する（`SpringBootServletInitializer`継承、`java -jar app.war`実行と外部Tomcat WARデプロイの両対応）
- [x] Step 1.4: backend i18n基盤を作成する（`MessageSource`設定クラス、`messages_ja.properties`/`messages_en.properties`の空の雛形）
- [x] Step 1.5: `backend/src/main/resources/application.yml`を作成する（最小設定、環境変数プレースホルダー）
- [x] Step 1.6: `frontend/`をVite + React 19 + TypeScriptでスキャフォールドする（`package.json`, `tsconfig.json`, `vite.config.ts`等）
- [x] Step 1.7: `frontend`にoxlint + Prettier（セミコロンなし、シングルクォート）を設定する
- [x] Step 1.8: `frontend/build.gradle.kts`にGradle Node Plugin（`com.github.node-gradle.node`）を設定し、`npmInstall`/`npmBuild`タスクを定義する
- [x] Step 1.9: `backend`の`bootWar`タスクが`frontend`の`npmBuild`成果物（`frontend/dist`）をbackendの静的リソースへコピーする処理に依存する構成にする（`backend:build`単体には影響しないことを確認） — `providedRuntime`は`spring-boot-starter-tomcat-runtime`を使用（`spring-boot-starter-tomcat`だと`spring-web`まで除外される問題があったため）
- [x] Step 1.10: `devenv/docker-compose.yml`を作成する（MailPit, MySQL/MariaDB/PostgreSQLコンテナ）
- [x] Step 1.11: 全ソースファイルにApache License 2.0ヘッダーコメントを付与する方針を適用する（以降の全生成ステップで継続）
- [x] Step 1.12: **検証チェックポイント**: `./gradlew :backend:build`（backend単体ビルド、frontendを巻き込まないことを確認）、`npm run build`（frontend）、`./gradlew :backend:bootWar`（統合WAR、`java -jar`起動・HTTP 200・静的配信を実機確認）がすべて通ることを確認した

### 2. Frontend Components Generation - デザイントークン・プロバイダ

- [x] Step 2.1: デザイントークン（2層: プリミティブ`--mm-palette-*` + セマンティック`--mm-color-*`/`--mm-font-*`/`--mm-space-*`等、ライト/ダーク両対応）をCSSとして作成する
- [x] Step 2.2: セルフホストフォント（本文用・SQL/コード表示用等幅フォント）を`@fontsource`経由で導入し、`font-display: swap`を設定する
- [x] Step 2.3: `ThemeProvider`を作成する（light/dark/system、`data-theme`属性切り替え、`localStorage`、`matchMedia`）
- [x] Step 2.4: i18n初期化（react-i18next、`common`/`design-system`名前空間、日本語・英語リソース、`navigator.language`検出、`localStorage`保存）を作成する
- [x] Step 2.5: `ErrorBoundary`を作成する（コンソール出力のみ、汎用フォールバックUI）

### 3. Frontend Components Generation - 基本部品・フォーム

- [x] Step 3.1: `Button`を作成する（`IconButton`含む。ローディング表示は`Spinner`コンポーネント非依存の内蔵CSSスピナーとし、Section 5との依存順序を回避）
- [x] Step 3.2: `TextInput`を作成する（`PasswordInput`/`TextArea`/`Select`/`SearchInput`も併せて作成。参考実装の構成に合わせて拡張）
- [x] Step 3.3: `Choice`（`Checkbox` / `RadioGroup` / `Switch`）を作成する
- [x] Step 3.4: `FormField`を作成する（`cloneElement`方式でid/aria属性を子要素へ注入）
- [x] Step 3.5: `Icon`（自作SVGアイコンセット）を作成する。「Icon一覧（ベースライン）」の20種に加え、`PasswordInput`の表示切替用に`eye`/`eye-off`を追加

### 4. Frontend Components Generation - グランドデザイン

- [x] Step 4.1: `PublicLayout`を作成する
- [x] Step 4.2: `AppShell` / `Header` / `SideNav` / `Footer`を作成する（タブレット幅ブレークポイント対応含む。Header/SideNavは参考実装に合わせAppShellに内包する構成、Footerは独立コンポーネント）
- [x] Step 4.3: `HeaderControl` / `LanguageSwitcher` / `ThemeToggle`を作成する（HeaderControlは共有CSSモジュール。ヘッダーのユーザ情報・ログアウト導線はAppShell内にプレースホルダーとして実装）
- [x] Step 4.4: SideNavのナビゲーション項目（全10ユニット見込み、frontend-components.md §1.3参照）を実装する（`navigation.ts`の`useDefaultNavItems`、React Router連携）

### 5. Frontend Components Generation - 表示・フィードバック

- [x] Step 5.1: `Card` / `AuthCard`を作成する
- [x] Step 5.2: `PageHeader`を作成する
- [x] Step 5.3: `DataTable`を作成する（`Table`を土台に、列定義・簡易表示のみ）
- [x] Step 5.4: `Pagination`を作成する（見た目のみ）
- [x] Step 5.5: `EmptyState`を作成する
- [x] Step 5.6: `Alert`（tone: info/success/warning/danger）を作成する（各toneにIcon: info/check-circle/warning-triangle/x-circleを付与）
- [x] Step 5.7: `Badge`を作成する
- [x] Step 5.8: `Spinner`を作成する
- [x] Step 5.9: `Modal`を作成する（汎用の`Overlay`背景幕コンポーネントは存在しない。Modal内部で直接処理する構成に修正）
- [x] Step 5.10: `ConfirmDialog`を作成する（`Modal`を土台に構築）
- [x] Step 5.11: `FilterBar`を作成する（簡易版）
- [x] Step 5.12: `Tabs`を作成する
- [x] Step 5.13: `Toast`を作成する
- [x] Step 5.14: `CodeBlock`を作成する
- [x] Step 5.16: `Dropdown`を作成する（HeaderControlのユーザーメニュー等で使用。キーボード操作・フォーカストラップ対応）
- [x] Step 5.17: `Tooltip`を作成する（ホバー/フォーカスで表示する補足情報）
- [x] Step 5.15: `KeyValueList`を作成する

### 6. Frontend Components Unit Testing

- [x] Step 6.1: デザイントークン・`ThemeProvider`・i18n初期化・`ErrorBoundary`のユニットテストを作成する（Vitest + React Testing Library）
- [x] Step 6.2: 基本部品・フォーム（Button, TextInput, Choice, FormField, Icon）のユニットテストを作成する（Iconは静的SVG描画のみのため個別テストを省略）
- [x] Step 6.3: グランドデザイン（PublicLayout, AppShell, Header, SideNav, Footer, HeaderControl, LanguageSwitcher, ThemeToggle）のユニットテストを作成する（Footerは静的表示のみのため個別テストを省略）
- [x] Step 6.4: 表示・フィードバックコンポーネント（Card, AuthCard, PageHeader, DataTable, Pagination, EmptyState, Alert, Badge, Spinner, Modal, ConfirmDialog, FilterBar, Tabs, Toast, CodeBlock, KeyValueList, Dropdown, Tooltip）のユニットテストを作成する（AuthCard/PageHeader/Spinnerは静的表示のみのため個別テストを省略）
- [x] Step 6.5: **検証チェックポイント**: `npm test`（19ファイル・51テスト全て成功）、`npm run build`、`npm run lint`、`npm run format`がすべて通ることを確認した

### 7. Frontend Components Summary

- [x] Step 7.1: 生成した全コンポーネントの一覧をドキュメント化する（`aidlc-docs/construction/unit-01/code/component-inventory.md`）

### 8. Mock Screens Generation（`/mock/*`、devビルド限定）

- [ ] Step 8.1: React Routerを導入し、`/mock/*`をdevビルド限定（`import.meta.env.DEV`等）で`React.lazy` + `Suspense`により遅延読み込みする構成にする
- [ ] Step 8.2: `/mock/login`（ログイン画面モック）を作成する
- [ ] Step 8.3: `/mock/register`（ユーザ登録画面モック、メール送信・PW設定の2ステップ）を作成する
- [ ] Step 8.4: `/mock/dashboard`（管理者ダッシュボードモック）を作成する
- [ ] Step 8.5: `/mock/master-data`（マスタメンテナンス画面モック）を作成する
- [ ] Step 8.6: `/mock/permissions`（権限設定画面モック）を作成する
- [ ] Step 8.7: `/mock/catalog`（コンポーネントカタログ）を作成する。UNIT-01の全共通コンポーネントを一覧表示する

### 9. Mock Screens Unit Testing

- [ ] Step 9.1: 5つの代表画面モック（login/register/dashboard/master-data/permissions）の画面状態（通常/空/エラー）が正しく切り替わることを検証するテストを作成する
- [ ] Step 9.2: `/mock/*`ルートが本番ビルドのバンドルに含まれないこと（コード分割）を検証する
- [ ] Step 9.3: **検証チェックポイント**: `npm test`、`npm run build`が通ることを確認する

### 10. Documentation Generation

- [ ] Step 10.1: `frontend/README.md`を作成する（開発手順、`npm run dev`、テスト実行方法等）
- [ ] Step 10.2: `aidlc-docs/construction/unit-01/code/summary.md`を作成する（生成物一覧、設計判断の要約）

### 11. Deployment Artifacts

- [ ] Step 11.1: N/A — Infrastructure DesignはSKIP。本番デプロイ関連の成果物は生成しない（devenv/docker-compose.ymlはStep 1.10で対応済み、ローカル開発環境用）

### 12. 最終ビルド検証

- [ ] Step 12.1: **検証チェックポイント**: `./gradlew build`（ルートから、backend・frontend両サブプロジェクトの`bootWar`統合を含む全体ビルド）が通ることを確認する
- [ ] Step 12.2: OWASP Dependency-Check（`./gradlew :backend:dependencyCheckAnalyze`）、`npm audit`をそれぞれ実行し、重大な既知脆弱性がないことを確認する

## Story Traceability

- STORY-0.1（共通デザイン基盤の構築）→ Step 2〜3, 6
- STORY-0.2（個別画面デザインの段階的整備）→ Step 4（グランドデザイン確立により後続ユニットが個別画面を実装できる土台を提供）
- STORY-0.3（代表画面モックによる早期デザイン確認）→ Step 8〜9

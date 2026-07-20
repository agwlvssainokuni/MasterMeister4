# UNIT-01（デザインシステム基盤）Code Generation 計画

## ユニットコンテキスト

- **対応ストーリー**: STORY-0.1（共通デザイン基盤の構築）。STORY-0.2（個別画面デザインの段階的整備）はプロセス上の原則であり、UNIT-01固有の成果物は生成しない（以降の各機能ユニットの実装時に順次満たされる）
- **対応コンポーネント**: フロントエンドのみ（バックエンドコンポーネントなし）。Business Logic / API Layer / Repository Layer / Database Migration Scriptsの各ステップは本ユニットでは**N/A**
- **依存ユニット**: なし（最初に着手するユニット）
- **本ユニットが提供するインターフェース**: `frontend/src/design-system/`配下の共通UIコンポーネント一式。以降の全機能ユニット（UNIT-02〜UNIT-09）がこれをimportして利用する
- **入力とする既承認の決定事項**:
  - 技術スタック: React 19 + TypeScript + Vite（`tech-stack-decisions.md`、`nfr-requirements.md` NFR-UNIT01-4）
  - CSS Modules、react-i18next、最小限のアイコン方針（`tech-stack-decisions.md`）
  - 共通ErrorBoundary（トップレベル1つ、`nfr-design-patterns.md` DP-UNIT01-1, DP-UNIT01-2）
  - バレルエクスポート構成（`nfr-design-patterns.md` DP-UNIT01-3）
  - `dangerouslySetInnerHTML`不使用の徹底（`nfr-design-patterns.md` DP-UNIT01-4）
  - `frontend/src/design-system/`への配置、テーマ切替対応トークン構造（`logical-components.md`）
  - アクセシビリティは基本的なキーボード操作・ARIA属性のみ（`nfr-requirements.md` NFR-UNIT01-5）
  - ブレークポイントはデスクトップ・タブレットの2段階（`nfr-requirements.md` NFR-UNIT01-6）
  - Vitest + React Testing Libraryでテスト、Storybook等の専用カタログなし（`nfr-requirements.md` NFR-UNIT01-4）
  - フォントはセルフホストのWebフォントを採用し、外部CDNには依存しない（SECURITY-13のN/A判定と整合）。本文用は「Noto Sans JP」（`@fontsource/noto-sans-jp`）、SQL/コード表示用の等幅フォントは「Noto Sans Mono」（`@fontsource/noto-sans-mono`）とし、Noto Sans Monoでカバーされない日本語文字はNoto Sans JPにフォールバックする（本Code Generation Planning中に追加確認。いずれもOFL-1.1ライセンスであることをnpmインストール後に確認済み）
  - Lint/フォーマット: oxlint（Vite最新テンプレートの既定）+ Prettier（Part 2実装中にユーザ確認の上、当初計画のESLint+Prettierから変更）。Prettierは`semi: false`（セミコロンなし、Code Generation完了後のレビュー指摘で変更）
  - 全ソースファイル（`.ts`/`.tsx`/`.css`）先頭にApache License 2.0のヘッダーコメントを付与（Code Generation完了後のレビュー指摘で追加）
  - コンポーネントカタログ: Storybook等は導入せず、`src/design-system/catalog/CatalogPage.tsx`という軽量なアプリ内一覧ページ（`/catalog`）で対応（NFR-UNIT01-4の「専用カタログ不使用」方針は維持。Code Generation完了後のレビュー指摘で追加、ユーザに方式を確認済み）

## 実行ステップ

### Step 1: プロジェクト構造セットアップ（Greenfield・最初のユニット）
- [x] 1-1. `frontend/`ディレクトリを作成し、Vite + React 19 + TypeScriptプロジェクトを初期化する
- [x] 1-2. Vitest + React Testing Library + jsdom環境をセットアップする（テスト設定ファイル、セットアップファイル）
- [x] 1-3. CSS Modules対応を確認する（Viteの標準機能、追加設定は最小限）
- [x] 1-4. ~~ESLint + Prettier~~ → **oxlint（Vite既定）+ Prettier**の基本設定を行う（計画変更、下記「計画変更ログ」参照）
- [x] 1-5. `frontend/package.json`に`dev`, `build`, `test`, `lint`, `format`, `audit`（`npm audit`）の各スクリプトを定義する
- [x] 1-6. `frontend/.gitignore`を確認する（Vite既定を採用。`node_modules/`, `dist/`等を含む）

**計画変更ログ**: Vite最新版のデフォルトテンプレートがESLintではなくoxlint（Rust製高速Linter）を採用していたため、ユーザに確認の上、方針を「oxlint（Lint）+ Prettier（フォーマット）」に変更した（当初計画のESLint+Prettierから変更）。

**注記**: `backend/`, `devenv/`ディレクトリは本ユニットでは作成しない。バックエンドコンポーネントが必要になるUNIT-02以降で、必要になった時点で作成する（`requirements.md`§4のプロジェクト構成は維持しつつ、段階的に実体化する）

### Step 2: デザイントークン生成
- [x] 2-1. `@fontsource/noto-sans-jp`と`@fontsource/noto-sans-mono`をインストールし、フォントファイルを自己ホストする（外部CDN非依存）
- [x] 2-2. 色・タイポグラフィ（本文font-familyに Noto Sans JP、コード/SQL表示用font-familyに `'Noto Sans Mono', 'Noto Sans JP', monospace`（日本語文字はNoto Sans JPへフォールバック）を設定）・スペーシング・ブレークポイント（デスクトップ/タブレット2段階）のデザイントークンをCSS変数として定義する（`frontend/src/design-system/tokens/`）
- [x] 2-3. テーマ切替可能な構造（`[data-theme="light"]`セレクタ）としつつ、初期値はライトテーマのみ定義する

### Step 3: i18n基盤セットアップ
- [x] 3-1. react-i18nextを初期化する（`frontend/src/design-system/i18n/`）
- [x] 3-2. 日本語（`ja`）・英語（`en`）の初期名前空間ファイルを作成する（共通UIコンポーネントの文言: ボタンラベル、フォームバリデーションメッセージ、ErrorBoundaryのフォールバック文言等）

### Step 4: 共通UIコンポーネント生成
- [x] 4-1. Button（`frontend/src/design-system/components/Button/`）
- [x] 4-2. TextField（テキスト入力、`frontend/src/design-system/components/TextField/`）
- [x] 4-2b. TextArea（複数行入力、`frontend/src/design-system/components/TextArea/`。`monospace`プロップでNoto Sans Mono表示に切替可能。Code Generation完了後のレビュー指摘で追加）
- [x] 4-3. Select（選択、`frontend/src/design-system/components/Select/`。ドロップダウン表示用に最小限のChevronDownアイコンを1つ自作SVGコンポーネントとして追加）
- [x] 4-4. Checkbox（`frontend/src/design-system/components/Checkbox/`）
- [x] 4-5. RadioButton（`frontend/src/design-system/components/RadioButton/`）
- [x] 4-6. FormField（ラベル・ヘルプテキスト・エラーメッセージのラッパー、`frontend/src/design-system/components/FormField/`）
- [x] 4-7. ErrorBoundary（アプリケーション全体のトップレベル用、`frontend/src/design-system/components/ErrorBoundary/`。フォールバック表示は汎用メッセージのみ、内部情報は含めない。捕捉したエラーは`console.error`に出力）
- [x] 4-8. 各コンポーネントに`data-testid`を付与できるよう`testId`プロップを用意する（命名規則: `{component}-{element-role}`、例: `login-form-submit-button`）
- [x] 4-9. バレルファイル（`frontend/src/design-system/index.ts`）で全コンポーネントを名前付きエクスポートする

### Step 4b: 参考実装との突合による追加コンポーネント生成（Code Generation完了後のレビュー指摘で追加）
ユーザ提供の参考デザインシステム（`design-system--`、ワークスペースルート直下、frontend/の対象外）との過不足点検の結果、以下を追加。詳細は`aidlc-docs/construction/unit-01/code/summary.md`「レビュー対応ログ」参照。
- [x] 4b-1. Button拡張（variant: danger/ghost追加、size: sm/md、loading状態+Spinner連携）、IconButton
- [x] 4b-2. Spinner
- [x] 4b-3. PasswordInput、SearchInput
- [x] 4b-4. Switch
- [x] 4b-5. Badge、Alert、Card、EmptyState
- [x] 4b-6. ThemeProvider（light/dark/system、localStorage永続化、OS設定追従）、ThemeToggle
- [x] 4b-7. LanguageSwitcher、i18n語彙拡張（action/state/theme/language/pagination/table/form）、ブラウザ言語自動検出
- [x] 4b-8. デザイントークンをプリミティブ層(`--mm-palette-*`)+セマンティック層(`--mm-color-*`等)に再構成し、ダークモードの実データを追加
- [x] 4b-9. FormFieldのAPIをrender-props方式から`cloneElement`方式に変更（ユーザ承認済み）
- **見送り**: Table, Modal/ConfirmDialog, Toast, Dropdown/Tooltip, Tabs, Pagination, AppShell, CodeBlock, KeyValueList等の機能寄りコンポーネントは、FR-0.3の方針に従い後続ユニット（UNIT-05等）に委ねる

### Step 5: 共通UIコンポーネントのユニットテスト
- [x] 5-1. 全コンポーネントについて、レンダリング・基本的なユーザ操作・キーボード操作・ARIA属性をVitest + React Testing Libraryで検証するテストを作成する（20ファイル・56テスト、全てパス）

### Step 6: ドキュメント生成
- [x] 6-1. `frontend/README.md`を作成する（セットアップ手順、開発サーバ起動、テスト実行、ビルド、`npm audit`実行手順）
- [x] 6-2. `aidlc-docs/construction/unit-01/code/summary.md`を作成する（生成した構造・コンポーネント一覧のサマリ、Markdownのみ）
- [x] 6-3. `frontend/README.md`にNoto Sans JP・Noto Sans Monoのライセンス表記（いずれもSIL Open Font License 1.1）を記載する

### Step 7: デプロイ成果物生成
- **N/A**: 本ユニットは共通UIコンポーネント一式の提供のみであり、単独でデプロイ可能な成果物を持たない。デプロイ関連の成果物は、実際にエンドツーエンドで動作する画面が揃う後続ユニット以降で検討する

---

## Story Traceability

| ストーリー | 対応ステップ |
|---|---|
| STORY-0.1 共通デザイン基盤の構築 | Step 1〜6 |
| STORY-0.2 個別画面デザインの段階的整備 | 本ユニットでは対象外（以降の各機能ユニットで順次満たす） |

---

このプランは、UNIT-01のCode Generation実行における唯一の正とする。承認後、Step 1から順に実行し、各ステップ完了時にチェックボックスを更新する。

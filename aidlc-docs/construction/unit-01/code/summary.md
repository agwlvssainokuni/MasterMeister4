# UNIT-01（デザインシステム基盤）Code Generation サマリ

`unit-01-code-generation-plan.md`の全ステップおよびレビュー対応（Request Changes、参考実装との突合）を実行した結果のサマリ。実装本体は`frontend/`（ワークスペースルート）にある。本ファイルはMarkdownドキュメントのみで、コードは含まない。

## 作成した構造

```
frontend/
├── src/
│   ├── design-system/
│   │   ├── index.ts                 # バレルエクスポート（tokens/fonts/i18nの読み込みも兼ねる）
│   │   ├── fonts.ts                 # Noto Sans JP / Noto Sans Monoの自己ホスト読み込み
│   │   ├── tokens/
│   │   │   ├── colors.css           # プリミティブ層(--mm-palette-*) + セマンティック層(--mm-color-*)、ライト/ダーク
│   │   │   ├── typography.css       # --mm-font-*
│   │   │   ├── spacing.css          # --mm-space-*、shadow、z-index、duration、コンポーネント寸法
│   │   │   ├── breakpoints.ts       # デスクトップ/タブレットのブレークポイント定数
│   │   │   └── index.css            # 上記CSSトークンの集約 + bodyの基本スタイル
│   │   ├── theme/
│   │   │   └── ThemeProvider.tsx    # light/dark/systemのテーマ管理（localStorage永続化、OS設定追従）
│   │   ├── i18n/
│   │   │   ├── index.ts             # react-i18next初期化、ブラウザ言語検出、切替、<html lang>同期
│   │   │   └── locales/{ja,en}/common.json
│   │   ├── components/
│   │   │   ├── Button/              # variant: primary/secondary/danger/ghost, size: sm/md, loading
│   │   │   ├── TextField/
│   │   │   ├── TextArea/
│   │   │   ├── PasswordInput/       # 表示/非表示切替
│   │   │   ├── SearchInput/
│   │   │   ├── Select/
│   │   │   ├── Checkbox/
│   │   │   ├── RadioButton/
│   │   │   ├── Switch/
│   │   │   ├── FormField/           # cloneElementでid/aria-*を子要素へ注入
│   │   │   ├── ErrorBoundary/
│   │   │   ├── Spinner/
│   │   │   ├── Badge/
│   │   │   ├── Alert/
│   │   │   ├── Card/
│   │   │   ├── EmptyState/
│   │   │   ├── ThemeToggle/
│   │   │   ├── LanguageSwitcher/
│   │   │   └── icons/ChevronDownIcon.tsx
│   │   └── catalog/
│   │       └── CatalogPage.tsx      # 軽量なコンポーネント一覧ページ（/catalog）
│   ├── App.tsx                      # UNIT-01時点のプレースホルダー画面（デザインシステムの動作確認用）
│   ├── App.module.css
│   ├── main.tsx                     # アプリのルート。ThemeProvider→ErrorBoundary（トップレベル1つ）。パスが/catalogならCatalogPage
│   └── test/setup.ts                # Vitestセットアップ（jest-dom、i18nをjaに強制、matchMediaのポリフィル）
├── package.json
├── vite.config.ts                   # Vitest設定を含む
├── tsconfig*.json
├── .prettierrc.json / .prettierignore
└── README.md
```

`backend/`, `devenv/`は本ユニットでは作成していない（計画のとおり、必要になるUNIT-02以降で作成する）。

## 生成した共通UIコンポーネント

| コンポーネント | 概要 |
|---|---|
| Button, IconButton | variant: primary/secondary/danger/ghost、size: sm/md、`loading`状態（Spinner連携） |
| TextField | テキスト入力 |
| TextArea | 複数行入力。`monospace`プロップでNoto Sans Mono表示に切替可能（SQL入力を想定、STORY-7.1） |
| PasswordInput | 表示/非表示切替付きのパスワード入力 |
| SearchInput | `type="search"`のテキスト入力 |
| Select | ネイティブ`<select>`をスタイリング。ChevronDownアイコン付き |
| Checkbox, RadioButton | ラベルクリック・キーボード操作に対応 |
| Switch | トグルスイッチ（`role="switch"`） |
| FormField | ラベル・ヘルプテキスト・エラーメッセージのラッパー。単一の子要素に`cloneElement`でid/aria-invalid/aria-describedby/aria-requiredを注入する方式 |
| ErrorBoundary | アプリケーション全体のトップレベルに1つ配置（`main.tsx`）。捕捉したエラーは`console.error`のみに出力し、ユーザには汎用フォールバック文言（i18n対応）のみ表示。内部情報は非表示（SECURITY-15整合） |
| Spinner | ローディング表示（sm/md/lg） |
| Badge, Alert, Card, EmptyState | 汎用的な表示系コンポーネント |
| ThemeToggle, LanguageSwitcher | ヘッダー等に置くテーマ・言語切替UI |

各コンポーネントは`testId`プロップで`data-testid`を指定可能（命名規則: `{component}-{element-role}`）。

## デザイントークン

- プリミティブ層（`--mm-palette-*`）とセマンティック層（`--mm-color-*`等）を分離。コンポーネント・画面はセマンティック層のみ参照する
- タイポグラフィ: 本文フォント（Noto Sans JP）、コード/SQL表示用フォント（Noto Sans Mono、Noto Sans JPへフォールバック）
- スペーシング（4pxグリッド）・角丸・shadow（エレベーション）・z-index・トランジション時間・コンポーネント寸法（control-height等）
- ブレークポイント: デスクトップ・タブレットの2段階（`tokens/breakpoints.ts`に定数として定義。CSSのメディアクエリでは同じ値をリテラルで指定する必要がある点に注意）
- **ダークモードを実データで実装**（`:root[data-theme="dark"]`に上書き値を定義。`ThemeProvider`が`<html data-theme>`を制御し、`localStorage`に永続化、`system`選択時はOS設定に追従）

## i18n

react-i18nextを`common`名前空間で初期化。`action`/`state`/`theme`/`language`/`pagination`/`table`/`form`等の語彙を持つ（一部は後続ユニットで実装するコンポーネント向けに先行して定義）。**ブラウザ言語を自動検出**し、`localStorage`に永続化。`LanguageSwitcher`で切替可能。言語変更時に`<html lang>`属性を同期する。

## フォント

Noto Sans JP・Noto Sans Mono（いずれも`@fontsource`経由で自己ホスト、SIL Open Font License 1.1）をインストール。外部CDNには依存しない。ライセンス表記は`frontend/README.md`に記載。

## テスト

Vitest + React Testing Libraryで全コンポーネントの基本動作・キーボード操作・ARIA属性を検証。**56件のテストが全てパス**（20ファイル）。

## レビュー対応ログ

### Request Changes（1回目）
1. **ライセンス表記コメント**: `frontend/src/`配下の全`.ts`/`.tsx`/`.css`ファイルの先頭にApache License 2.0のヘッダーコメントを追加（リポジトリルートの`LICENSE`に準拠）
2. **セミコロンなしスタイル**: `.prettierrc.json`の`semi`を`false`に変更し、Prettierで全ファイルを再フォーマット
3. **コンポーネントカタログページ**: NFR-UNIT01-4（Storybook等の専用カタログは導入しない）との整合を確認した上で、Storybook等の新規ツールは導入せず、`src/design-system/catalog/CatalogPage.tsx`という軽量なアプリ内ページ（`/catalog`）を追加
4. **TextAreaコンポーネントの追加**: 複数行入力用の`TextArea`を追加

### 参考実装との突合（`design-system--`、ユーザ提供）
ユーザが参考にしたいデザインシステム一式（約2,250行）を提供し、過不足点検を実施。3カテゴリに分類して対応方針を確認：
- **カテゴリA（基盤強化、全て取り込み）**: トークンのプリミティブ/セマンティック層分離、ダークモード実装（`ThemeProvider`）、i18n語彙拡張・ブラウザ言語検出、Button拡張（variant/size/loading）、IconButton、Spinner、Badge/Alert/Card/EmptyState、Switch、PasswordInput/SearchInput
- **カテゴリB（Table/Modal/Toast/Dropdown/Tooltip/Tabs/Pagination/AppShell等の機能寄りコンポーネント）**: 後続ユニットに委ねる（FR-0.3「個別画面は各機能実装時に順次整備」の方針に従う）。本ユニットでは実装しない
- **FormField API**: render-props方式から、参考実装と同じ`cloneElement`方式に変更

**意図的に踏襲しなかった点**:
- `data-testid`（`testId`プロップ）: 参考実装にはないが、CLAUDE.mdのAutomation Friendly Code Rulesで必須のため維持
- セミコロンあり・ダブルクォート: 参考実装のスタイルだが、直前のRequest Changesでの指示（セミコロンなし）を優先

**作業中に発見・対処した環境課題**:
- Node.js 26の実験的Web Storage機能（`--experimental-webstorage`）がVitest+jsdomの`localStorage`実装と衝突する問題を発見。`NODE_OPTIONS=--no-experimental-webstorage`を`test`/`test:watch`スクリプトに追加して解決
- jsdomが`matchMedia`を実装していないため、`ThemeProvider`の`system`テーマ判定用にテストセットアップでポリフィルを追加
- i18nをブラウザ言語自動検出に変更したことでテスト環境（jsdom既定でnavigator.language=en-US）でのテスト結果が英語表示になっていた問題を発見。テストセットアップで`ja`に強制することで解決
- 参考実装ディレクトリ（`design-system--`）が一時的に`frontend/src/`配下にあった際、Prettierのglobパターンが誤ってマッチし、参考実装ファイルの書式（セミコロン・ダブルクォート）を書き換えてしまった。ユーザがディレクトリをワークスペースルート直下へ移動し、`frontend/`のツールから完全に切り離すことで解決（内容・ロジックへの影響はないが、書式は変更されたままである点に留意）

## 検証結果（最終）

| コマンド | 結果 |
|---|---|
| `npx tsc --noEmit` | エラーなし |
| `npm test`（Vitest） | 20ファイル・56テスト全てパス |
| `npm run lint`（oxlint） | エラーなし |
| `npm run format:check`（Prettier） | 全ファイル整形済み |
| `npm run build` | 成功 |
| `npm run audit`（`npm audit`） | 0件の脆弱性 |

## Story Traceability

| ストーリー | 状態 |
|---|---|
| STORY-0.1 共通デザイン基盤の構築 | 完了 |
| STORY-0.2 個別画面デザインの段階的整備 | 本ユニットでは対象外（以降の各機能ユニットで順次満たす） |

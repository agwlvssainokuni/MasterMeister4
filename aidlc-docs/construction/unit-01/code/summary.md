# UNIT-01（デザインシステム基盤）Code Generation サマリ

`unit-01-code-generation-plan.md`の全ステップを実行した結果のサマリ。実装本体は`frontend/`（ワークスペースルート）にある。本ファイルはMarkdownドキュメントのみで、コードは含まない。

## 作成した構造

```
frontend/
├── src/
│   ├── design-system/
│   │   ├── index.ts                 # バレルエクスポート（tokens/fonts/i18nの読み込みも兼ねる）
│   │   ├── fonts.ts                 # Noto Sans JP / Noto Sans Monoの自己ホスト読み込み
│   │   ├── tokens/
│   │   │   ├── colors.css
│   │   │   ├── typography.css
│   │   │   ├── spacing.css
│   │   │   ├── breakpoints.ts       # デスクトップ/タブレットのブレークポイント定数
│   │   │   └── index.css            # 上記CSSトークンの集約 + bodyの基本スタイル
│   │   ├── i18n/
│   │   │   ├── index.ts             # react-i18next初期化
│   │   │   └── locales/{ja,en}/common.json
│   │   └── components/
│   │       ├── Button/
│   │       ├── TextField/
│   │       ├── Select/
│   │       ├── Checkbox/
│   │       ├── RadioButton/
│   │       ├── FormField/
│   │       ├── ErrorBoundary/
│   │       └── icons/ChevronDownIcon.tsx
│   ├── App.tsx                      # UNIT-01時点のプレースホルダー画面（デザインシステムの動作確認用）
│   ├── App.module.css
│   ├── main.tsx                     # アプリのルート。ErrorBoundaryをトップレベルに1つ配置
│   └── test/setup.ts                # Vitestセットアップ（jest-dom、i18n初期化）
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
| Button | primary/secondary バリアント、`type`既定値`button`、`testId`プロップ対応 |
| TextField | テキスト入力。`FormField`と組み合わせて使用 |
| Select | ネイティブ`<select>`をスタイリング。ドロップダウン表示用にChevronDownアイコンを1つ追加（最小限のアイコン方針） |
| Checkbox | ラベルクリック・キーボード操作に対応 |
| RadioButton | `name`/`value`でグループ化して使用 |
| FormField | ラベル・ヘルプテキスト・エラーメッセージのラッパー。render-props（`children(fieldProps)`）でid/aria-describedby/aria-invalidを入力コンポーネントに伝搬する |
| ErrorBoundary | アプリケーション全体のトップレベルに1つ配置（`main.tsx`）。捕捉したエラーは`console.error`のみに出力し、ユーザには汎用フォールバック文言（i18n対応）のみ表示。内部情報は非表示（SECURITY-15整合） |

各コンポーネントは`testId`プロップで`data-testid`を指定可能（命名規則: `{component}-{element-role}`）。

## デザイントークン

- 色: ニュートラル・プライマリ・セマンティック（success/warning/error/info）のスケールと、それを組み合わせた役割別トークン（背景・文字色・ボーダー）
- タイポグラフィ: 本文フォント（Noto Sans JP）、コード/SQL表示用フォント（Noto Sans Mono、Noto Sans JPへフォールバック）
- スペーシング・角丸・フォーカスリング幅
- ブレークポイント: デスクトップ・タブレットの2段階（`tokens/breakpoints.ts`に定数として定義。CSSのメディアクエリでは同じ値をリテラルで指定する必要がある点に注意）
- テーマ構造: `[data-theme="light"]`セレクタで将来のテーマ切替に対応できる構造としつつ、現時点ではライトテーマの値のみ定義

## i18n

react-i18nextを`common`名前空間で初期化。既定言語は日本語（`ja`）、フォールバックは英語（`en`）。言語切替UIは本ユニットのスコープ外（将来追加時も同じi18nextインスタンスを拡張する形で対応可能）。

## フォント

Noto Sans JP・Noto Sans Mono（いずれも`@fontsource`経由で自己ホスト、SIL Open Font License 1.1）をインストール。外部CDNには依存しない。ライセンス表記は`frontend/README.md`に記載。

## テスト

Vitest + React Testing Libraryで全コンポーネントの基本動作・キーボード操作・ARIA属性を検証。**25件のテストが全てパス**。

## 検証結果

| コマンド | 結果 |
|---|---|
| `npx tsc --noEmit` | エラーなし |
| `npm test`（Vitest） | 7ファイル・25テスト全てパス |
| `npm run lint`（oxlint） | エラーなし |
| `npm run build` | 成功 |
| `npm run audit`（`npm audit`） | 0件の脆弱性 |

## Story Traceability

| ストーリー | 状態 |
|---|---|
| STORY-0.1 共通デザイン基盤の構築 | 完了 |
| STORY-0.2 個別画面デザインの段階的整備 | 本ユニットでは対象外（以降の各機能ユニットで順次満たす） |

# MasterMeister frontend

React 19 + TypeScript + Vite。UIコンポーネントはサードパーティのUIライブラリを使わず、`src/design-system/`配下に自前で構築する（NFR-6.1）。

## セットアップ

```sh
npm install
```

## 開発サーバ起動

```sh
npm run dev
```

## テスト実行

```sh
npm test          # 1回実行
npm run test:watch  # watchモード
```

Vitest + React Testing Libraryを使用（NFR-9.1）。

## Lint / フォーマット

```sh
npm run lint         # oxlint
npm run format       # Prettierで整形
npm run format:check # 整形が必要な箇所がないか確認のみ
```

## ビルド

```sh
npm run build
```

## 依存パッケージの脆弱性スキャン（SECURITY-10）

```sh
npm run audit
```

## デザインシステム

`src/design-system/`に共通UIコンポーネント（Button, TextField, Select, Checkbox, RadioButton, FormField, ErrorBoundary）、デザイントークン、i18n基盤を配置する。以降の全機能ユニットはこのディレクトリのコンポーネントをimportして画面を構築する。

専用のコンポーネントカタログ（Storybook等）は導入していない。各コンポーネントの仕様はTypeScriptの型定義とテストコード（`*.test.tsx`）を参照すること。

## フォントライセンス

自己ホストしている以下のフォントは、いずれも [SIL Open Font License 1.1](https://openfontlicense.org/) の下で提供されている。

- [Noto Sans JP](https://fonts.google.com/noto/specimen/Noto+Sans+JP)（`@fontsource/noto-sans-jp`） — 本文用
- [Noto Sans Mono](https://fonts.google.com/noto/specimen/Noto+Sans+Mono)（`@fontsource/noto-sans-mono`） — SQL/コード表示用（カバーされない日本語文字はNoto Sans JPにフォールバック）

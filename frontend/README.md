# MasterMeister frontend

React 19 + TypeScript + Vite製のフロントエンド。UNIT-01（デザインシステム基盤）で構築した共通コンポーネント・グランドデザインは`src/design-system/`配下にある。

## 開発

```bash
npm install
npm run dev
```

`http://localhost:5173/`でアプリが起動する。devビルド時のみ`/mock/*`配下でデザインシステムのコンポーネントカタログ・代表画面モックを確認できる（`/mock/catalog`が入口）。本番ビルドにはこれらのコードは含まれない。

## ビルド

```bash
npm run build
```

`dist/`に成果物を生成する。バックエンドとの統合（単一WAR生成）は`../backend`のGradleタスク（`./gradlew :backend:bootWar`）から行う。`frontend`単体のビルドはこのコマンドで完結し、バックエンドを必要としない。

## テスト

```bash
npm test        # 1回実行
npm run test:watch  # watchモード
```

Vitest + React Testing Libraryを使用。

## Lint / フォーマット

```bash
npm run lint          # oxlint
npm run format        # prettier --write
npm run format:check  # prettier --check
```

コードスタイルはセミコロンなし・シングルクォート（`.prettierrc.json`参照）。

## ディレクトリ構成

```
src/
├── design-system/     # UNIT-01で構築した共通デザインシステム
│   ├── tokens/         # デザイントークン、セルフホストフォント
│   ├── theme/           # ダークモード（ThemeProvider）
│   ├── i18n/             # 多言語対応（react-i18next、common/design-system名前空間）
│   └── components/    # 共通UIコンポーネント一式
├── mocks/              # devビルド限定のコンポーネントカタログ・代表画面モック
└── test/                 # テスト共通セットアップ・ヘルパー
```

詳細は`aidlc-docs/construction/unit-01/code/component-inventory.md`を参照。

# MasterMeister frontend

React 19 + TypeScript + Vite製のフロントエンド。UNIT-01（デザインシステム基盤）で構築した共通コンポーネント・グランドデザインは`src/design-system/`配下、UNIT-02（ユーザ登録・認証）で構築したログイン・ユーザ登録・ユーザ管理・トップ画面は`src/pages/`配下にある。

## 開発

バックエンド（`../backend`）を別プロセスで起動しておく必要がある（デフォルトport 8080。起動方法は`../backend/README.md`参照）。

```bash
npm install
npm run dev
```

`http://localhost:5173/`でアプリが起動する。devサーバは`/api/**`へのリクエストを`http://localhost:8080`へプロキシする（`vite.config.ts`の`server.proxy`）。本番ビルド（単一WAR）ではフロントエンドとバックエンドが同一オリジンから配信されるためプロキシは不要になる。

devビルド時のみ`/mock/*`配下でデザインシステムのコンポーネントカタログ・代表画面モックを確認できる（`/mock/catalog`が入口）。本番ビルドにはこれらのコードは含まれない。

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
│   └── components/    # 共通UIコンポーネント一式
├── i18n/                 # 多言語対応（react-i18next、common/design-system名前空間。アプリ全体で使う横断的インフラのためdesign-system/の外に配置）
├── auth/                # 認証状態管理（AuthContext）、トークン保管（sessionStorage）、JWTデコード
├── api/                  # バックエンドAPIクライアント（apiFetch、リフレッシュ自動再試行）
├── pages/               # UNIT-02で構築した画面（ログイン、ユーザ登録、ユーザ管理、トップ）
├── mocks/              # devビルド限定のコンポーネントカタログ・代表画面モック
└── test/                 # テスト共通セットアップ・ヘルパー（renderMock、renderPage）
```

詳細は`aidlc-docs/construction/unit-01/code/component-inventory.md`（デザインシステム）・`aidlc-docs/construction/unit-02/code/frontend-summary.md`（認証基盤・画面）を参照。

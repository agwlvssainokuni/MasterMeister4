# UNIT-01 デザインシステム基盤 - Logical Components

`unit-01-nfr-design-plan.md`の回答（Q1〜Q7すべてA）に基づく。

## 1. デザイントークン（2層アーキテクチャ、Q1=A）

- **プリミティブトークン**（`--mm-palette-*`）: 生の色相・グレースケール等の値。ライト/ダーク共通で固定値として定義する
- **セマンティックトークン**（`--mm-color-*`, `--mm-font-*`, `--mm-space-*`, `--mm-radius-*`等）: プリミティブトークンを参照し、用途（背景色、テキスト色、境界線色等）に応じて意味付けした変数。コンポーネントは常にセマンティックトークンのみを参照する
- テーマ（ライト/ダーク）ごとに、セマンティックトークンの値の対応関係のみを再定義する（プリミティブトークン自体は変わらない）

## 2. ThemeProvider（ダークモード、Q2=A）

- React Contextでテーマ状態（`light` / `dark` / `system`）を管理する
- `<html data-theme="light|dark">`属性を切り替えることで、CSS変数（セマンティックトークン）の値をテーマごとに再定義する方式とする
- `system`選択時は`matchMedia('(prefers-color-scheme: dark)')`を用いてOS設定に追従する
- 選択状態は`localStorage`に保存し、次回訪問時も復元する

## 3. I18nProvider（多言語対応、Q3=A）

- react-i18nextを初期化し、デフォルト言語は日本語、対応言語は日本語・英語とする
- 翻訳リソースは名前空間ごとにファイルを分割する。UNIT-01時点で用意する名前空間:
  - `common`: ボタン・エラーメッセージ等、全画面共通のUI文言
  - `design-system`: グランドデザイン（ナビゲーション項目名等）・代表画面モックの文言
- 言語検出は`navigator.language`を初期値とし、`localStorage`に選択言語を保存する

## 4. ErrorBoundary（Q5=A）

- アプリのルート付近（`AppShell`/`PublicLayout`より外側）に1つ設置する
- 捕捉した描画エラーは`console.error`に出力する。フォールバックUIとして汎用エラーメッセージ（i18n対応）を表示する
- エラーレポート送信APIとの連携は本ユニットでは行わない（将来のユニットで検討）

## 5. コンポーネントカタログ（NFR-01-04、devビルド限定）

- devビルド限定ルート（例: `/__mocks__/catalog`）で、UNIT-01が新設する共通コンポーネント一式・代表5画面モックを一覧表示する
- `React.lazy` + `Suspense`で遅延読み込みし、本番ビルドのバンドルに含めない（Q4=A）

## 6. Gradle Node Plugin統合（単一WAR生成、Q7=A）

- `frontend`サブプロジェクトに`com.github.node-gradle.node`プラグインを適用する
- `npmInstall` / `npmBuild`（`vite build`相当）タスクを定義する
- `backend`の`bootWar`タスクが、`frontend`の`npmBuild`タスクの成果物（`frontend/dist`）を`backend`の静的リソースディレクトリへコピーする処理に依存する構成とする
- `backend:build` / `backend:test`単体の実行では`frontend`側のタスクは実行されない（バックエンド開発サイクルを損なわない）

## 7. アイコン（自作SVG、Functional Designより）

- サードパーティのアイコンライブラリは使用せず、SVGを自作する`Icon`コンポーネントとして提供する
- アイコンはセマンティックトークン（`--mm-color-*`）を用いて色を制御し、ダークモードにも自動追従させる

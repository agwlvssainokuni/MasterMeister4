# UNIT-01 デザインシステム基盤 - Tech Stack Decisions

`unit-01-nfr-requirements-plan.md`の回答に基づく技術選定。

## フォント（Q1=B, Q2=B）

- **本文用**: セルフホストのWebフォント（`@fontsource`等のnpmパッケージ経由でバンドル。候補: Noto Sans JP。OFLライセンス）。外部CDN（Google Fonts等）は使用しない
- **SQL/コード表示用（等幅）**: セルフホストの等幅Webフォント（候補: Noto Sans Mono）。日本語グリフを含まない等幅フォント単体では和文コメント等が表示できないため、本文用フォント（Noto Sans JP）にフォールバックするフォントスタックを組む
- 具体的なパッケージ名・バージョンはCode Generation段階で確定する

## ダークモード（Q3=A）

- ライト／ダーク／システム追従の3モード
- 実装方針: テーマコンテキスト（React Context）で管理し、選択状態を`localStorage`に保存。システム追従時は`matchMedia('(prefers-color-scheme: dark)')`を使用
- CSS実装: `<html data-theme="light|dark">`をトリガーに、CSS変数（デザイントークン）を切り替える方式とする（詳細トークン設計はNFR Designで行う）

## 多言語対応・i18n（Q4=B）

- ライブラリ: react-i18next
- デフォルト言語: 日本語。ブラウザの`navigator.language`を初期検出に用い、`localStorage`に選択言語を保存する
- 対応言語: 日本語・英語（英語の翻訳リソースは本ユニットで骨組みを用意し、文言の充実は各ユニット実装時に追加していく）

## アクセシビリティ（Q5=A）

- 目標水準: WCAG 2.1 Level AA相当
- 共通コンポーネント（Button, TextField, DataTable等）はセマンティックHTML・適切なARIA属性・キーボード操作対応を前提に実装する

## ブラウザサポート（Q6=A）

- モダンブラウザの最新2バージョン（Chrome, Edge, Firefox, Safari）
- Viteの`browserslist`設定等でビルドターゲットを明示する（詳細はCode Generation段階）

## パフォーマンス（Q7=A）

- 明確な数値目標は設けず、Viteの標準的な最適化（コード分割・Tree Shaking）に委ねる
- フォントは`font-display: swap`を用いてレンダリングブロックを回避する

## エラーハンドリング（Q8=A）

- Reactの`ErrorBoundary`をアプリのルート付近に1箇所設置し、フォールバックUI（汎用エラーメッセージ）を表示する

## コンポーネントカタログ（Q9=A）

- Storybookは導入しない
- アプリ内の軽量な一覧ページ（devビルド限定ルート、例: `/__mocks__/catalog`）で共通コンポーネント一式を確認できるようにする

## 依存関係の脆弱性スキャン（Q10=A）

- `npm audit`をローカル/CI実行のベースラインとする
- Dependabot等の自動化ツールの導入はUNIT-10（CI/CD）で検討する

# UNIT-01 デザインシステム基盤 - NFR Design Patterns

`unit-01-nfr-design-plan.md`の回答（Q1〜Q7すべてA、AI推奨どおり）に基づく。

## Scalability Patterns
N/A — バックエンド・データストアを持たない静的フロントエンドのため該当しない。

## Resilience Patterns

- **描画エラーのフェイルセーフ**: アプリのルート付近に1つの React `ErrorBoundary`を設置する。捕捉した描画エラーはブラウザコンソールへ出力するのみとし（Q5=A）、バックエンドへのエラーレポート送信APIは本ユニットでは実装しない。エラーレポート送信の仕組みは、バックエンドAPIが実装される後続ユニットで別途検討する

## Performance Patterns

- **コード分割**: devビルド限定のモック/コンポーネントカタログルートは`React.lazy` + `Suspense`で遅延読み込みし、本番ビルドのバンドルに含めない（Q4=A、SECURITY-09と対応）
- **i18nリソースの分割ロード**: 翻訳リソースは機能（ユニット）単位でファイルを分割し、react-i18nextの名前空間機能で必要な範囲のみ読み込む（Q3=A）。UNIT-01時点では`common`（共通UI文言）・`design-system`（グランドデザイン・モック文言）の2名前空間を用意し、以降のユニットで機能ごとの名前空間を追加していく
- **フォント読み込み**: `@fontsource`パッケージ経由でCSSに`@font-face`を静的にバンドルし、`font-display: swap`を指定してレンダリングブロックを回避する（Q6=A）

## Security Patterns

- **フォントのセルフホスト継続**: 外部CDN（Google Fonts等）を使わず、npm経由でセルフホストする方針を維持する（SECURITY-13、NFR Requirementsでの決定を継続）
- **devビルド限定ルートの分離**: モック/カタログはコード分割（本セクション「Performance Patterns」参照）により本番バンドルから物理的に除外し、SECURITY-09（サンプル/デモページを本番に含めない）を実装レベルで担保する

## Logical Components

以下のコンポーネント/仕組みをUNIT-01のCode Generationで実装する。詳細は`logical-components.md`を参照。

- デザイントークン（2層アーキテクチャ）
- `ThemeProvider`（ダークモード）
- `I18nProvider`（react-i18next初期化）
- `ErrorBoundary`
- Gradle Node Plugin統合（`frontend`ビルドを`backend`の`bootWar`に接続）

# UNIT-01（デザインシステム基盤）NFR Design Patterns

`unit-01-nfr-design-plan.md`の回答（Q1〜Q6）に基づく、UNIT-01のNFRデザインパターン。

## Resilience Patterns
- **DP-UNIT01-1（ErrorBoundary配置）**: アプリケーション全体で1つのトップレベルErrorBoundaryのみを配置する（Q1=A）。画面単位の個別配置は行わない（シンプルさを優先。単独開発・現段階のスコープでは過剰な粒度と判断）
- **DP-UNIT01-2（エラー記録）**: ErrorBoundaryが捕捉した実行時エラーは、このユニットの時点ではconsole.error等の開発者向け出力のみ行う（Q2=A）。監査ログ記録基盤はUNIT-02で構築されるため、フロントエンドからのログ送信連携は後続ユニットで統合する（本ユニット時点ではプレースホルダーの接続点も用意しない）

## Scalability Patterns
**N/A** — NFR Requirementsの時点でScalability自体がN/A（静的コンポーネント群のため実行時にスケールする対象がない）。

## Performance Patterns
- **DP-UNIT01-3（エクスポート方式）**: 各コンポーネントを名前付きエクスポートし、バレルファイル（`index.ts`）で再エクスポートする構成とする（Q3=A）。バンドラー（Vite/Rollup）のTree-shakingにより、未使用コンポーネントはビルド成果物から除外される

## Security Patterns
- **DP-UNIT01-4（XSS対策）**: 全コンポーネントで`dangerouslySetInnerHTML`を使用しない方針を徹底し、Reactの標準エスケープに委ねる（Q6=A）。将来リッチテキスト表示等が必要になった場合は、その時点で個別にサニタイズライブラリ（例: DOMPurify）の導入を検討する

## Logical Components
詳細は`logical-components.md`参照。

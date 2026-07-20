# UNIT-01 デザインシステム基盤 - NFR Requirements

`unit-01-nfr-requirements-plan.md`の回答（Q1=B, Q2=B, Q3=A, Q4=B, Q5=A, Q6=A, Q7=A, Q8=A, Q9=A, Q10=A、すべてAI推奨どおり）に基づく。

## Scalability Requirements
N/A — バックエンド・データストアを持たない静的フロントエンドのため該当しない。

## Availability Requirements
N/A — 本ユニットは可用性SLAを持つ稼働システムではない。実際のデプロイ先の可用性は将来のInfrastructure Design／Operationsフェーズで扱う。

## Performance Requirements
- **NFR-01-01**: 初期表示バンドルサイズの明確な数値目標は設けず、Viteの標準的な最適化（コード分割・Tree Shaking）に委ねる（Q7=A）
- **NFR-01-02**: フォントは`font-display: swap`等でレンダリングブロックを避ける（セルフホストフォント導入に伴う配慮。Q1/Q2）

## Security Requirements（Security Baseline拡張、該当ルールのみ）
- **SECURITY-09**（ハードニング）: 代表画面モックはdevビルド限定ルートとし、本番ビルドに含めない（Functional Designで決定済み。継続適用）
- **SECURITY-10**（サプライチェーン）: 依存関係は`package-lock.json`で固定し、`npm audit`をローカル/CI実行の基本とする。詳細な自動化（Dependabot等）はUNIT-10（CI/CD）で構築する（Q10=A）
- **SECURITY-13**（整合性検証）: フォントは外部CDN（Google Fonts等）を使わず、npm経由でセルフホストし、パッケージの整合性はロックファイルのハッシュで担保する（Q1=B, Q2=B）
- **SECURITY-15**（例外処理・フェイルセーフ）: アプリ全体を覆うReact Error Boundaryを1つ設置し、描画エラー時は汎用フォールバックUIを表示する（Q8=A）
- 上記以外のSECURITYルール（01〜08, 11, 12, 14）: N/A — データストア・API・認証・ネットワークインフラを持たないため該当しない

## Reliability Requirements
- **NFR-01-03**: アプリ全体を覆うReact Error Boundaryを設置し、予期しない描画エラー時にアプリ全体がクラッシュしないようにする（Q8=A、SECURITY-15と対応）

## Maintainability Requirements
- **NFR-01-04**: Storybookは導入せず、アプリ内の軽量なコンポーネント一覧ページ（devビルド限定ルート）でコンポーネントを確認できるようにする（Q9=A）
- **NFR-01-05**: 依存関係の脆弱性スキャンは`npm audit`をベースラインとする（Q10=A、SECURITY-10と対応）

## Usability Requirements
- **NFR-01-06**: アクセシビリティはWCAG 2.1 Level AA相当を目標とする（キーボード操作、スクリーンリーダー対応、コントラスト比等）（Q5=A）
- **NFR-01-07**: ブラウザサポートはモダンブラウザの最新2バージョン（Chrome/Edge/Firefox/Safari）とし、レガシーブラウザは対象外とする（Q6=A）
- **NFR-01-08**: ダークモード（ライト／ダーク／システム追従の3モード）に対応する。選択状態はlocalStorageに保存する（Q3=A）
- **NFR-01-09**: フロントエンドの多言語対応（日本語デフォルト、英語対応、react-i18next）とする（Q4=B、requirements.md §7.8 NFR-7.1/7.2に対応）
- **NFR-01-10**: NFR-7.3（「バックエンド・フロントエンドともにi18n基盤は最初の実装ユニットから導入する」）に基づき、UNIT-01で構築するbackendの最小起動スケルトンにも、Spring側のi18n基盤（`MessageSource`設定、`messages_ja.properties`/`messages_en.properties`の空の雛形）を合わせて用意する。実際のメッセージ内容は各ユニットの実装時に追加する（INCEPTION継続審議で決定）

## PBT (Property-Based Testing) 拡張の適用性
N/A（フル適用）— UNIT-01は静的UIのみでアルゴリズム的処理を持たないため、PBT-01〜09は対象外。functional-design/frontend-components.mdの「Testable Properties」セクションに記録済み。

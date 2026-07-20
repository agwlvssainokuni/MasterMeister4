# UNIT-01（デザインシステム基盤）NFR Requirements

`unit-01-nfr-requirements-plan.md`の回答（Q1〜Q9）に基づく、UNIT-01固有の非機能要件。

## Scalability Requirements
**N/A** — 静的な共通UIコンポーネント群であり、実行時にスケールするサービスではない。

## Availability Requirements
**N/A** — 常駐サービスではなく、ビルド成果物として各画面に組み込まれるコンポーネント。

## Performance Requirements
- **NFR-UNIT01-1**: 明確な数値目標（バンドルサイズ上限、初期表示速度等）は設定しない。コード分割・不要な再レンダリング回避といった標準的なReactのベストプラクティスを踏襲する（Q7=A）

## Security Requirements
- **NFR-UNIT01-2**: 依存パッケージ（npm）の脆弱性スキャンは、ローカルでの`npm audit`実行手順の整備（Build and Testステージ）と、UNIT-10（CI/CD）でのGitHub Dependabot設定の両方で対応する（Q9=C、SECURITY-10準拠）
- 本ユニットにはAPI・データストア・認証機構が存在しないため、SECURITY-01/02/03/04/05/06/07/08/12/14はN/A。詳細はSecurity Complianceセクション参照

## Reliability Requirements
- **NFR-UNIT01-3**: 共通ErrorBoundaryコンポーネントをこのユニットで提供し、以降の全機能ユニットが画面単位で利用する（Q6=A）。ユーザ向けにはスタックトレース等の内部情報を含まない汎用フォールバック表示とし、SECURITY-15（フェイルクローズ・汎用エラーメッセージ）に整合させる

## Maintainability Requirements
- **NFR-UNIT01-4**: 専用のコンポーネントカタログ（Storybook等）は導入せず、TypeScriptの型定義とテストコード（Vitest + React Testing Library）を仕様の拠り所とする（Q5=C）

## Usability Requirements
- **NFR-UNIT01-5**: アクセシビリティは明確な準拠レベル（WCAG等）を設定せず、基本的なキーボード操作とARIA属性の付与のみ行う（Q4=B）
- **NFR-UNIT01-6**: レスポンシブのブレークポイントはデスクトップ・タブレットの2段階のみ定義する（Q8=A、NFR-8.1に整合）

## Tech Stack Selection
詳細は`tech-stack-decisions.md`参照。

## Security Compliance（Security Baseline拡張）

| ルール | 判定 | 根拠 |
|---|---|---|
| SECURITY-01 (暗号化) | N/A | データストアなし |
| SECURITY-02 (アクセスログ) | N/A | ネットワーク中継コンポーネントなし |
| SECURITY-03 (アプリケーションログ) | N/A | このユニットにサーバ/常駐プロセスなし |
| SECURITY-04 (HTTPセキュリティヘッダ) | N/A | このユニットにHTMLを配信するサーバエンドポイントなし。バックエンドが構築される後続ユニットで再評価 |
| SECURITY-05 (入力検証) | N/A | APIなし |
| SECURITY-06 (最小権限) | N/A | IAM/権限設定なし |
| SECURITY-07 (ネットワーク構成) | N/A | ネットワークリソースなし |
| SECURITY-08 (アプリレベルアクセス制御) | N/A | 保護対象リソース・エンドポイントなし |
| SECURITY-09 (ハードニング/エラー処理) | **準拠**（設計時点） | NFR-UNIT01-3（ErrorBoundary）により、ユーザ向けエラー表示は汎用メッセージとしスタックトレース等を露出しない方針を確定 |
| SECURITY-10 (サプライチェーン) | **準拠**（設計時点） | NFR-UNIT01-2（`npm audit` + Dependabot）、依存関係はpackage-lock.jsonで固定 |
| SECURITY-11 (セキュアデザイン) | N/A | 本ユニットにセキュリティクリティカルなロジックなし |
| SECURITY-12 (認証・認可) | N/A | 認証機構は本ユニットに含まれない（UNIT-02で対応） |
| SECURITY-13 (整合性検証) | N/A | 外部CDN・サードパーティスクリプトを使用しない方針（NFR-6.1、自作コンポーネント） |
| SECURITY-14 (アラート・監視) | N/A | サーバ/常駐プロセスなし |
| SECURITY-15 (例外処理・フェイルセーフ) | **準拠**（設計時点） | NFR-UNIT01-3（ErrorBoundary）によりフェイルクローズかつ汎用エラー表示を実現 |

**ブロッキングファインディング**: なし。

# UNIT-01 デザインシステム基盤 - NFR Requirements 計画

## カテゴリ適用性の評価

- **Scalability Requirements**: N/A — バックエンド・データストアを持たない静的フロントエンドのため、負荷・スケーリングの概念が該当しない
- **Availability Requirements**: N/A — 本ユニットは可用性SLAを持つ稼働システムではない。実際のデプロイ先の可用性は将来のInfrastructure Design／Operationsフェーズで扱う
- **Performance Requirements**: 該当（Q7）
- **Security Requirements**: 該当（Q1・Q2・Q10、および下記Security Baseline適用性評価）
- **Tech Stack Selection**: 該当（Q1〜Q4）
- **Reliability Requirements**: 該当（Q8）
- **Maintainability Requirements**: 該当（Q9）
- **Usability Requirements**: 該当（Q5・Q6）

## Security Baseline拡張の適用性評価（SECURITY-01〜15）

UNIT-01はバックエンド・データストア・認証・API・インフラを持たないため、大半のSECURITYルールはN/A。該当するもののみ質問に含める。

| ルール | 適用性 | 備考 |
|---|---|---|
| SECURITY-01〜08, 11, 12, 14 | N/A | データストア・API・認証・ネットワークインフラなし |
| SECURITY-09（ハードニング） | 該当（対応済み） | 「サンプル/デモページを本番に含めない」→ Functional Designでモックをdevビルド限定ルートに限定する決定済み |
| SECURITY-10（サプライチェーン） | 該当（Q10） | 依存関係のロックファイル・脆弱性スキャン |
| SECURITY-13（整合性検証） | 該当（Q1・Q2） | フォント等をセルフホストし外部CDNに依存しない方針の継続要否 |
| SECURITY-15（例外処理） | 該当（Q8） | 描画エラー時のフェイルセーフ（Error Boundary） |

## PBT拡張の適用性

N/A（フル適用）— UNIT-01は静的UIのみでアルゴリズム的処理を持たないため、PBT-01〜09は対象外。functional-design/frontend-components.mdの「Testable Properties」セクションに記録済み。

## 計画チェックリスト

- [x] Step A: 質問への回答を収集する
- [x] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）— 全問AI推奨どおりの回答、曖昧性なし
- [x] Step C: NFR要件を`aidlc-docs/construction/unit-01/nfr-requirements/`に文書化する
- [x] Step D: 完了メッセージを提示し、承認を得る — 承認済み（2026-07-20T14:40:00Z）

## 質問

### Question 1
本文用フォントの方針について

A) システムフォントスタック（游ゴシック／Noto Sans CJK JP等、OS標準）を使う。追加のダウンロード・ライセンス管理が不要

B) セルフホストのWebフォント（例: Noto Sans JP、npm経由でバンドル）を使う。外部CDNに依存せず表示の一貫性を担保する

C) Google Fonts等の外部CDN経由でWebフォントを読み込む

D) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 2
SQL/コード表示用（等幅）フォントの方針について

A) 本文と同じフォントで代用する（専用の等幅フォントは用意しない）

B) セルフホストの等幅Webフォント（例: Noto Sans Mono。日本語グリフ非対応部分は本文用フォントにフォールバック）を追加する

C) システムの等幅フォントスタック（Consolas/Menlo等）を使う

D) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 3
ダークモード対応について

A) 対応する（ライト／ダーク／システム追従の3モード、選択状態をlocalStorageに保存）

B) 対応する（ライト／ダークの2モードのみ、システム追従は無し）

C) 対応しない（ライトモードのみ）

D) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 4
多言語対応（i18n）について

A) 日本語のみ対応（当面i18nライブラリは導入しない）

B) 日本語をデフォルトとしつつ、i18nライブラリ（react-i18next等）を導入し英語にも対応できる構造にする

C) Other（[Answer]: の後に内容を記述）

[Answer]: B

### Question 5
アクセシビリティの目標水準について

A) WCAG 2.1 Level AA相当を目標とする（キーボード操作・スクリーンリーダー対応・コントラスト比等を考慮）

B) 明確な目標水準は定めず、基本的なセマンティックHTML・キーボード操作のみ配慮する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 6
ブラウザサポート範囲について

A) モダンブラウザの最新2バージョン（Chrome/Edge/Firefox/Safari）のみサポート。レガシーブラウザは対象外

B) より広い範囲（例: 1年以内にリリースされたバージョン全て）をサポート

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 7
バンドルサイズ・パフォーマンス方針について

A) 初期表示バンドルサイズの明確な数値目標は設けず、Viteの標準的な最適化（コード分割・Tree Shaking）に委ねる

B) 明確な数値目標（例: 初期JS 200KB以下）を設定し、CIで監視する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 8
描画エラー時のフォールバック（React Error Boundary）について

A) アプリ全体を覆うError Boundaryを1つ設置し、フォールバックUI（汎用エラーメッセージ）を表示する（SECURITY-15: フェイルセーフ）

B) Error Boundaryは設置しない（現時点では不要と判断）

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 9
コンポーネントカタログ・ドキュメントの方針について

A) Storybookは導入せず、アプリ内の軽量な一覧ページ（devビルド限定ルート、例: `/__mocks__/catalog`）で全コンポーネントを確認できるようにする

B) Storybookを導入し、コンポーネントカタログとして運用する

C) 現時点ではカタログ・ドキュメントの仕組みは用意しない

D) Other（[Answer]: の後に内容を記述）

[Answer]: A

### Question 10
依存関係の脆弱性スキャン（SECURITY-10）について

A) `npm audit`をローカル/CI実行の基本とし、詳細な自動化はUNIT-10（CI/CD）で構築する

B) Dependabot等の自動PR型ツールを今のうちに導入する

C) Other（[Answer]: の後に内容を記述）

[Answer]: A

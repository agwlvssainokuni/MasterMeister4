# UNIT-01（デザインシステム基盤）NFR Design 計画

`nfr-requirements.md`（Scalability/Availability=N/A、Performance=数値目標なし、Security=npm audit+Dependabot、Reliability=共通ErrorBoundary、Maintainability=カタログなし・型定義とテストが仕様、Usability=基本的なA11y対応・2段階ブレークポイント）を入力とし、これらをデザイン・論理コンポーネントのレベルに落とし込む。

## 実行チェックリスト

- [x] Step 1: NFR Requirementsを分析
- [x] Step 2: NFR Design評価カテゴリの当てはめ（N/A判定を含む）
- [x] Step 3: 質問を作成
- [ ] Step 4: ユーザ回答を収集
- [ ] Step 5: 曖昧な回答がないか分析
- [ ] Step 6: `nfr-design-patterns.md` / `logical-components.md` を生成
- [ ] Step 7: 完了メッセージを提示し承認を得る

## カテゴリ別評価

| カテゴリ | 判定 | 理由 |
|---|---|---|
| Resilience Patterns | 質問あり（Q1, Q2） | NFR-UNIT01-3（共通ErrorBoundary）の粒度・配置とエラー記録方針を具体化する必要あり |
| Scalability Patterns | **N/A** | NFR RequirementsでScalability自体がN/A（静的コンポーネント群） |
| Performance Patterns | 質問あり（Q3） | NFR-UNIT01-1（標準的ベストプラクティス踏襲）を、コンポーネントのエクスポート方式（Tree-shaking可否）という具体的パターンに落とし込む必要あり |
| Security Patterns | 質問あり（Q6） | XSS対策の設計原則（Reactエスケープに委ねる方針の明文化） |
| Logical Components | 質問あり（Q4, Q5） | デザインシステムのリポジトリ配置、デザイントークンの構造（将来のテーマ切替可否）が未確定 |

## 質問

各設問に A/B/C... の記号で回答してください。当てはまる選択肢がない場合は最後の「Other」を選び、[Answer]: の後ろに内容を記述してください。

### Question 1: ErrorBoundaryの粒度・配置
NFR RequirementsでEXECUTEと決定した共通ErrorBoundaryについて、どの粒度で配置しますか？

A) アプリケーション全体で1つのトップレベルErrorBoundaryのみ配置する

B) トップレベルに加え、各機能ユニットの主要画面（ルート）単位にもErrorBoundaryを配置し、1画面のエラーが他画面に波及しないようにする

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 2: エラー発生時のログ記録
ErrorBoundaryが捕捉したエラーをどう記録しますか？

A) このユニットの時点ではconsole.error等の開発者向け出力のみ行う（監査ログ記録基盤はUNIT-02で構築されるため、フロントエンドからの送信連携は後続ユニットで統合する）

B) このユニットで、将来のログ送信APIへの接続点（呼び出し先未実装のプレースホルダー関数）を用意しておく

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 3: コンポーネントのエクスポート方式
共通UIコンポーネントのエクスポート方式（バンドルサイズ・Tree-shakingに影響）はどうしますか？

A) 各コンポーネントを名前付きエクスポートし、バレルファイル（index.ts等）で再エクスポートする。バンドラーのTree-shakingにより未使用コンポーネントはバンドルから除外される構成とする

B) バレルファイルは作らず、各コンポーネントを個別のファイルパスから直接importする方式とする

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 4: デザインシステムのリポジトリ配置
デザインシステムのコードはリポジトリ内のどこに配置しますか？

A) フロントエンドアプリ内の1ディレクトリ（例: `frontend/src/design-system/`）として配置し、他機能ユニットと同じビルド・パッケージに含める

B) npm workspaces等を用いた独立パッケージ（例: `frontend/packages/design-system/`）として切り出す

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 5: デザイントークンの構造
色・タイポグラフィ・間隔等のデザイントークンはどう構造化しますか？

A) ライトテーマのみを前提としたデザイントークンをCSS変数として定義する（ダークモード等の将来のテーマ切替は本ユニットのスコープ外とし、必要になった時点で再設計する）

B) 将来のテーマ切替（ダークモード等）を見据え、トークンを最初からテーマ切替可能な構造（テーマ別のCSS変数セット等）で設計する

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 6: XSS対策の設計原則
共通UIコンポーネントにおけるXSS対策の設計方針はどうしますか？

A) 全コンポーネントで`dangerouslySetInnerHTML`を使用しない方針を徹底し、Reactの標準エスケープに委ねる。将来リッチテキスト表示等が必要になった場合は、その時点で個別にサニタイズライブラリの導入を検討する

B) 特に方針は定めず、実装時に個別のコンポーネント単位で判断する

X) Other（[Answer]: の後に内容を記述）

[Answer]:

---

すべての設問に回答後、完了したことを教えてください。回答内容を分析し、曖昧な点があれば追加の確認質問を作成します。

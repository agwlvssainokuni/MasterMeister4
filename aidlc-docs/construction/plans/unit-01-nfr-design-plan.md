# UNIT-01 デザインシステム基盤 - NFR Design 計画

## カテゴリ適用性の評価

- **Scalability Patterns**: N/A — バックエンド・データストアを持たない静的フロントエンドのため該当しない
- **Resilience Patterns**: 該当（Q5）— Error Boundaryのログ出力方針のみ
- **Performance Patterns**: 該当（Q3・Q4・Q6）
- **Security Patterns**: 該当（Q6、フォントのセルフホスト方式継続）
- **Logical Components**: 該当（Q1・Q2・Q3・Q7）

## 計画チェックリスト

- [ ] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `nfr-design-patterns.md` と `logical-components.md` を作成する
- [ ] Step D: 完了メッセージを提示し、承認を得る

## 質問

### Question 1
デザイントークンのアーキテクチャについて

A) 2層構成とする（プリミティブトークン`--mm-palette-*` + セマンティックトークン`--mm-color-*`/`--mm-font-*`/`--mm-space-*`等）。コンポーネントは常にセマンティックトークンのみを参照し、パレット値を直接参照しない

B) 1層構成とする（コンポーネントが直接パレット値を参照する。シンプルだがテーマ切り替え時の再定義の柔軟性が低い）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 2
ダークモードのテーマ切り替え実装パターンについて

A) `<html data-theme="light|dark">`属性を切り替え、セマンティックトークン（CSS変数）の値をテーマごとに再定義する方式

B) テーマごとに別のCSSファイル/クラスを丸ごと切り替える方式

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 3
i18n翻訳リソースの管理パターンについて

A) 機能（ユニット）単位で翻訳リソースファイルを分割し、react-i18nextの名前空間機能で必要な範囲のみ読み込む

B) 単一の翻訳リソースファイルにすべての文言をまとめる

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 4
コード分割（devビルド限定のモック/カタログルート）について

A) `React.lazy`等で遅延読み込みし、本番ビルドのバンドルに影響を与えない構造にする

B) 特にコード分割は行わない

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 5
Error Boundaryのログ出力方針について

A) 現時点ではブラウザコンソールへの出力のみとする（バックエンドへのエラーレポート送信APIは未実装のため）。エラーレポート送信の仕組みは将来のユニットで検討する

B) 今のうちにエラーレポート送信の仕組み（送信先API等）まで設計する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 6
フォント読み込みパターンについて

A) `@fontsource`パッケージ経由でCSSに`@font-face`を静的にバンドルし、`font-display: swap`を設定する

B) JS経由での動的フォントローディング制御を行う

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 7
Gradle Node Pluginの統合パターン（単一WAR生成に関連）について

A) `frontend`サブプロジェクトに`com.github.node-gradle.node`プラグインを適用し、`npmInstall`/`npmBuild`タスクを`backend`の`bootWar`タスクの依存関係として接続する（`backend:build`単体には影響しない構成を維持）

B) シェルスクリプト等、Gradle Node Plugin以外の方法で統合する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

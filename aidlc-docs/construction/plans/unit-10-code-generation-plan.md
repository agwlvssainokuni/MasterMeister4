# UNIT-10 CI/CD - Code Generation 計画

## Unit Context

- **対応エピック/コンポーネント**: なし（NFR-10.1〜10.3に対応する開発インフラタスク、ユーザーストーリー化していない）
- **対応要件**: requirements.md NFR-10.1〜10.3、§4（プロジェクト構造）、NFR-4.4（依存関係脆弱性スキャン）
- **責務**: GitHub Actionsによるビルド・テスト自動化（CI）、タグpushトリガーでのGitHub Releases作成（CD）
- **前提ユニット**: UNIT-01〜UNIT-09すべて（最終ユニット、`unit-of-work.md`のとおり）
- **本ユニットが所有するデータ**: なし（`.github/workflows/`配下のワークフロー定義のみ、アプリケーションコードへの変更なし）
- **Functional Design/NFR Requirements/NFR Design/Infrastructure Design**: いずれもSKIP判定済み（aidlc-state.md参照）。技術選定はNFR-10.1〜10.3で確定済み（GitHub Actions、タグpushトリガー）

## Part 1計画作成時の実装判断

- 既存プロジェクト構成（Gradleマルチモジュール: `backend`/`frontend`/`cherry-mustache-core`、Java 25 toolchain、Gradle 9.6.1、Node.js 26.5.0）をそのまま前提とする
- CIワークフローは`push`（`main`ブランチ）・`pull_request`をトリガーとし、バックエンド全体（`./gradlew build`、`cherry-mustache-core`・`backend`両方のテストを含む。`frontend`サブプロジェクトは`assemble`がnpmBuildに依存するため`backend`単体ビルドに影響しないよう既存の`tasks.named("assemble") { setDependsOn(emptyList()) }`設定を踏襲し、`./gradlew build`はバックエンドのみを対象とする）とフロントエンド単体（`npm ci`・`npm run lint`・`npm test -- --run`・`npm run build`）を別ジョブとして並行実行する
- リリースワークフローは`v*`形式のタグpushをトリガーとし、`./gradlew bootWar`でリリース用WARをビルドし、GitHub Releasesを作成してWARファイルを成果物として添付する（NFR-10.3、`feedback_deployment_artifact.md`の運用ルールに従い`bootWar`を使用）
- devenv（PostgreSQL/MySQL/MariaDB）を使った実機E2E検証はCIには組み込まない（requirements.md NFR-10.1の「ローカルでのビルド・テスト手順の整備までとし」というスコープに従う。各ユニットのCode Generationで実施済みの実機E2E検証はローカル手順として`aidlc-docs/construction/build-and-test/`に既存整備済みのため、CIでは単体テスト・統合テスト（`@DataJpaTest`等のH2ベース）の自動実行のみを対象とする）
- OWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）はCIワークフローに組み込むが、NVD APIキーがリポジトリシークレットに未設定の場合は失敗せず警告に留める設定とする（既存の各ユニットCode Generation時と同じ運用: NVD APIキー未設定のため実施見送りが常態化している状況を踏まえ、シークレット未設定時はスキップするジョブ条件を設ける）
- 新規外部ライブラリ・Build Configuration変更（Gradle/npm設定自体）は不要。`.github/workflows/`配下の新規ファイルのみ追加する

## 計画チェックリスト

### 1. CI Workflow（ビルド・テスト自動化）

- [ ] Step 1.1: `.github/workflows/ci.yml`を作成する（`push`: `main`ブランチ、`pull_request`をトリガー。`backend`ジョブ: `actions/setup-java`（Java 25）→`./gradlew build`（`cherry-mustache-core`・`backend`の全テスト含む）。`frontend`ジョブ: `actions/setup-node`（Node.js 26）→`npm ci`→`npm run lint`→`npm test -- --run`→`npm run build`。2ジョブは並行実行）
- [ ] Step 1.2: OWASP Dependency-Checkジョブを追加する（NVD APIキーがリポジトリシークレット`NVD_API_KEY`に設定されている場合のみ実行する条件分岐、`continue-on-error: true`で失敗時もワークフロー全体を止めない）

### 2. Release Workflow（タグpushトリガーのGitHub Releases作成）

- [ ] Step 2.1: `.github/workflows/release.yml`を作成する（`push`: `v*`形式のタグをトリガー。`actions/setup-java`（Java 25）→`actions/setup-node`（Node.js 26）→`./gradlew bootWar`→生成された`mastermeister-*.war`を成果物としてGitHub Releasesに添付、リリースノートは自動生成（`generate_release_notes: true`）)

### 3. ドキュメント更新

- [ ] Step 3.1: リポジトリルートの`README.md`（存在すれば）またはプロジェクト全体のREADMEに、CI/CDワークフローの概要（トリガー条件、リリース手順）を追記する
- [ ] Step 3.2: `aidlc-docs/construction/unit-10/code/ci-cd-summary.md`を作成する

### 4. 最終検証

- [ ] Step 4.1: 作成したワークフローYAMLの構文妥当性を確認する（YAMLパーサでの構文チェック）
- [ ] Step 4.2: ローカルで`./gradlew build`（バックエンド全件）・`npm ci && npm run lint && npm test -- --run && npm run build`（フロントエンド）を実行し、CIワークフローが実行するコマンドと同一の手順でエラーなく成功することを確認する（GitHub Actions環境そのものでの実行確認はスコープ外、ローカルでのコマンド等価性確認に留める）
- [ ] Step 4.3: `./gradlew bootWar`を実行し、リリースワークフローが生成する成果物（WARファイル）が問題なく生成されることを確認する

## Story Traceability

- NFR-10.1〜10.3（対応ユーザーストーリーなし、開発インフラタスク） — Step 1.1〜2.1

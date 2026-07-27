# UNIT-10 CI/CD - Summary

## 作成したファイル

- **`.github/workflows/ci.yml`**（新規）: `push`（`main`）・`pull_request`をトリガーに、`backend`（`./gradlew build`、`cherry-mustache-core`含む）・`frontend`（`npm ci`→`lint`→`test`→`build`）を並行ジョブで実行。`dependency-check`ジョブはOWASP Dependency-Check（`:backend:dependencyCheckAnalyze`）を、リポジトリシークレット`NVD_API_KEY`が設定されている場合のみ実行し、未設定時は当該ステップのみスキップする（後述「実機検証で発見したエラー」参照）。実行しても`continue-on-error: true`のためワークフロー全体は失敗させない
- **`.github/workflows/release.yml`**（新規）: `v*`形式のタグpushをトリガーに、`bootWar`でビルドしたWARをGitHub Releasesに添付する。ビルド実行前にタグ名（`v`除去）と`backend/build.gradle.kts`の`version`の一致を検証し、不一致の場合は`exit 1`でジョブを失敗させリリースを中断する（ユーザー指示によるバージョン整合性チェック）

## 実装時の変更・発見

- **初期バージョンを`0.0.0`に統一**（ユーザー指示）: `backend/build.gradle.kts`の`version`を`"0.0.1-SNAPSHOT"`から`"0.0.0"`に変更した。実装過程で`frontend/src/design-system/components/Footer.tsx`のフッター表示にも同じバージョン文字列`'0.0.1-SNAPSHOT'`がハードコードされていることを発見し、`'0.0.0'`に統一した。`cherry-mustache-core`は独立したサブプロジェクトで別バージョニング（現状`0.1.0`のまま）のため対象外
- **バージョン整合性チェックの実装方式**: `./gradlew :backend:properties -q | grep '^version:' | awk '{print $2}'`でGradleの`version`プロパティを取得し、`${GITHUB_REF_NAME#v}`でタグ名から`v`プレフィックスを除去した値と文字列比較する。実際に`version: 0.0.0`が出力されることをローカルで確認済み
- **リポジトリルートに`README.md`が存在しない**: 計画時点で想定していた「ルートREADMEへの追記」が対象なしだったため、既存パターン（各ユニットのCode Generationで`backend/README.md`・`frontend/README.md`に追記）を踏襲し、両ファイルにCI/CDセクションを追加した
- devenv（PostgreSQL/MySQL/MariaDB）を使った実機E2E検証はCIワークフローに含めない（NFR-10.1のスコープに従い、ローカル手順の整備までとする）

## 検証結果

- 作成したYAMLファイル2件の構文妥当性をPythonの`yaml`モジュールで確認済み（構文エラーなし）
- ローカルで`./gradlew :cherry-mustache-core:build :backend:build`を実行し、CIワークフローのbackendジョブと同一の手順でエラーなく成功することを確認
- `./gradlew :backend:bootWar`を実行し、リリースワークフローが生成する成果物（`mastermeister-0.0.0.war`）が問題なく生成されることを確認。フロントエンドのビルド確認は`bootWar`が内部で`npmInstall`→`npmBuild`を実行するため、これで兼ねられる（ユーザー指摘）
- GitHub Actions環境そのものでの実行確認（実際のpush/タグpushでのワークフロートリガー）はローカル環境からは不可能なためスコープ外とし、コマンド等価性の確認に留めた

### 実装時に発見した2つの問題

1. **ci.ymlの`./gradlew build`（ルートタスク）はfrontendのビルドまで巻き込み失敗する**: 当初backendジョブで`./gradlew build`と記載していたが、ルートの`build`タスクは全サブプロジェクト（`backend`・`frontend`・`cherry-mustache-core`）を対象にする。既存の`tasks.named("assemble") { setDependsOn(emptyList()) }`（`backend/build.gradle.kts`）は`backend`単体の`assemble`にのみ影響し、ルートタスクには効かない。`./gradlew :cherry-mustache-core:build :backend:build`とサブプロジェクトを明示指定する形に修正した
2. **Gradleデーモンが起動時のPATHをキャッシュし`npm`コマンドを認識できないことがある**: Volta経由でインストールされた`npm`が、既に起動済みのGradleデーモンからは見えず`bootWar`が失敗した。`./gradlew --stop`でデーモンを再起動すると解消した。CI環境（`actions/setup-node`で毎回新規セットアップ）では発生しない、ローカル検証固有の事象

## 完了報告後の指摘対応（アクションバージョンの最新化）

「アクションのバージョンを最新化しておいて」との指摘を受け、各アクションの最新メジャーバージョンをWeb検索で確認し更新した。

| アクション | 変更前 | 変更後 |
|---|---|---|
| `actions/checkout` | v4 | v7 |
| `actions/setup-java` | v4 | v5 |
| `actions/setup-node` | v4 | v7 |
| `gradle/actions/setup-gradle` | v4 | v6 |
| `softprops/action-gh-release` | v2 | v3 |

修正後、両ワークフローYAMLの構文妥当性をPythonの`yaml`モジュールで再確認した（構文エラーなし）。

## 承認後に発見されたエラーの修正（IDE上でのGitHub Actions構文検証）

ユーザーがIDE上でci.ymlを開いた際、`(Line: 58, Col: 9): Unrecognized named-value: 'secrets'`というエラーが表示された。

**原因**: GitHub Actionsでは、ジョブレベルの`if`条件式では`secrets`コンテキストを参照できない（既知の制約。`with:`ブロックや`if:`の評価はジョブ実行前の限定的なコンテキストで行われるため）。`secrets`コンテキストが利用できるのはステップレベルの式のみ。

**修正（1回目）**: `dependency-check`ジョブの`if: ${{ secrets.NVD_API_KEY != '' }}`（ジョブレベル）を削除し、実際にスキャンを実行する`OWASP Dependency-Check`ステップの`if:`に移した。これによりジョブ自体（`checkout`・`setup-java`・`setup-gradle`）は常に起動するが、NVD APIキー未設定時は実際のスキャン実行ステップのみがスキップされる。当初意図していた「ジョブ全体のスキップ」ではなく「ステップ単位のスキップ」になるが、`continue-on-error: true`と合わせて実質的な挙動（失敗せず実行もしない）は変わらない。

**修正（2回目）**: ステップレベルの`if:`に移した後も同じ`Unrecognized named-value: 'secrets'`エラーが再発した。調査の結果、`secrets`コンテキストは`if`条件式内で直接参照すると（ステップレベルであっても）未定義扱いになりうる既知の問題があり、確実な回避策は「`env:`へ一旦マッピングしてから`env`コンテキスト経由で判定する」方法であることが判明した。ジョブレベルに`env: { NVD_API_KEY: ${{ secrets.NVD_API_KEY }} }`を設定し、`OWASP Dependency-Check`ステップの`if:`を`${{ env.NVD_API_KEY != '' }}`に変更して解消した。

# Build Instructions

## Prerequisites

- **Build Tool**: Gradle 9.6.1（Gradle Wrapper同梱、`./gradlew`経由で実行するためGradle自体の個別インストールは不要）
- **JDK**: Java 25（`backend/build.gradle.kts`の`toolchain`で指定。Gradle toolchainにより、ローカルにJDK 25がなくても自動ダウンロードされる）
- **Node.js**: 26.x系（`frontend/build.gradle.kts`のNode Gradle Pluginで指定。`download.set(false)`のため、実行環境に事前インストールされたNode.js/npmを利用する）
- **依存関係**: Gradle依存関係はビルド時に自動解決される。フロントエンドの`node_modules`はGradle Node Pluginの`npmInstall`タスク（`npmBuild`が依存）で自動インストールされる
- **環境変数**: 通常のビルド・単体テスト実行には環境変数は不要（`AppProperties`が参照する`MM_APP_*`系環境変数は実行時にのみ必要、単体テストは`@TestPropertySource`等でテスト用の値を注入する）
- **システム要件**: 特別な要件なし（通常の開発マシンで十分。ディスク容量目安: Gradle/npm依存関係キャッシュ含め数GB）

## Build Steps

### 1. リポジトリ構成の確認

```bash
# ルートに settings.gradle.kts があり、backend/frontend/cherry-mustache-core の
# 3サブプロジェクトから構成される（Gradleマルチモジュール）
cat settings.gradle.kts
```

### 2. バックエンドのビルド（cherry-mustache-core含む）

```bash
./gradlew :cherry-mustache-core:build :backend:build
```

`cherry-mustache-core`（自作Mustacheテンプレートエンジン、UNIT-02で追加）は`backend`から`implementation`依存されているサブプロジェクトであり、`:backend:build`単体ではコンパイル成果物のみが生成されテストは実行されない（UNIT-10 Code Generation時に検証済み）。両方を明示的に指定することで、両サブプロジェクトの単体テストを含めてビルドする。

### 3. フロントエンドのビルド・テスト

```bash
cd frontend
npm ci
npm run lint
npm test -- --run
npm run build
cd ..
```

### 4. リリース用単一WARの生成（フロントエンド内包）

```bash
./gradlew :backend:bootWar
```

`bootWar`はGradle Node Pluginの`npmInstall`→`npmBuild`（`npm run build`相当）に依存しており、上記手順3を個別に実行しなくても、このコマンド単体でフロントエンドのビルドも含めて実行される（UNIT-10 Code Generation時の実装判断）。生成された`backend/build/libs/mastermeister-<version>.war`は、`java -jar`による自己完結実行（`SpringBootServletInitializer`継承）・外部Tomcatへの配備の両方に対応する（NFR-2.2/2.6）。

### 5. ビルド成功の確認

- **期待される出力**: 各コマンドが`BUILD SUCCESSFUL`（Gradle）・エラーなし終了（npm）で完了する
- **ビルド成果物**:
  - `backend/build/libs/mastermeister-<version>.war`（リリース用単一WAR）
  - `cherry-mustache-core/build/libs/*.jar`（内部依存用、単体では配布しない）
  - `frontend/dist/`（フロントエンドのビルド成果物、`bootWar`実行時に`backend`へ内包される）
- **許容される警告**:
  - `Using H2 <version> which is newer than the version Flyway has been verified with.`（Flywayの検証済みバージョンより新しいH2を使用している旨の警告、動作に支障なし）
  - `The cache 'effectivePermission' is not recording statistics.`（Caffeineキャッシュの統計未記録警告、動作に支障なし）
  - `react(only-export-components)`警告3件（`AuthContext.tsx`、`Toast.tsx`、`ThemeProvider.tsx`。Fast Refresh最適化に関する既知の警告で機能に影響なし）

## Troubleshooting

### Gradleデーモンが古いPATHをキャッシュしnpmコマンドを認識しない

- **原因**: Volta等のNode.jsバージョンマネージャー導入前後でGradleデーモンが起動したままの場合、デーモンプロセスが起動時点のPATH環境変数を保持し続け、新しくインストールされた`npm`を認識できないことがある（UNIT-10 Code Generation時に発見）
- **解決策**: `./gradlew --stop`でGradleデーモンを停止し、次回実行時に新しいデーモンを起動させる

### ルートの`./gradlew build`がfrontendのビルドエラーで失敗する

- **原因**: ルートタスクの`build`は全サブプロジェクト（`backend`・`frontend`・`cherry-mustache-core`）を対象にする。`backend/build.gradle.kts`の`tasks.named("assemble") { setDependsOn(emptyList()) }`設定は`backend`単体の`assemble`にのみ影響し、ルートタスクには効かない（UNIT-10 Code Generation時に発見）
- **解決策**: `./gradlew :cherry-mustache-core:build :backend:build`のようにサブプロジェクトを明示的に指定する。フロントエンドを含めたい場合は`./gradlew :backend:bootWar`を使う

### ビルドが依存関係エラーで失敗する

- **原因**: ネットワーク接続不良、Gradle/npmレジストリの一時的な障害
- **解決策**: `./gradlew build --refresh-dependencies`（Gradle）、`npm ci`の再実行（frontend）

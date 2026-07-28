import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'

/**
 * ローカルでのE2Eスモークテスト実行を優先し、CI統合は別途検討する
 * （ユーザー指示、Backlog記載の課題への対応）。
 * backend/frontendはこのディレクトリから独立させ、E2Eテスト自体の依存関係が
 * frontendのビルド・単体テストプロセスに混ざらないようにする。
 */

// Gradleの:backend:bootRunはbackendサブプロジェクトのディレクトリを実行時のカレント
// ディレクトリにするため、相対パスで指定するとbackend/e2e/...に解決されてしまう
// （実際に発生した事象）。絶対パスで明示することで回避する。
const e2eDataPath = path.resolve(import.meta.dirname, '.tmp/e2e-data/mastermeister')
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: 'http://localhost:5173',
    // frontendのi18n初期言語はnavigator.languageで判定される（detectInitialLanguage）。
    // localeを明示しないとPlaywrightのデフォルト(en-US)になり英語UIで表示されるため指定する。
    locale: 'ja-JP',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      // reuseExistingServerがtrueの場合、webServerはテスト実行全体で1回だけ起動される。
      // 起動前にDBファイルを削除することで、複数回のテスト実行をまたいだデータ蓄積を防ぎ
      // 各実行が同じクリーンな状態から始まるようにする（実機検証で発見: 削除済み接続の
      // プレースホルダーが複数回の実行分累積し、テストが不安定になった）。
      command:
        `rm -rf ${path.dirname(e2eDataPath)} && ` +
        'MM_APP_JWT_SECRET=e2e-test-secret-key-at-least-32-bytes-long ' +
        'MM_APP_RDBMS_ENCRYPTION_KEYS=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE= ' +
        'MM_APP_ADMIN_BOOTSTRAP_EMAIL=e2e-admin@example.com ' +
        'MM_APP_ADMIN_BOOTSTRAP_PASSWORD=E2eAdminP@ss9x7Q ' +
        `MM_APP_DATASOURCE_PATH=${e2eDataPath} ` +
        './gradlew :backend:bootRun',
      port: 8080,
      cwd: '..',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: 'npm run dev',
      port: 5173,
      cwd: '../frontend',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
  ],
})

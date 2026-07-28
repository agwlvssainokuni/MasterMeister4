import { test, expect } from '@playwright/test'

/**
 * クエリビルダー → クエリ実行 → 保存クエリ → クエリ履歴のスモークテスト。
 * Integration Test Instructions（aidlc-docs/construction/build-and-test/
 * integration-test-instructions.md）のScenario 3に対応する。
 */
test.describe.serial('クエリビルダーから実行・保存・履歴確認まで', () => {
  const uniqueSuffix = Date.now()
  const connectionDisplayName = `E2E-QB-${uniqueSuffix}`
  const savedQueryName = `E2E保存クエリ-${uniqueSuffix}`

  const adminEmail = 'e2e-admin@example.com'
  const adminPassword = 'E2eAdminP@ss9x7Q'

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(adminEmail)
    await page.getByTestId('login-password-input').fill(adminPassword)
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL('/')
  })

  test('接続登録・スキーマ取込・権限設定を行う', async ({ page }) => {
    await page.goto('/connections')
    await page.getByTestId('connections-add-button').click()
    await page.getByTestId('connections-form-display-name').fill(connectionDisplayName)
    await page.getByTestId('connections-form-db-type').selectOption('POSTGRESQL')
    await page.getByTestId('connections-form-host').fill('localhost')
    await page.getByTestId('connections-form-port').fill('5432')
    await page.getByTestId('connections-form-database-name').fill('mastermeister')
    await page.getByTestId('connections-form-username').fill('postgres')
    await page.getByTestId('connections-form-password').fill('mastermeister')
    await page.getByTestId('connections-form-submit').click()

    const row = page.getByRole('row', { name: new RegExp(connectionDisplayName) })
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: 'スキーマ取込' }).click()
    await expect(page.getByText('スキーマ取込に成功しました')).toBeVisible()

    await row.getByRole('link', { name: '権限設定' }).click()
    await expect(page).toHaveURL(/\/permissions\/\d+/)
    await page.getByTestId('permissions-principal-type').selectOption('USER')
    await page
      .getByTestId('permissions-principal-select')
      .selectOption({ label: `${adminEmail}（${adminEmail}）` })
    await page.getByTestId('permissions-schema-toggle-public').click()
    await page.getByTestId('permissions-primary-public').selectOption('READ')
    await expect(page.getByTestId('permissions-primary-public')).toHaveValue('READ')
  })

  test('クエリビルダーでSQLを組み立てて実行できる', async ({ page }) => {
    await page.goto('/query-builder')
    const connectionRow = page.getByRole('button', { name: new RegExp(connectionDisplayName) })
    await expect(connectionRow).toBeVisible()
    await connectionRow.click()

    await expect(page).toHaveURL(/\/query-builder\/\d+/)
    // FROMタブ（既定でアクティブ）でテーブルを選択する
    await page.getByLabel('テーブル').selectOption('categories')
    // SELECTタブへ切り替え、追加ボタンで先頭カラムを自動選択する
    await page.getByRole('tab', { name: 'SELECT' }).click()
    await page.getByTestId('query-builder-select-item-add').click()

    // SQLが生成され実行ボタンが有効になるまで待つ（デバウンス400ms）
    await expect(page.getByTestId('query-builder-execute-button')).toBeEnabled()
    await page.getByTestId('query-builder-execute-button').click()

    await expect(page).toHaveURL(/\/query-execution\/\d+/)
    await expect(page.getByTestId('query-editor-sql-input')).not.toHaveValue('')
    await page.getByTestId('query-editor-execute-button').click()
    await expect(page.getByTestId('data-table')).toBeVisible()

    // 名前を付けて保存 → 保存クエリ新規作成画面へ遷移
    await page.getByTestId('query-execution-save-as-button').click()
    await expect(page).toHaveURL(/\/saved-queries\/\d+\/new/)
    await page.getByTestId('saved-query-save-button').click()
    const saveDialog = page.getByRole('dialog')
    await saveDialog.getByLabel('クエリ名').fill(savedQueryName)
    await saveDialog.getByRole('button', { name: '保存' }).click()

    await expect(page).toHaveURL(/\/saved-queries\/\d+\/\d+$/)
    await expect(page.getByRole('heading', { name: savedQueryName })).toBeVisible()
  })

  test('クエリ履歴に実行記録が表示される', async ({ page }) => {
    await page.goto('/query-history')
    const connectionRow = page.getByRole('button', { name: new RegExp(connectionDisplayName) })
    await expect(connectionRow).toBeVisible()
    await connectionRow.click()

    await expect(page).toHaveURL(/\/query-history\/\d+/)
    await expect(page.getByTestId('data-table')).toBeVisible()
    // ad-hoc実行の記録が1件以上表示される（SELECT文のSQLテキストを含む行）
    await expect(page.getByText(/SELECT/)).toBeVisible()
  })
})

import { test, expect } from '@playwright/test'
import { login, registerAndApproveUser } from './helpers/registration'

/**
 * 各種操作 → 監査ログ閲覧のスモークテスト。
 * Integration Test Instructions（aidlc-docs/construction/build-and-test/
 * integration-test-instructions.md）のScenario 4に対応する。
 */
test.describe.serial('監査ログ閲覧', () => {
  const uniqueSuffix = Date.now()
  const connectionDisplayName = `E2E-Audit-${uniqueSuffix}`
  const generalUserEmail = `e2e-audit-user-${uniqueSuffix}@example.com`
  const generalUserPassword = 'E2eAuditP@ss5k2M'

  const adminEmail = 'e2e-admin@example.com'
  const adminPassword = 'E2eAdminP@ss9x7Q'

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(adminEmail)
    await page.getByTestId('login-password-input').fill(adminPassword)
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL('/')
  })

  test('接続登録イベントが監査ログに記録され、イベント種別で絞り込める', async ({ page }) => {
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
    await expect(page.getByRole('row', { name: new RegExp(connectionDisplayName) })).toBeVisible()

    await page.goto('/audit-log')
    await page.getByLabel('イベント種別').selectOption('CONNECTION_REGISTERED')
    // targetResource列・対象接続列の両方に接続表示名が入るため複数マッチする、.first()で十分
    await expect(page.getByRole('cell', { name: connectionDisplayName }).first()).toBeVisible()
  })

  test('対象接続・結果で絞り込める', async ({ page }) => {
    await page.goto('/audit-log')
    await page.getByLabel('対象接続').selectOption({ label: connectionDisplayName })
    await expect(page.getByRole('cell', { name: connectionDisplayName }).first()).toBeVisible()

    await page.getByLabel('結果').selectOption('FAILURE')
    // 今回のシナリオでは失敗イベントを発生させていないため0件になる
    await expect(page.getByRole('cell', { name: connectionDisplayName })).toHaveCount(0)
  })

  test('一般ユーザは監査ログAPIへのアクセスを403で拒否される', async ({ request, baseURL }) => {
    if (!baseURL) {
      throw new Error('baseURL is not configured')
    }
    const adminToken = await login(request, baseURL, adminEmail, adminPassword)
    await registerAndApproveUser(
      request,
      baseURL,
      adminToken,
      generalUserEmail,
      generalUserPassword,
      'E2E監査ログ検証ユーザー',
    )
    const userToken = await login(request, baseURL, generalUserEmail, generalUserPassword)

    const response = await request.get(`${baseURL}/api/admin/audit-log`, {
      headers: { Authorization: `Bearer ${userToken}` },
    })
    expect(response.status()).toBe(403)
  })

  test('一般ユーザには監査ログのナビ項目・ホームカードが表示されない', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(generalUserEmail)
    await page.getByTestId('login-password-input').fill(generalUserPassword)
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL('/')

    await expect(page.getByRole('button', { name: '監査ログ' })).not.toBeVisible()
    await expect(page.getByTestId('feature-card-audit-log')).not.toBeVisible()
  })
})

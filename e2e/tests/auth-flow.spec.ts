/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { test, expect } from '@playwright/test'
import { extractTokenFromMailText, waitForLatestMailText } from './helpers/mailpit'

/**
 * 認証フローのスモークテスト（ユーザ登録申請 → 管理者承認 → ログイン）。
 * UNIT-02の中核フローを、実際に起動したbackend/frontend/Mailpitに対して確認する。
 * ユーザー指示によりまずローカルでの実行を優先し、CI統合は別途検討する。
 */
test.describe.serial('認証フロー', () => {
  const uniqueSuffix = Date.now()
  const newUserEmail = `e2e-user-${uniqueSuffix}@example.com`
  const newUserPassword = 'E2eUserP@ss3z8K'
  const newUserFullName = 'E2E テストユーザー'

  const adminEmail = 'e2e-admin@example.com'
  const adminPassword = 'E2eAdminP@ss9x7Q'

  test('ユーザ登録申請からメール送信までが成功する', async ({ page }) => {
    await page.goto('/register')
    await page.getByTestId('register-step1-email-input').fill(newUserEmail)
    await page.getByTestId('register-step1-submit-button').click()
    await expect(page.getByText('確認メールを送信しました')).toBeVisible()
  })

  test('登録完了（パスワード設定）まで成功する', async ({ page, request }) => {
    const mailText = await waitForLatestMailText(request, newUserEmail)
    const token = extractTokenFromMailText(mailText)

    await page.goto(`/register/complete?token=${token}`)
    await page.getByTestId('register-step2-fullname-input').fill(newUserFullName)
    await page.getByTestId('register-step2-password-input').fill(newUserPassword)
    await page.getByTestId('register-step2-password-confirm-input').fill(newUserPassword)
    await page.getByTestId('register-step2-submit-button').click()
    await expect(page.getByText('登録が完了しました')).toBeVisible()
  })

  test('管理者が新規ユーザを承認できる', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(adminEmail)
    await page.getByTestId('login-password-input').fill(adminPassword)
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL('/')

    await page.goto('/users')
    const row = page.getByRole('row', { name: new RegExp(newUserEmail) })
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: '承認' }).click()

    const dialog = page.getByRole('dialog')
    await dialog.getByRole('button', { name: '承認' }).click()

    // 承認によりステータスが変わり、既定の「承認待ち」フィルタから外れて一覧から消えるため、
    // 「すべて」に切り替えてから確認する。承認処理と競合してもレースコンディションが起きないことを
    // 確認する（UserManagementPage.tsxのloadUsersRef対応、実機検証で発見・修正済み）
    await page.getByTestId('users-status-filter').selectOption('ALL')

    await expect(row.getByText('承認済み')).toBeVisible()
  })

  test('承認された新規ユーザがログインできる', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(newUserEmail)
    await page.getByTestId('login-password-input').fill(newUserPassword)
    await page.getByTestId('login-submit-button').click()

    await expect(page).toHaveURL('/')
    await expect(page.getByText('ようこそ')).toBeVisible()
  })
})

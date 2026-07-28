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

/**
 * 接続登録 → スキーマ取込 → 権限設定 → マスタデータ表示のスモークテスト。
 * Integration Test Instructions（aidlc-docs/construction/build-and-test/
 * integration-test-instructions.md）のScenario 2に対応する。
 */
test.describe.serial('接続登録からマスタデータ表示まで', () => {
  const uniqueSuffix = Date.now()
  const connectionDisplayName = `E2E-PG-${uniqueSuffix}`

  const adminEmail = 'e2e-admin@example.com'
  const adminPassword = 'E2eAdminP@ss9x7Q'

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(adminEmail)
    await page.getByTestId('login-password-input').fill(adminPassword)
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL('/')
  })

  test('RDBMS接続を登録できる', async ({ page }) => {
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
  })

  test('スキーマ取込ができる', async ({ page }) => {
    await page.goto('/connections')
    const row = page.getByRole('row', { name: new RegExp(connectionDisplayName) })
    await row.getByRole('button', { name: 'スキーマ取込' }).click()
    await expect(page.getByText('スキーマ取込に成功しました')).toBeVisible()
    // 取込成功後は「スキーマ詳細」「権限設定」の導線が表示される
    await expect(row.getByRole('link', { name: 'スキーマ詳細' })).toBeVisible()
    await expect(row.getByRole('link', { name: '権限設定' })).toBeVisible()
  })

  test('権限設定でpublicスキーマにREAD権限を付与できる', async ({ page }) => {
    await page.goto('/connections')
    const row = page.getByRole('row', { name: new RegExp(connectionDisplayName) })
    await row.getByRole('link', { name: '権限設定' }).click()

    await expect(page).toHaveURL(/\/permissions\/\d+/)
    await page.getByTestId('permissions-principal-type').selectOption('USER')
    await page
      .getByTestId('permissions-principal-select')
      .selectOption({ label: `${adminEmail}（${adminEmail}）` })

    await expect(page.getByTestId('permissions-tree')).toBeVisible()
    await page.getByTestId('permissions-schema-toggle-public').click()
    await page.getByTestId('permissions-primary-public').selectOption('READ')

    // 保存は即時反映（setPermission呼び出し）のため、ページを再読み込みして永続化を確認する
    await page.reload()
    await page.getByTestId('permissions-principal-type').selectOption('USER')
    await page
      .getByTestId('permissions-principal-select')
      .selectOption({ label: `${adminEmail}（${adminEmail}）` })
    await expect(page.getByTestId('permissions-primary-public')).toHaveValue('READ')
  })

  test('マスタメンテナンス画面でテーブル一覧が表示される', async ({ page }) => {
    await page.goto('/master-data')
    // DataTableはonRowClickが指定されるとtr要素がrole="button"になる（role="row"ではない、
    // design-system/components/DataTable.tsx参照）
    const connectionRow = page.getByRole('button', { name: new RegExp(connectionDisplayName) })
    await expect(connectionRow).toBeVisible()
    await connectionRow.click()

    await expect(page).toHaveURL(/\/master-data\/\d+/)
    await expect(page.getByText('public').first()).toBeVisible()
  })
})

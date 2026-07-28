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
 * 接続削除後のプレースホルダー表示のスモークテスト。
 * Integration Test Instructions（aidlc-docs/construction/build-and-test/
 * integration-test-instructions.md）のScenario 5に対応する。記録の不変性
 * （BR-QUERYHISTORY-04、BR-AUDITVIEW-08）により、接続削除後も履歴・監査ログの
 * レコード自体は閲覧可能で、接続の表示名が「(削除済み接続)」になることを確認する。
 */
test.describe.serial('接続削除後のプレースホルダー表示', () => {
  const uniqueSuffix = Date.now()
  const connectionDisplayName = `E2E-Del-${uniqueSuffix}`

  const adminEmail = 'e2e-admin@example.com'
  const adminPassword = 'E2eAdminP@ss9x7Q'

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email-input').fill(adminEmail)
    await page.getByTestId('login-password-input').fill(adminPassword)
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL('/')
  })

  test('接続登録・スキーマ取込・権限設定・クエリ実行を行う', async ({ page }) => {
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

    await page.goto('/query-execution')
    const connectionRow = page.getByRole('button', { name: new RegExp(connectionDisplayName) })
    await expect(connectionRow).toBeVisible()
    await connectionRow.click()

    await expect(page).toHaveURL(/\/query-execution\/\d+/)
    await page.getByLabel('スキーマ').selectOption('public')
    await page.getByTestId('query-editor-sql-input').fill('SELECT * FROM categories')
    await page.getByTestId('query-editor-execute-button').click()
    await expect(page.getByTestId('data-table')).toBeVisible()
  })

  test('接続を削除する', async ({ page }) => {
    await page.goto('/connections')
    const row = page.getByRole('row', { name: new RegExp(connectionDisplayName) })
    await row.getByRole('button', { name: '削除' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '削除' }).click()
    await expect(row).not.toBeVisible()
  })

  test('クエリ履歴で削除済み接続としてプレースホルダー表示され、記録は閲覧できる', async ({ page }) => {
    await page.goto('/query-history')
    // devデータベースは複数回のテスト実行をまたいで永続化されるため、過去の実行分も含め
    // 複数件の「(削除済み接続)」が存在しうる。.first()で最初の1件を対象にする
    const placeholderRow = page.getByRole('button', { name: '(削除済み接続)' }).first()
    await expect(placeholderRow).toBeVisible()
    await placeholderRow.click()

    await expect(page).toHaveURL(/\/query-history\/\d+/)
    await expect(page.getByText(/SELECT \* FROM categories/)).toBeVisible()
  })

  test('監査ログで削除済み接続としてプレースホルダー表示され、記録は閲覧できる', async ({ page }) => {
    await page.goto('/audit-log')
    await page.getByLabel('イベント種別').selectOption('CONNECTION_DELETED')
    await expect(page.getByRole('cell', { name: '(削除済み接続)' }).first()).toBeVisible()
  })
})

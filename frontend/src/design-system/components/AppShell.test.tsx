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

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../theme/ThemeProvider'
import { AppShell } from './AppShell'

describe('AppShell', () => {
  it('ナビ項目・コンテンツ・Footerを表示し、折りたたみでラベルが消える', async () => {
    render(
      <ThemeProvider>
        <AppShell navItems={[{ key: 'users', label: 'ユーザ管理', active: true }]}>
          <p>コンテンツ</p>
        </AppShell>
      </ThemeProvider>,
    )
    expect(screen.getByText('ユーザ管理')).toBeInTheDocument()
    expect(screen.getByText('コンテンツ')).toBeInTheDocument()
    expect(screen.getByTestId('app-footer')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'menu' }))
    expect(screen.queryByText('ユーザ管理')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'menu' }))
    expect(screen.getByText('ユーザ管理')).toBeInTheDocument()
  })

  it('userLabelを渡すとログアウトボタンが表示され、クリックでonLogoutが呼ばれる', async () => {
    const onLogout = vi.fn()
    render(
      <ThemeProvider>
        <AppShell navItems={[]} userLabel="山田太郎" onLogout={onLogout}>
          <p>コンテンツ</p>
        </AppShell>
      </ThemeProvider>,
    )
    expect(screen.getByText('山田太郎')).toBeInTheDocument()
    await userEvent.click(screen.getByTestId('app-shell-logout-button'))
    expect(onLogout).toHaveBeenCalledTimes(1)
  })
})

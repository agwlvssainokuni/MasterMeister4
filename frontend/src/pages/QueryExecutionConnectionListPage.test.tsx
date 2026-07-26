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
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as queryApi from '../api/query'
import { QueryExecutionConnectionListPage } from './QueryExecutionConnectionListPage'

vi.mock('../api/query')

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/query-execution']}>
        <AuthProvider>
          <Routes>
            <Route path="/query-execution" element={<QueryExecutionConnectionListPage />} />
            <Route path="/query-execution/:connectionId" element={<p>ad-hoc実行画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('QueryExecutionConnectionListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('アクセス可能な接続一覧を表示する', async () => {
    vi.mocked(queryApi.listQueryConnections).mockResolvedValueOnce([
      { connectionId: 1, displayName: '本番DB' },
    ])
    renderPage()

    expect(await screen.findByText('本番DB')).toBeInTheDocument()
  })

  it('接続が0件の場合、空状態メッセージを表示する', async () => {
    vi.mocked(queryApi.listQueryConnections).mockResolvedValueOnce([])
    renderPage()

    expect(await screen.findByText('アクセス可能な接続はありません')).toBeInTheDocument()
  })

  it('行をクリックするとad-hoc実行画面へ遷移する', async () => {
    vi.mocked(queryApi.listQueryConnections).mockResolvedValueOnce([
      { connectionId: 1, displayName: '本番DB' },
    ])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('本番DB'))

    expect(await screen.findByText('ad-hoc実行画面')).toBeInTheDocument()
  })
})

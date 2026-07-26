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
import * as queryHistoryApi from '../api/queryHistory'
import { QueryHistoryConnectionListPage } from './QueryHistoryConnectionListPage'

vi.mock('../api/queryHistory')

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/query-history']}>
        <AuthProvider>
          <Routes>
            <Route path="/query-history" element={<QueryHistoryConnectionListPage />} />
            <Route path="/query-history/:connectionId" element={<p>クエリ履歴画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('QueryHistoryConnectionListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('履歴実績ベースの接続一覧を表示する', async () => {
    vi.mocked(queryHistoryApi.listQueryHistoryConnections).mockResolvedValueOnce([
      { connectionId: 1, displayName: '本番DB' },
    ])
    renderPage()

    expect(await screen.findByText('本番DB')).toBeInTheDocument()
  })

  it('接続が0件の場合、空状態メッセージを表示する', async () => {
    vi.mocked(queryHistoryApi.listQueryHistoryConnections).mockResolvedValueOnce([])
    renderPage()

    expect(await screen.findByText('履歴が記録された接続はありません')).toBeInTheDocument()
  })

  it('行をクリックするとクエリ履歴画面へ遷移する', async () => {
    vi.mocked(queryHistoryApi.listQueryHistoryConnections).mockResolvedValueOnce([
      { connectionId: 1, displayName: '本番DB' },
    ])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('本番DB'))

    expect(await screen.findByText('クエリ履歴画面')).toBeInTheDocument()
  })
})

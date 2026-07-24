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
import * as masterDataApi from '../api/masterData'
import { MasterDataConnectionListPage } from './MasterDataConnectionListPage'

vi.mock('../api/masterData')

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/master-data']}>
        <AuthProvider>
          <Routes>
            <Route path="/master-data" element={<MasterDataConnectionListPage />} />
            <Route path="/master-data/:connectionId" element={<p>テーブル一覧画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('MasterDataConnectionListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('アクセス可能な接続一覧を表示する', async () => {
    vi.mocked(masterDataApi.listMasterDataConnections).mockResolvedValueOnce([
      { connectionId: 1, displayName: '本番DB' },
      { connectionId: 2, displayName: '検証DB' },
    ])
    renderPage()

    expect(await screen.findByText('本番DB')).toBeInTheDocument()
    expect(screen.getByText('検証DB')).toBeInTheDocument()
  })

  it('接続が0件の場合、空状態メッセージを表示する', async () => {
    vi.mocked(masterDataApi.listMasterDataConnections).mockResolvedValueOnce([])
    renderPage()

    expect(await screen.findByText('アクセス可能な接続はありません')).toBeInTheDocument()
  })

  it('行をクリックするとテーブル一覧画面へ遷移する', async () => {
    vi.mocked(masterDataApi.listMasterDataConnections).mockResolvedValueOnce([
      { connectionId: 1, displayName: '本番DB' },
    ])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('本番DB'))

    expect(await screen.findByText('テーブル一覧画面')).toBeInTheDocument()
  })
})

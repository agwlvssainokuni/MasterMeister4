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

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as queryApi from '../api/query'
import { SavedQueryListPage } from './SavedQueryListPage'

vi.mock('../api/query')

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/saved-queries/1']}>
        <AuthProvider>
          <Routes>
            <Route path="/saved-queries/:connectionId" element={<SavedQueryListPage />} />
            <Route path="/saved-queries/:connectionId/new" element={<p>新規保存クエリ画面</p>} />
            <Route path="/saved-queries/:connectionId/:savedQueryId" element={<p>保存クエリ実行画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const QUERY_A = {
  id: 1,
  name: 'クエリA',
  sql: 'SELECT 1',
  visibility: 'PUBLIC' as const,
  createdBy: 42,
  own: true,
  retired: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('SavedQueryListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('保存クエリ一覧を表示する', async () => {
    vi.mocked(queryApi.listSavedQueries).mockResolvedValueOnce([QUERY_A])
    renderPage()

    expect(await screen.findByText('クエリA')).toBeInTheDocument()
    expect(queryApi.listSavedQueries).toHaveBeenCalledWith(1, 'ALL', false)
  })

  it('保存クエリが0件の場合、空状態メッセージを表示する', async () => {
    vi.mocked(queryApi.listSavedQueries).mockResolvedValueOnce([])
    renderPage()

    expect(await screen.findByText('表示対象の保存クエリはありません')).toBeInTheDocument()
  })

  it('「追加」ボタンをクリックすると新規保存クエリ画面へ遷移する', async () => {
    vi.mocked(queryApi.listSavedQueries).mockResolvedValueOnce([])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByTestId('saved-query-add-button'))

    expect(await screen.findByText('新規保存クエリ画面')).toBeInTheDocument()
  })

  it('クエリ名をクリックすると保存クエリ実行画面へ遷移する', async () => {
    vi.mocked(queryApi.listSavedQueries).mockResolvedValueOnce([QUERY_A])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByTestId('saved-query-name-1'))

    expect(await screen.findByText('保存クエリ実行画面')).toBeInTheDocument()
  })

  it('自分の保存クエリには非表示化ボタンが表示され、非表示化を実行できる', async () => {
    vi.mocked(queryApi.listSavedQueries).mockResolvedValue([QUERY_A])
    vi.mocked(queryApi.retireSavedQuery).mockResolvedValueOnce(undefined)
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByTestId('saved-query-retire-1'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByText('非表示化'))

    expect(queryApi.retireSavedQuery).toHaveBeenCalledWith(1, 1)
  })

  it('他ユーザの保存クエリには非表示化ボタンを表示しない', async () => {
    vi.mocked(queryApi.listSavedQueries).mockResolvedValueOnce([{ ...QUERY_A, own: false }])
    renderPage()

    await screen.findByText('クエリA')
    expect(screen.queryByTestId('saved-query-retire-1')).not.toBeInTheDocument()
  })
})

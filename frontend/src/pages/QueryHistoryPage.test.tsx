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
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as queryHistoryApi from '../api/queryHistory'
import { QueryHistoryPage } from './QueryHistoryPage'

vi.mock('../api/queryHistory')

function makeToken(payload: unknown): string {
  const header = window.btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = window.btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

function setAccessToken(role: string) {
  window.sessionStorage.setItem('mastermeister.accessToken', makeToken({ sub: '42', role }))
}

function ExecutionTarget() {
  const location = useLocation()
  const state = location.state as { sql?: string; schemaName?: string } | null
  return (
    <p>
      クエリ実行画面 sql={state?.sql} schema={state?.schemaName}
    </p>
  )
}

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/query-history/1']}>
        <AuthProvider>
          <Routes>
            <Route path="/query-history/:connectionId" element={<QueryHistoryPage />} />
            <Route path="/query-execution/:connectionId" element={<ExecutionTarget />} />
            <Route path="/saved-queries/:connectionId/new" element={<p>新規保存クエリ画面</p>} />
            <Route path="/query-builder/:connectionId" element={<p>クエリビルダー画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const RECORD = {
  id: 1,
  executedBy: 42,
  executorDisplayName: '山田太郎',
  connectionId: 1,
  schemaName: 'public',
  sql: 'SELECT * FROM items',
  savedQueryId: null,
  savedQueryName: null,
  queryType: 'AD_HOC' as const,
  rowCount: 3,
  durationMillis: 5,
  executedAt: '2026-07-26T00:00:00Z',
}

describe('QueryHistoryPage', () => {
  beforeEach(() => {
    vi.mocked(queryHistoryApi.listQueryHistorySchemas).mockResolvedValue(['public'])
    vi.mocked(queryHistoryApi.listQueryHistory).mockResolvedValue({
      content: [RECORD],
      page: 0,
      pageSize: 50,
      totalElements: 1,
      totalPages: 1,
    })
  })

  afterEach(() => {
    vi.resetAllMocks()
    window.sessionStorage.clear()
  })

  it('履歴一覧を表示する', async () => {
    renderPage()

    expect(await screen.findByText('SELECT * FROM items')).toBeInTheDocument()
  })

  it('一般ユーザには実行者スコープSelectを表示しない', async () => {
    setAccessToken('USER')
    renderPage()

    await screen.findByText('SELECT * FROM items')
    expect(screen.queryByLabelText('実行者')).not.toBeInTheDocument()
  })

  it('管理者には実行者スコープSelectを表示する', async () => {
    setAccessToken('ADMIN')
    renderPage()

    await screen.findByText('SELECT * FROM items')
    expect(screen.getByLabelText('実行者')).toBeInTheDocument()
  })

  it('行クリックで詳細モーダルが開きSQL全文を表示する', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('SELECT * FROM items'))

    expect(await screen.findByText('クエリ詳細')).toBeInTheDocument()
  })

  it('詳細モーダルの「実行へ」でクエリ実行画面へrouter state経由で遷移する', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('SELECT * FROM items'))
    await user.click(await screen.findByTestId('query-history-execute-button'))

    expect(await screen.findByText(/クエリ実行画面/)).toBeInTheDocument()
    expect(screen.getByText(/sql=SELECT \* FROM items/)).toBeInTheDocument()
    expect(screen.getByText(/schema=public/)).toBeInTheDocument()
  })

  it('詳細モーダルの「保存へ」で新規保存クエリ画面へ遷移する', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('SELECT * FROM items'))
    await user.click(await screen.findByTestId('query-history-save-button'))

    expect(await screen.findByText('新規保存クエリ画面')).toBeInTheDocument()
  })

  it('詳細モーダルの「ビルダーで開く」でクエリビルダー画面へ遷移する', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('SELECT * FROM items'))
    await user.click(await screen.findByTestId('query-history-builder-button'))

    expect(await screen.findByText('クエリビルダー画面')).toBeInTheDocument()
  })

  it('SQLキーワード検索を入力すると絞込条件付きで再取得する', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('SELECT * FROM items')

    await user.type(screen.getByTestId('query-history-sql-keyword-input'), 'items')

    expect(queryHistoryApi.listQueryHistory).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ sqlKeyword: 'items' }),
    )
  })
})

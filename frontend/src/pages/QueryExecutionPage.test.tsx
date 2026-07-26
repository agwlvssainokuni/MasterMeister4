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
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as queryApi from '../api/query'
import { QueryExecutionPage } from './QueryExecutionPage'

vi.mock('../api/query')

function SaveAsTarget() {
  const location = useLocation()
  const state = location.state as { sql?: string; schemaName?: string } | null
  return (
    <p>
      新規保存クエリ画面 sql={state?.sql} schema={state?.schemaName}
    </p>
  )
}

function QueryBuilderTarget() {
  const location = useLocation()
  const state = location.state as { sql?: string; schemaName?: string } | null
  return (
    <p>
      クエリビルダー画面 sql={state?.sql} schema={state?.schemaName}
    </p>
  )
}

function renderPage(state?: unknown) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[{ pathname: '/query-execution/1', state }]}>
        <AuthProvider>
          <Routes>
            <Route path="/query-execution/:connectionId" element={<QueryExecutionPage />} />
            <Route path="/saved-queries/:connectionId/new" element={<SaveAsTarget />} />
            <Route path="/query-builder/:connectionId" element={<QueryBuilderTarget />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('QueryExecutionPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('スキーマ一覧を読み込んでセレクタに表示する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }, { schemaName: 'sales' }])
    renderPage()

    expect(await screen.findByText('public')).toBeInTheDocument()
    expect(screen.getByText('sales')).toBeInTheDocument()
  })

  it('実行するとクエリ結果を表示する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryApi.executeQuery).mockResolvedValueOnce({
      columns: ['id', 'name'],
      rows: [{ id: '1', name: 'Apple' }],
      page: null,
      pageSize: null,
      totalCount: null,
      rowCount: 1,
      durationMillis: 5,
    })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.type(screen.getByTestId('query-editor-sql-input'), 'SELECT * FROM items')
    await user.selectOptions(screen.getByLabelText('スキーマ'), 'public')
    await user.click(screen.getByTestId('query-editor-execute-button'))

    expect(await screen.findByText('Apple')).toBeInTheDocument()
    expect(queryApi.executeQuery).toHaveBeenCalledWith(1, {
      sql: 'SELECT * FROM items',
      schemaName: 'public',
      params: {},
      pagingEnabled: false,
      page: 0,
      pageSize: 50,
    })
  })

  it('SQLに含まれる:paramトークンに応じてパラメータ入力欄を表示する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.type(screen.getByTestId('query-editor-sql-input'), 'SELECT * FROM t WHERE a = :name')

    expect(await screen.findByTestId('query-editor-param-name')).toBeInTheDocument()
  })

  it('クエリビルダー画面からのrouter stateでSQL・スキーマをプレフィルする', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    renderPage({ sql: 'SELECT t1.id FROM items AS t1', schemaName: 'public' })

    expect(await screen.findByDisplayValue('SELECT t1.id FROM items AS t1')).toBeInTheDocument()
    expect(await screen.findByText('public')).toBeInTheDocument()
  })

  it('「名前を付けて保存」をクリックすると現在の入力をrouter state経由で新規保存クエリ画面へ引き継ぐ', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.type(screen.getByTestId('query-editor-sql-input'), 'SELECT 1')
    await user.selectOptions(screen.getByLabelText('スキーマ'), 'public')
    await user.click(screen.getByTestId('query-execution-save-as-button'))

    expect(await screen.findByText(/新規保存クエリ画面/)).toBeInTheDocument()
    expect(screen.getByText(/sql=SELECT 1/)).toBeInTheDocument()
    expect(screen.getByText(/schema=public/)).toBeInTheDocument()
  })

  it('「クエリビルダーで編集」をクリックすると現在の入力をrouter state経由でクエリビルダー画面へ引き継ぐ', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.type(screen.getByTestId('query-editor-sql-input'), 'SELECT 1')
    await user.selectOptions(screen.getByLabelText('スキーマ'), 'public')
    await user.click(screen.getByTestId('query-execution-edit-in-builder-button'))

    expect(await screen.findByText(/クエリビルダー画面/)).toBeInTheDocument()
    expect(screen.getByText(/sql=SELECT 1/)).toBeInTheDocument()
    expect(screen.getByText(/schema=public/)).toBeInTheDocument()
  })
})
